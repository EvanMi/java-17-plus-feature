# LLM Transformer Decoder 自回归推理、QKV、KV Cache 原理详解

>
> 本文从基础自注意力公式入手，结合手工数值示例，拆解 Q/K/V 的含义、矩阵运算逻辑、Prefill 与 Decode 两个阶段的差异，重点解释 KV Cache 的存储、追加机制，说明为什么 Q 不缓存；同时扩展多头、批量 Batch 推理、PagedAttention（分页注意力）工业级优化方案，附带完整 Decoder 层结构、自回归生成链路，适合理解大模型推理显存带宽瓶颈、KV Cache 工程优化前置知识。

## 一、前置：Transformer 架构分类

Transformer 分为 Encoder-only、Encoder-Decoder、**Decoder-only（当前主流 LLM，如 Llama、Qwen）** 三类。

- Encoder-only：双向注意力，token 互相可见，用于理解类任务（分类、抽取）
- Encoder-Decoder：编码器理解输入，解码器生成输出（翻译），存在交叉注意力 Cross-Attention
- Decoder-only（LLM）：**因果掩码自注意力（Causal Masked Self-Attention）**，自回归生成，只能看见当前以及历史 token，看不到未来未生成 token；没有 Cross-Attention。

单层 Decoder Block 结构：

```
输入隐向量
├─ Masked Multi-Head Self-Attention（KV Cache 在这里生效）
├─ Add 残差连接 + LayerNorm
├─ Feed Forward Network（FFN / MLP，两个线性层 + 激活函数 GELU）
├─ Add 残差连接 + LayerNorm
└─ 输出隐向量，送入下一层 Decoder Block
```

>
> 核心分工：
> 自注意力：**让 token 和其他历史 token 交换上下文信息**
> FFN：**独立对单个 token 的特征做非线性变换，token 之间互不干扰**
> 残差 + LayerNorm：解决深层网络梯度消失、训练不稳定问题

多头注意力：多套独立的 \(W_Q,W_K,W_V\)，并行计算多组注意力，分头捕捉不同语义关联（指代、语法、语义依赖），最后拼接多头输出。

## 二、自注意力基础公式

\(\text{Attention}(Q,K,V)=\text{softmax}\left(\frac{QK^T}{\sqrt{d_k}}\right)V\)三个向量定义：

1. Q（Query 查询）：**当前 token 想要检索什么信息**，是检索者
2. K（Key 键）：**每一条历史信息的检索索引**，被检索的 key
3. V（Value 值）：**每一条历史信息承载的原始内容**

通俗类比：

- Q = 你的搜索词
- K = 文档的索引标签
- V = 文档正文
  注意力分数就是搜索词和索引的匹配度，然后按照匹配度加权读取文档正文，融合成新向量。

>
> 缩放 \(\sqrt{d_k}\) 的作用：维度 \(d_k\) 很大时，内积数值会爆炸，softmax 输出会极端尖锐，梯度难以更新，缩放用来稳定梯度。

## 三、手工示例：一句话的自注意力计算（Encoder 双向，无掩码）

示例句子：`I love AI`，共 3 个 token
每个 token 经过 Embedding + 位置编码，得到初始向量 X（3 行，每行对应一个 token）
设置 \(d_k=3\)，训练好权重矩阵 \(W_Q,W_K,W_V\)，计算：

\(Q = XW_Q,\quad K=XW_K,\quad V=XW_V\)

得到 Q/K/V 矩阵后计算相似度：

\(QK^T\)
矩阵内每个元素 \(score_{i,j}\)：**第 i 个 token 的 Q 和 第 j 个 token 的 K 的相似度**。

经过缩放、softmax 之后得到注意力权重矩阵（每行总和 = 1）：

\(\text{softmax}=
\begin{bmatrix}
0.533 & 0.168 & 0.299 \\
0.136 & 0.432 & 0.432 \\
0.219 & 0.390 & 0.390
\end{bmatrix}\)- 第 0 行：`I` 的注意力分配：53.3% 看自己，16.8% 看 love，29.9% 看 AI
- 第 1 行：`love` 的注意力分配：13.6% 看 I，43.2% 看自己，43.2% 看 AI
- 第 2 行：`AI` 的注意力分配：21.9% 看 I，39.0% 看 love，39.0% 看自己

>
> 含义：每一行就是**当前 token 的注意力账单**，决定它融合多少其他 token 的 V 信息。

再用权重乘以 V 矩阵，加权求和：

\(\text{attn\_out} = \text{softmax_score} @ V\)
`attn_out`：注意力层输出的融合上下文的隐向量。
⚠️ **attn_out ≠ token 概率！它只是高维特征向量**，距离预测下一个 token 还有多层 Decoder 和输出分类头。

>
> 本示例是 Encoder 双向注意力，没有掩码，token 可以看见前后所有词；LLM Decoder 会增加因果 mask，把右上三角分数置为 \(-\inf\)，softmax 后权重 = 0，禁止看到未来 token。

## 四、两个推理阶段：Prefill（预填充） vs Decode（逐 token 生成）

这是 KV Cache 存在的根本原因，也是 “Decode 瓶颈是显存带宽而不是算力” 的来源。

### 4.1 Prefill 阶段：一次性处理 prompt 输入

输入：prompt 全部 token \([t_0,t_1,t_2...t_{n-1}]\)，一次性全部送入模型

- Q/K/V shape：\([seq\_len, d_k]\)，是矩阵
- 计算完整注意力矩阵 \([seq\_len, seq\_len]\)，计算量大，**计算密集型**
- 计算完成后，**把所有 token 的 K、V 保存下来，存入 KV Cache**
- Q 直接丢弃，不缓存

Prefill 只执行一次，处理用户输入的 prompt。

### 4.2 Decode 阶段：自回归逐个生成新 token（循环执行）

Prefill 结束后，进入循环，**每一轮只输入刚刚生成的 1 个新 token**，循环直到遇到停止符或者最大长度。

>
> 这是 LLM 流式输出的核心循环。

#### 4.2.1 新来单个 token 的 Q/K/V

新 token embedding \(x_{new}\)：shape \([1, d_{model}]\)

\(q_{new}=x_{new}@W_Q \quad [1,d_k] \\
k_{new}=x_{new}@W_K \quad [1,d_k] \\
v_{new}=x_{new}@W_V \quad [1,d_k]\)
✅ \(k_{new},v_{new}\) 是单行向量。

#### 4.2.2 KV Cache 的追加逻辑

KV Cache 本质是两个矩阵（简化单头，忽略 batch、多头）

- \(K_{cache}: [seq\_len, d_k]\)
- \(V_{cache}: [seq\_len, d_k]\)

历史的 K、V 不动，**只在序列维度末尾拼接新的 k_new、v_new**

```
K_cache_new = concat(K_cache_old, k_new, dim=0)
V_cache_new = concat(V_cache_old, v_new, dim=0)
```

👉 缓存持续变长；历史 K/V 不需要重算，这就是 KV Cache 节省算力的核心。

>
> 本轮的 \(k_{new},v_{new}\) 保存起来，**给未来后续生成 token 的 Q 做查询使用**。

#### 4.2.3 Decode 阶段注意力计算（单行 Q 查询全部历史 K）

\(\text{score} = \frac{q_{new} \cdot K_{cache\_new}^T}{\sqrt{d_k}}\)- \(q_{new}: [1, d_k]\)
- \(K_{cache\_new}^T: [d_k, seq\_len]\)
- 结果 score：\([1, seq\_len]\)，**只有一行相似度分数**

含义：**当前新 token 的 Q，挨个和缓存里面全部历史 token（包含自己刚刚追加进去的 K）做相似度打分**。

然后 softmax 得到单行注意力权重 \(attn\_weight[1, seq\_len]\)

\(\text{attn\_out} = attn\_weight @ V_{cache}\)
用权重对全部 V 做加权求和，得到当前 token 注意力输出。

>
> 和 Prefill 对比：Prefill 算 N×N 的大注意力矩阵；Decode 每一步只算 1×N 一行分数，**计算量极低**，但是需要从显存读取巨大的 K/V 缓存矩阵，访存压力巨大 → 瓶颈是显存带宽。

## 五、为什么 Q 不缓存？（核心疑问）

- K、V：代表**已经出现过的历史内容**，序列固定不变，后面所有新 token 都需要查询它们，所以可以缓存复用；新 token 的 K/V 追加进去，成为后续上下文。
- Q：**查询者，每一轮查询者都是刚刚新生成的 token**，每一轮 Q 都是全新的；本轮 Q 用完使命就结束，下一轮的查询者是下一个新 token，和当前 Q 无关，没有复用价值，因此不缓存。

一句话：K/V 是知识库，Q 是每次换一个的搜索词。

## 六、从 attn_out 到预测下一个完整链路

```
新token embedding
→ q_new,k_new,v_new
→ k_new、v_new追加进入KV Cache
→ score = q_new @ K_cache.T / sqrt(d_k)
→ softmax → attn_weight
→ attn_out = attn_weight @ V_cache
→ 残差 + LayerNorm
→ FFN(MLP)
→ 残差 + LayerNorm
→ 送入下一层Decoder Block，逐层传递
→ 顶层最后一层输出 h_final [1, d_model]
→ logits = h_final @ W_embed.T    # 权重共享，词表分类头
→ softmax(logits) → 词表概率分布 [1, vocab_size]
→ 采样策略（argmax / top-p / top-k）选出下一个token id
→ 将该token作为下一轮输入，循环Decode
```

关键点重申：

- `attn_out`：注意力层特征向量，**不是 token 概率**
- logits：原始分数，还不是概率
- softmax (logits)：才是下一个 token 的概率分布

## 七、因果掩码（Causal Mask）补充

Decoder-only LLM 的自注意力带因果掩码：
在 Prefill 阶段构造注意力分数矩阵时，把未来位置（i<j）的分数填充为 \(-\inf\)，softmax 之后权重 = 0。
含义：生成第 i 个 token 时，**不能看到 j>i 还没有生成的未来 token**。

>
> Decode 阶段，每次只有最后一个新 token，没有未来 token，因果 mask 对这一步不再生效。

## 八、KV Cache 扩展：多头、Batch 批量推理

>
> 前面章节是【单头、单请求】简化模型，工业线上模型全部是多头、多请求 Batch 混跑。

### 8.1 多头注意力下的 KV Cache 布局

LLM 都是多头自注意力（Multi-Head Attention），关系：
`d_model = num_heads × head_dim`

>
> 例如 Qwen-7B：`d_model=4096, num_heads=32, head_dim=128`

输入隐向量经过 \(W_Q,W_K,W_V\) 之后切分成多头：

- \(Q: [1, num\_heads, head\_dim]\)
- \(K: [1, num\_heads, head\_dim]\)
- \(V: [1, num\_heads, head\_dim]\)

**每个头是独立的注意力，每个头维护一套独立的 K、V Cache**。
👉 多头 KV Cache 形状（单样本，单 Decoder 层）：

```
K_cache: [seq_len, num_heads, head_dim]
V_cache: [seq_len, num_heads, head_dim]
```

部分框架存储顺序调换为 `[num_heads, seq_len, head_dim]`，只是内存排布差异，逻辑等价。

多头 Decode 计算流程（单样本）：

1. 新 token 算出 \(q_{new},k_{new},v_{new}: [1, num\_heads, head\_dim]\)
2. \(k_{new},v_{new}\) 追加到各自 head 对应的 KV 缓存尾部
3. 每个 head 内部独立做注意力：

\(score_h = q_h @ K_h^T / \sqrt{head\_dim}\)

\(attn\_out_h = softmax(score_h) @ V_h\)
4. 所有 head 输出拼接，投影回 \(d_{model}\) 维度

✅ 关键点：**头与头之间 KV 完全隔离，互不共享缓存**；分头独立计算注意力，最后合并结果。

### 8.2 Batch 批量推理下的 KV Cache

在线推理服务同时承载多个用户请求，也就是 Batch，分为两类：

1. **Prefill Batch**：一批 prompt 同时进入 Prefill 阶段（各个 prompt 长度可以不一样）
2. **Decode Batch（连续批 /ongoing batch）**：一批正在生成的请求，各自处在不同生成步数，每一轮各自新增 1 个 token，流式服务最常见。

规整 Batch（等长序列，教学理解）：batch_size = B，所有样本 seq_len 相同
单层 KV shape：

```
K_cache: [B, seq_len, num_heads, head_dim]
V_cache: [B, seq_len, num_heads, head_dim]
```

B 个请求完全独立，每个请求拥有自己独立一套 KV 缓存，互不干扰。

**真实线上痛点：Batch 内序列长短参差不齐**

- Request A：已经生成 120 token
- Request B：已经生成 350 token
- Request C：刚走完 prefill，seq=50

传统连续 KV 缓存（Naive KV Cache）问题：

1. 每个请求的 KV 是一块**连续的显存 Buffer**，随着生成不断变长；
2. 分配方式要么提前按最大长度预分配，要么 realloc 动态扩容；
3. 请求结束后整块释放，极易产生**大量显存碎片**；
4. 碎片拉高显存浪费，服务只能缩小 batch，吞吐上不去。

传统 Batch 注意力两种实现：

1. Pad 补齐：所有序列 padding 到最长长度，mask 屏蔽 padding 位置，算力显存浪费；
2. 变长无 padding：记录每个 request 有效长度，只访问有效 KV 位置（vLLM、SGLang 方案）。

## 九、PagedAttention 分页注意力（vLLM 核心）

>
> 论文：PagedAttention: Efficient Attention with Paged KV Cache
> 核心类比操作系统虚拟内存的分页思想：Naive KV Cache = 连续数组；PagedAttention = KV 拆成固定大小的页，页在显存中不需要连续。

### 9.1 核心概念：KV Page & PageTable

把 KV 缓存切成固定大小的**页（Page）**，一页存放固定数量 token 的 KV 数据。
示例：`page_size = 16 token`
一页存储：16 个 token × num_heads × head_dim 的 K + V。

每个推理请求不再持有一块连续 KV 显存，而是维护一张**页表 PageTable**：记录该请求的 KV 由哪些离散 Page 编号组成。

```
Request-0 PageTable: [page12, page45, page78]
Request-1 PageTable: [page2, page9]
```

这些 Page 在显存中的物理地址完全不连续。

生命周期：

- 当前 Page 填满 → 分配新空闲 Page，追加到请求的 page table；
- 请求生成结束 → 遍历 page table，Page 引用计数减一，引用计数归零则放回空闲页池，供其他请求复用。

✅ 核心收益：**消除 KV Cache 动态扩容带来的显存碎片，显存利用率大幅提升，支持更大 batch**。

### 9.2 分页模式下怎么做注意力计算

KV 散落在离散 Page，不会提前拷贝拼接成连续矩阵；采用**分块注意力（Blocked Attention）**：

1. 根据 request 的 page table，遍历它所有 KV page；
2. 逐个 page 读取 K_block, V_block；
3. 当前单行 Q 和该 page 内 K 计算局部 score、局部加权 V；
4. 累积所有 page 的加权结果，得到最终 attn_out。

>
> 避免了大块显存拷贝开销，但依赖 GPU 自定义 CUDA kernel 实现，原生 HuggingFace Transformer 不支持。

### 9.3 Prefix Sharing 前缀共享（王牌特性）

多个请求拥有相同 prompt 前缀（例如统一的系统提示词）：

```
Req1: [SystemPrompt] + 用户queryA
Req2: [SystemPrompt] + 用户queryB
```

前缀部分的 KV 完全一致，PagedAttention 支持多个请求的 page table**指向同一组共享 Page**，依靠引用计数管理页面。
👉 同一份前缀 KV 显存，多用户复用，极大节省显存；传统连续 KV 方案很难低成本实现。

### 9.4 引用计数 & Page 生命周期

每个 Page 维护 `ref_cnt` 引用计数：

- ref_cnt > 0：正在被至少一个请求使用，不可回收
- ref_cnt = 0：空闲，放回 page pool

共享前缀场景：一个请求结束，仅 ref_cnt -=1，Page 还保留给其他请求继续使用。

### 9.5 PagedAttention 的局限

1. 间接寻址、分块计算引入少量 kernel 开销，收益来自更高显存利用率、更大 batch；
2. page_size 是超参：page 太小，page table 变长、寻址开销变大；page 太大，页内尾部空闲 token 浪费显存，vLLM 默认 page_size 一般 16；
3. 对推理算子实现要求高，需要定制 CUDA 内核。

### 9.6 Naive KV Cache vs PagedAttention 对比

表格

| 特性 | 传统连续 KV Cache | PagedAttention |
| --- | --- | --- |
| 内存布局 | 每个请求一块连续显存 buffer | 拆成固定大小 Page，物理离散，page table 管理 |
| 扩容 | realloc，容易碎片 | 按需分配新 page，无碎片 |
| 前缀共享 | 很难，需要手动拷贝 | 原生支持 Page 引用共享，省显存 |
| 释放 | 整块释放 | 按页回收，页可复用 |
| 访存 | 连续访存，cache 友好，但碎片限制 batch | 离散间接访存，kernel 复杂，显存利用率高 |
| 代表框架 | HuggingFace transformers 原生 | vLLM, SGLang, TensorRT-LLM |

## 十、配套衍生优化方向

1. **Contiguous Batching（连续批调度）**
   区分 prefill 请求和 decode 请求；prefill 是计算密集，decode 是带宽密集，混合调度（hybrid batch）提升整体吞吐。
2. **KV Quantization**
   KV 数值 FP16→INT8/FP8，降低 KV 显存占用，代价是轻微精度损失；PagedAttention 的 Page 同样可以存放量化后的 KV。
3. **KV Eviction（KV 淘汰）**
   超长上下文场景，使用 LRU 淘汰久远的 KV page，控制显存上限，典型如 StreamingLLM。
4. **Sliding Window Attention**
   只保留最近 N 个 token 的 KV，更早 KV 直接丢弃，降低 KV 存储压力。

## 十一、全文总结

1. 自注意力本质：Q 检索 K，按相似度加权聚合 V，实现 token 之间上下文信息融合；
2. Prefill：计算密集，算力瓶颈，一次性处理 prompt，生成并保存 KV Cache；
3. Decode：访存密集，显存带宽瓶颈，逐一生成 token，增量追加 K、V；Q 没有复用价值，不缓存；
4. 多头 KV：每个 head 独立维护一套 KV 缓存，分头独立计算注意力；
5. Batch 推理：多请求独立持有 KV；传统连续 KV 容易产生显存碎片；
6. PagedAttention 借鉴操作系统分页内存思想，KV 拆分为离散 Page、PageTable 寻址、引用计数、原生支持前缀共享，是当前工业 LLM 推理吞吐优化的基石。