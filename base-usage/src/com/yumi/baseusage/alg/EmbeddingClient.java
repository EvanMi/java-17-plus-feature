package com.yumi.baseusage.alg;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * 调用 LLM 网关 embeddings 接口，为文本生成向量表示。
 * 对应 curl 示例：
 *   POST http://llm-gw.jd.local/v1/embeddings
 *   Authorization: Bearer <token>
 *   {"model":"Qwen3-Embedding-8B-joybuilder","input":"..."}
 */
public class EmbeddingClient {

    private static final String URL = "http://llm-gw.jd.local/v1/embeddings";
    private static final String TOKEN = "4d9551967c9b40eb94151db2aa13bdc6";
    private static final String MODEL = "Qwen3-Embedding-8B-joybuilder";

    private final HttpClient httpClient;

    public EmbeddingClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 以 input 文本作为入参，调用 embeddings 接口并返回向量。
     *
     * @param input 需要生成向量表示的文本
     * @return 向量（float 列表）
     */
    public float[] embedding(String input) {
        // 组装请求体，与 curl 的 -d 内容一致
        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("input", input);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString()))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("embeddings 接口调用失败，状态码：" + response.statusCode()
                        + "，响应：" + response.body());
            }

            JSONObject resp = JSON.parseObject(response.body());
            // OpenAI 兼容格式：data[0].embedding 为向量数组
            JSONArray data = resp.getJSONArray("data");
            if (data == null || data.isEmpty()) {
                throw new RuntimeException("embeddings 响应缺少 data 字段：" + response.body());
            }
            JSONArray embedding = data.getJSONObject(0).getJSONArray("embedding");
            if (embedding == null) {
                throw new RuntimeException("embeddings 响应缺少 embedding 字段：" + response.body());
            }
            float [] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = embedding.getFloat(i);
            }
            return vector;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("embeddings 请求被中断", e);
        } catch (Exception e) {
            throw new RuntimeException("embeddings 请求失败", e);
        }
    }

    public record TextEmbedding(String text, float[] vector) {}

    public static class UnionFind {
        private final int[] parent;

        public UnionFind(int n) {
            parent = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
            }
        }

        public int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }

        public void union(int x, int y) {
            int fx = find(x);
            int fy = find(y);
            if (fx != fy) {
                parent[fy] = fx;
            }
        }
    }

    public static class EmbeddingUnionFindGrouper {
        public static Map<Integer, List<TextEmbedding>> groupByCosineUnionFind(List<TextEmbedding> list, double threshold) {
            int n = list.size();
            UnionFind uf = new UnionFind(n);
            // 两两比较 O(n²)，数据量大时这个复杂度会爆炸
            for (int i = 0; i < n; i++) {
                TextEmbedding a = list.get(i);
                for (int j = i + 1; j < n; j++) {
                    TextEmbedding b = list.get(j);
                    double sim = VectorUtil.cosineSimilarity(a.vector(), b.vector());
                    if (sim >= threshold) {
                        uf.union(i, j);
                    }
                }
            }
            // 按根节点聚合
            Map<Integer, List<TextEmbedding>> result = new HashMap<>();
            for (int i = 0; i < n; i++) {
                int root = uf.find(i);
                result.computeIfAbsent(root, k -> new ArrayList<>()).add(list.get(i));
            }
            return result;
        }
    }


    public static class DBSCANVectorClustering {
        private final List<TextEmbedding> points;
        private final double eps;
        private final int minPts;

        // 标记：未访问、已访问、噪声
        private static final int UNVISITED = 0;
        private static final int VISITED = 1;
        private static final int NOISE = -1;

        private int[] visited;
        private List<List<TextEmbedding>> clusters;

        /**
         * @param points 向量列表
         * @param eps 邻域余弦距离上限，eps = 1 - 相似度阈值
         * @param minPts 核心点最小邻域点数（包含自己）
         */
        public DBSCANVectorClustering(List<TextEmbedding> points, double eps, int minPts) {
            this.points = points;
            this.eps = eps;
            this.minPts = minPts;
        }

        public List<List<TextEmbedding>> run() {
            int n = points.size();
            visited = new int[n];
            clusters = new ArrayList<>();

            for (int i = 0; i < n; i++) {
                if (visited[i] == UNVISITED) {
                    List<Integer> neighbors = rangeQuery(i);
                    if (neighbors.size() >= minPts) {
                        // 核心点，开始扩张簇
                        List<TextEmbedding> cluster = new ArrayList<>();
                        expandCluster(i, neighbors, cluster);
                        clusters.add(cluster);
                    } else {
                        // 暂时标记噪声，后续有可能被别的核心点吸收
                        visited[i] = NOISE;
                    }
                }
            }
            return clusters;
        }

        /**
         * 查询下标pointIdx的eps邻域内所有点下标
         */
        private List<Integer> rangeQuery(int pointIdx) {
            List<Integer> neighbors = new ArrayList<>();
            TextEmbedding p = points.get(pointIdx);
            float[] pv = p.vector();
            for (int i = 0; i < points.size(); i++) {
                TextEmbedding q = points.get(i);
                double dist = VectorUtil.cosineSimilarity(pv, q.vector());
                if (dist <= eps) {
                    neighbors.add(i);
                }
            }
            return neighbors;
        }

        /**
         * 扩张簇
         */
        private void expandCluster(int pointIdx, List<Integer> neighbors, List<TextEmbedding> cluster) {
            visited[pointIdx] = VISITED;
            cluster.add(points.get(pointIdx));

            Queue<Integer> queue = new LinkedList<>(neighbors);
            while (!queue.isEmpty()) {
                int curIdx = queue.poll();
                if (visited[curIdx] == UNVISITED) {
                    List<Integer> curNeighbors = rangeQuery(curIdx);
                    if (curNeighbors.size() >= minPts) {
                        queue.addAll(curNeighbors);
                    }
                    visited[curIdx] = VISITED;
                    cluster.add(points.get(curIdx));
                } else if (visited[curIdx] == NOISE) {
                    // 之前标记噪声，现在被并入簇
                    visited[curIdx] = VISITED;
                    cluster.add(points.get(curIdx));
                }
            }
        }

        /**
         * 获取噪声点（不属于任何簇）
         */
        public List<TextEmbedding> getNoisePoints() {
            List<TextEmbedding> noise = new ArrayList<>();
            for (int i = 0; i < visited.length; i++) {
                if (visited[i] == NOISE) {
                    noise.add(points.get(i));
                }
            }
            return noise;
        }

        // ========== 工具入口方法，方便调用 ==========
        public static DBSCANVectorClustering createBySimilarityThreshold(List<TextEmbedding> list, double simThreshold, int minPts) {
            double eps = 1.0 - simThreshold;
            return new DBSCANVectorClustering(list, eps, minPts);
        }
    }

    public static void main(String[] args) {
        EmbeddingClient embeddingService = new EmbeddingClient();
        List<String> texts = List.of(
                "证据来源：标题。选择理由：标题中明确提到‘除菌’‘除螨’‘长效抑菌’‘留香’，且‘除菌’与‘抑菌’在标题中同时出现，根据规则‘除菌’与‘抑菌’需区分，标题中‘除菌’为直接功效主张，‘长效抑菌’为持续性抑菌效果，符合‘抑菌’候选项；‘留香’为明确功效；‘除螨’为明确功效。未提及‘去渍’‘去污’等其他功效，且无其他可映射项。最终结果：[除菌, 抑菌, 除螨, 留香]",
                "证据来源：标题。选择理由：标题中明确提到‘持久留香’，可映射为‘留香’；‘去渍’直接对应‘去渍’；‘除菌’直接对应‘除菌’；‘除螨’直接对应‘除螨’。其他功效如‘净透生香’‘香氛’等为营销描述，未在候选范围内或无法可靠映射为其他具体功效。图片中虽有‘先净后香’‘净渍释香’等描述，但标题已明确列出‘去渍、除菌、除螨、留香’，优先以标题为准。最终结果：[留香,去渍,除菌,除螨]",
                "证据来源：标题。选择理由：标题中明确包含‘运动鞋’、‘打排球运动鞋’、‘训练网球鞋’等足部穿着商品形态描述，且‘鞋’字多次出现，商品形态为鞋类。根据类目规则，任何标题明确为鞋、球鞋、运动鞋等足部穿着商品，一律映射为‘排球鞋’。尽管标题提及‘网球鞋’，但‘排球运动鞋’为明确指向排球类运动的鞋类商品，且类目为排球鞋服，因此优先映射为‘排球鞋’。最终结果：[排球鞋]",
                "证据来源：标题。选择理由：标题中明确出现“排球鞋”、“手球鞋”、“训练鞋”等足部穿着商品形态词汇，且“排球鞋”为首要描述，符合鞋类商品定义。尽管标题提及“手球鞋”和“气排球鞋”，但商品形态明确为鞋类，且“排球鞋”为直接类别指向。根据规则，标题明确鞋类形态时必须映射为“排球鞋”，无需因运动项目跨类或图片未明确排球元素而留空。最终结果：[排球鞋]",
                "证据来源：标题。选择理由：标题明确包含‘排球服套装’，且‘套装’属于服装类商品形态，符合‘排球服’候选范围。标题中‘女男定制比赛训练队服’进一步确认为服装类商品，非鞋类。尽管标题中出现‘排球’，但商品形态明确为服装，无需因运动项目与候选名称不一致而留空。最终结果：[排球服]"
        );

        List<TextEmbedding> items = new ArrayList<>();
        for (String t : texts) {
            items.add(new TextEmbedding(t, embeddingService.embedding(t)));
        }

        double threshold = 0.85;
        Map<Integer, List<TextEmbedding>> groups = EmbeddingUnionFindGrouper.groupByCosineUnionFind(items, threshold);
        for (List<TextEmbedding> g : groups.values()) {
            System.out.println("===== 一组 =====");
            for (TextEmbedding e : g) {
                System.out.println(e.text());
            }
        }

//        List<TextEmbedding> items = new ArrayList<>();
//        for (String t : texts) {
//            items.add(new TextEmbedding(t, embeddingService.embedding(t)));
//        }
//
//        // 相似度阈值0.7，minPts=2：至少2个点才能形成簇
//        double simThreshold = 0.6;
//        int minPts = 2;
//        DBSCANVectorClustering dbscan = DBSCANVectorClustering.createBySimilarityThreshold(items, simThreshold, minPts);
//        List<List<TextEmbedding>> clusters = dbscan.run();
//        List<TextEmbedding> noises = dbscan.getNoisePoints();
//
//        System.out.println("===== DBSCAN聚类结果 =====");
//        for (int cIdx = 0; cIdx < clusters.size(); cIdx++) {
//            System.out.println("簇" + (cIdx + 1));
//            for (TextEmbedding te : clusters.get(cIdx)) {
//                System.out.println(" - " + te.text());
//            }
//        }
//        System.out.println("===== 噪声点（孤立） =====");
//        for (TextEmbedding te : noises) {
//            System.out.println(" - " + te.text());
//        }
    }
}