package com.yummi.redis.demo.rest.bloom;

import io.github.dengliming.redismodule.redisbloom.client.RedisBloomClient;
import io.github.dengliming.redismodule.redisbloom.model.TopKFilterInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

///🧠 核心原理
/// ‌数据结构‌：维护一个 d 行 w 列的二维哈希表，每个桶里存两项——指纹（fingerprint，元素哈希值摘要）和计数器（count，访问频次）。另外配一个容量为 K 的最小堆，用来保存当前 TopK 候选。
/// ‌插入流程‌：
/// 元素到达后，用 d 个独立哈希函数映射到每行的一个桶。
/// 桶为空或指纹匹配，直接计数 +1；指纹不匹配（哈希冲突），就按概率 P = b^(-C) 尝试把原有计数减 1（b 是衰减因子，C 是当前计数值）。减到 0 就替换成新元素。
/// 最后用 d 个桶中的最大计数值作为该元素的频率估计，更新最小堆。
/// 3.关键设计——指数衰减‌：高频元素计数大，衰减概率趋近于 0，地位稳固；低频元素计数小，容易被“踢走”。这样小流（噪声）被快速淘汰，大流（热点）稳定保留。
@RestController
@RequestMapping("/topk")
public class RedisTopKController {
    @Autowired
    private RedisBloomClient redisBloomClient;

    /// 3. 初始化 TopK 结构（必须先创建才能使用）
    @GetMapping("/reserve/{name}/{topk}/{width}/{depth}/{decay}")
    public Map<String, Object> reserve(@PathVariable("name") String name, @PathVariable("topk") Integer topk,
                                       @PathVariable("width") Integer width, @PathVariable("depth") Integer depth, @PathVariable("decay") Double decay) {
        boolean success = redisBloomClient.getTopKFilter(name).reserve(topk, width, depth, decay);
        return success("TopK 结构初始化" + (success ? "成功" : "失败（可能已存在）"), Map.of(
                "name", name,
                "topk", topk,
                "width", width,
                "depth", depth,
                "decay", decay,
                "reserved", success));
    }

    /// 4. 批量添加元素
    @GetMapping("/add/{name}/{item}")
    public Map<String, Object> add(@PathVariable("name") String name, @PathVariable("item") String item) {
        List<String> kickedItems = redisBloomClient.getTopKFilter(name).add(item);
        return success("元素添加完成", Map.of(
                "name", name,
                "item", item,
                "kickedItems", kickedItems == null ? List.of() : kickedItems));
    }

    /// 5. 批量增加元素计数（批量累加权重）
    @GetMapping("/incrby/{name}/{item1}/{value1}/{item2}/{value2}")
    public Map<String, Object> incrby(@PathVariable("name") String name, @PathVariable("item1") String item1, @PathVariable("value1") Integer value1,
                                      @PathVariable("item2") String item2, @PathVariable("value2") Integer value2) {
        List<String> kickedItems = redisBloomClient.getTopKFilter(name).incrby(Map.of(item1, value1, item2, value2));
        return success("批量累加计数完成", Map.of(
                "name", name,
                "increments", Map.of(item1, value1, item2, value2),
                "kickedItems", kickedItems == null ? List.of() : kickedItems));
    }

    /// 6. 批量查询元素是否在 TopK 中
    @GetMapping("/query/{name}/{item1}/{item2}")
    public Map<String, Object> query(@PathVariable("name") String name, @PathVariable("item1") String item1, @PathVariable("item2") String item2) {
        List<Boolean> results = redisBloomClient.getTopKFilter(name).query(item1, item2);
        return success("查询完成", Map.of(
                "name", name,
                "items", List.of(item1, item2),
                "inTopK", results));
    }

    /// 7. 查询元素的估计访问次数
    @GetMapping("/count/{name}/{item1}/{item2}")
    public Map<String, Object> count(@PathVariable("name") String name, @PathVariable("item1") String item1, @PathVariable("item2") String item2) {
        List<Integer> results = redisBloomClient.getTopKFilter(name).count(item1, item2);
        return success("计数查询完成", Map.of(
                "name", name,
                "items", List.of(item1, item2),
                "counts", results));
    }

    /// 8. 获取当前完整 TopK 列表
    @GetMapping("/list/{name}")
    public Map<String, Object> list(@PathVariable("name") String name) {
        List<String> items = redisBloomClient.getTopKFilter(name).list();
        return success("TopK 列表", Map.of(
                "name", name,
                "items", items == null ? List.of() : items));
    }

    /// 9. 获取 TopK 结构配置信息
    @GetMapping("/getInfo/{name}")
    public Map<String, Object> getInfo(@PathVariable("name") String name) {
        TopKFilterInfo info = redisBloomClient.getTopKFilter(name).getInfo();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("topk", info.getTopK());
        data.put("width", info.getWidth());
        data.put("depth", info.getDepth());
        data.put("decay", info.getDecay());
        return success("TopK 配置信息", data);
    }

    private Map<String, Object> success(String message, Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", message);
        result.put("data", data);
        return result;
    }
}