package com.yummi.redis.demo.rest.search;
import io.github.dengliming.redismodule.redisearch.RediSearch;
import io.github.dengliming.redismodule.redisearch.RediSearchBatch;
import io.github.dengliming.redismodule.redisearch.aggregate.AggregateOptions;
import io.github.dengliming.redismodule.redisearch.aggregate.AggregateResult;
import io.github.dengliming.redismodule.redisearch.aggregate.Group;
import io.github.dengliming.redismodule.redisearch.aggregate.Reducers;
import io.github.dengliming.redismodule.redisearch.aggregate.SortField;
import io.github.dengliming.redismodule.redisearch.client.RediSearchClient;
import io.github.dengliming.redismodule.redisearch.index.ConfigOption;
import io.github.dengliming.redismodule.redisearch.index.Document;
import io.github.dengliming.redismodule.redisearch.index.IndexDefinition;
import io.github.dengliming.redismodule.redisearch.index.IndexOptions;
import io.github.dengliming.redismodule.redisearch.index.RSLanguage;
import io.github.dengliming.redismodule.redisearch.index.Suggestion;
import io.github.dengliming.redismodule.redisearch.index.SuggestionOptions;
import io.github.dengliming.redismodule.redisearch.index.schema.DistanceMetric;
import io.github.dengliming.redismodule.redisearch.index.schema.Field;
import io.github.dengliming.redismodule.redisearch.index.schema.FieldType;
import io.github.dengliming.redismodule.redisearch.index.schema.Schema;
import io.github.dengliming.redismodule.redisearch.index.schema.TagField;
import io.github.dengliming.redismodule.redisearch.index.schema.TextField;
import io.github.dengliming.redismodule.redisearch.index.schema.VectorAlgorithm;
import io.github.dengliming.redismodule.redisearch.index.schema.VectorField;
import io.github.dengliming.redismodule.redisearch.index.schema.VectorType;
import io.github.dengliming.redismodule.redisearch.search.GeoFilter;
import io.github.dengliming.redismodule.redisearch.search.HighlightOptions;
import io.github.dengliming.redismodule.redisearch.search.MisspelledTerm;
import io.github.dengliming.redismodule.redisearch.search.MisspelledSuggestion;
import io.github.dengliming.redismodule.redisearch.search.NumericFilter;
import io.github.dengliming.redismodule.redisearch.search.SearchOptions;
import io.github.dengliming.redismodule.redisearch.search.SearchResult;
import io.github.dengliming.redismodule.redisearch.search.SortBy;
import io.github.dengliming.redismodule.redisearch.search.SpellCheckOptions;
import io.github.dengliming.redismodule.redisearch.search.SummaryOptions;
import io.github.dengliming.redismodule.redisearch.search.Tags;
import io.github.dengliming.redismodule.redisearch.search.Vectors;
import org.redisson.Redisson;
import org.redisson.api.BatchResult;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.api.SortOrder;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * {@code io.github.dengliming.redismodule.redisearch.RediSearch} 完整 Demo。
 *
 * <p>覆盖能力：
 * <ul>
 *   <li>创建索引（TEXT / TAG / NUMERIC / GEO / VECTOR 五种字段）</li>
 *   <li>以原生 HASH 写入文档（Redis 8 / RediSearch 2.x 推荐方式，靠前缀自动建索引）</li>
 *   <li>全文检索：基础查询、分页、排序、数值过滤、地理过滤、高亮、摘要</li>
 *   <li>向量检索：HNSW 索引 + KNN 查询（PARAMS + DIALECT 2）</li>
 *   <li>聚合：GROUPBY + REDUCE（count / avg / sum / min / max）+ 排序 + 分页</li>
 *   <li>输入联想（FT.SUG*）、拼写纠错（FT.SPELLCHECK）、同义词（FT.SYNUPDATE）、词典（FT.DICT*）</li>
 *   <li>别名（FT.ALIAS*）、索引信息（FT.INFO / FT._LIST）、查询计划（FT.EXPLAIN）、配置（FT.CONFIG）</li>
 *   <li>异步 API（*Async → RFuture）与批量管线（RediSearchBatch）</li>
 * </ul>
 *
 * <p>运行前置条件：Redis 8（内置 Search）或 Redis 7 + RediSearch 2.x 模块。
 * 命令行第一个参数可指定端口，默认 6379。
 */
public class RediSearchDemo {

    // 使用独立命名空间，避免和业务 key / 已有索引冲突（真实接入时按业务规划前缀即可）
    private static final String INDEX_NAME = "idx:demo_products";
    private static final String KEY_PREFIX = "demo:product:";

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 6379;

        // 1. 先创建一个独立的 RedissonClient，再让 RediSearchClient 包一层复用同一个连接池
        //    （RediSearchClient 还有其他模块客户端如 RedisJSON/RedisBloom 也共享 Redisson，全库统一一套连接）
        Config config = new Config();
        config.useSingleServer().setAddress("redis://127.0.0.1:" + port);
        RedissonClient redisson = Redisson.create(config);
        RediSearchClient client = new RediSearchClient(redisson);

        try {
            // RediSearch 是"索引句柄"，一个实例绑定一个索引名
            RediSearch products = client.getRediSearch(INDEX_NAME);

            createProductIndex(products);          // 建索引（幂等：已存在则跳过）
            seedProducts(client);                  // 写文档（原生 HASH）

            basicSearch(products);                 // 基础全文检索 + 分页
            filteredSearch(products);              // 数值过滤 + 地理过滤 + 排序 + 指定返回字段
            highlightAndSummary(products);         // 高亮 + 摘要
            vectorSearch(products);                // 向量 KNN 检索
            aggregateDemo(products);               // 聚合统计
            suggestionDemo(products);              // 输入联想
            miscDemo(products, client);                // 别名 / 词典 / 拼写纠错 / 同义词 / 配置 / 索引信息
            asyncDemo(products);                   // 异步 API
            batchDemo(client);                     // 批量管线

            cleanup(redisson);                     // 清掉示例 HASH（索引保留，见 README 注意事项）
        } finally {
            // 包装型 client 不会 shutdown 共享的 Redisson，需要调用方自己关
            redisson.shutdown();
        }
    }

    // ---------------------------------------------------------------- 建索引

    /**
     * 创建商品索引，覆盖 5 种字段类型：
     * TEXT（可带权重/后缀字典树）、TAG（可自定义分隔符）、NUMERIC（可排序）、GEO、VECTOR（HNSW/FLAT）。
     * IndexDefinition 声明"索引哪些 key"：这里用前缀 product: 把原生 HASH 纳入索引。
     */
    private static void createProductIndex(RediSearch products) {
        Schema schema = new Schema()
                .addField(new TextField("title", 5.0d).withSuffixTrie())       // 标题，权重 5，建后缀 trie
                .addField(new TextField("description"))                          // 描述，默认权重 1
                .addField(new TagField("brand"))                                // 品牌 TAG，默认分隔符 ","
                .addField(new TagField("category"))                             // 类目 TAG
                .addField(new Field("price", FieldType.NUMERIC).sortable())     // 价格，数值且可排序
                .addField(new Field("stock", FieldType.NUMERIC))                // 库存，数值
                .addField(new Field("location", FieldType.GEO))                 // 经纬度 "lon,lat"
                .addField(new VectorField("embedding", VectorAlgorithm.HNSW, VectorType.FLOAT32, 4, DistanceMetric.COSINE)
                        .m(16).efConstruction(200).efRuntime(100));              // 4 维向量，HNSW 索引

        IndexOptions options = new IndexOptions()
                .definition(new IndexDefinition()                               // 默认数据类型 HASH
                        .setPrefixes(Arrays.asList(KEY_PREFIX))
                        .setScore(1.0d));

        try {
            boolean ok = products.createIndex(schema, options);
            System.out.println("[建索引] createIndex => " + ok);
        } catch (Exception e) {
            // 第二次运行索引已存在会抛异常，这里做幂等处理；若想真正重建，见 README「注意事项」
            System.out.println("[建索引] 索引已存在，跳过创建（" + e.getMessage() + "）");
        }
    }

    // ---------------------------------------------------------------- 写文档

    /**
     * 写入文档。Redis 8 / RediSearch 2.x 下 FT.ADD 已被移除，正确姿势是：
     * 在索引声明的 key 前缀下写"普通 HASH"，RediSearch 会自动增量索引。
     * 向量字段必须是原始二进制 blob（小端 FLOAT32），因此这里用 StringCodec 的 HSET 原生写入，
     * 保证字节原样落库（用 RMap + 默认 codec 会把 byte[] 序列化包装，导致向量解析失败）。
     */
    private static void seedProducts(RediSearchClient client) {
        // 商品 1：iPhone，北京，向量接近 [1,0,0,0]
        hset(client, KEY_PREFIX + "1",
                "title", "iPhone 15 Pro",
                "description", "A17 Pro chip, titanium frame, 120Hz ProMotion display",
                "brand", "Apple",
                "category", "手机,数码",
                "price", "7999",
                "stock", "120",
                "location", "116.397,39.909",
                "embedding", Vectors.toFloat32Bytes(1.0f, 0.0f, 0.0f, 0.0f));

        // 商品 2：华为，北京（距商品 1 约 1km），向量接近 [0.9,0.1,0,0]
        hset(client, KEY_PREFIX + "2",
                "title", "HUAWEI Mate 60 Pro",
                "description", "Kirin 9000s, satellite communication, 50MP camera",
                "brand", "Huawei",
                "category", "手机,数码",
                "price", "6999",
                "stock", "80",
                "location", "116.407,39.904",
                "embedding", Vectors.toFloat32Bytes(0.9f, 0.1f, 0.0f, 0.0f));

        // 商品 3：小米，上海，向量接近 [0,0.9,0.1,0]
        hset(client, KEY_PREFIX + "3",
                "title", "Xiaomi 14",
                "description", "Snapdragon 8 Gen 3, Leica optics, 90W fast charging",
                "brand", "Xiaomi",
                "category", "手机,数码",
                "price", "3999",
                "stock", "300",
                "location", "121.473,31.230",
                "embedding", Vectors.toFloat32Bytes(0.0f, 0.9f, 0.1f, 0.0f));

        // 商品 4：MacBook，北京，向量接近 [0,0,0.9,0.1]
        hset(client, KEY_PREFIX + "4",
                "title", "MacBook Pro 14",
                "description", "M3 Pro chip, 18-hour battery, Liquid Retina XDR display",
                "brand", "Apple",
                "category", "笔记本,数码",
                "price", "14999",
                "stock", "50",
                "location", "116.397,39.909",
                "embedding", Vectors.toFloat32Bytes(0.0f, 0.0f, 0.9f, 0.1f));

        // 商品 5：索尼耳机，广州，向量接近 [0.1,0.2,0.3,0.4]
        hset(client, KEY_PREFIX + "5",
                "title", "Sony WH-1000XM5",
                "description", "Industry-leading noise cancelling, 30-hour battery, LDAC",
                "brand", "Sony",
                "category", "耳机,音频",
                "price", "2399",
                "stock", "200",
                "location", "113.264,23.129",
                "embedding", Vectors.toFloat32Bytes(0.1f, 0.2f, 0.3f, 0.4f));

        System.out.println("[写文档] 已写入 5 个 HASH（demo:product:1 ~ demo:product:5）");
    }

    /** 用 StringCodec 原生 HSET 写 HASH，保证字符串按 UTF-8、向量按原始字节落库。 */
    private static void hset(RediSearchClient client, String key, Object... fieldValues) {
        Object[] params = new Object[fieldValues.length + 1];
        params[0] = key;
        System.arraycopy(fieldValues, 0, params, 1, fieldValues.length);
        client.getCommandExecutor().get(
                client.getCommandExecutor().writeAsync(key, StringCodec.INSTANCE, RedisCommands.HSET, params));
    }

    // ---------------------------------------------------------------- 检索

    /** 基础全文检索：TEXT 字段自动分词匹配，支持评分、分页。 */
    private static void basicSearch(RediSearch products) {
        SearchResult result = products.search("iphone", new SearchOptions()
                .withScores()
                .page(0, 5));
        printResult("[检索] 全文 'iphone'", result);

        // 指定返回字段（RETURN），只取需要的列。
        // 注意：默认全文检索只匹配 TEXT 字段；"Apple" 只存在 TAG 字段 brand 里，不会被命中（TAG 用 @brand:{Apple} 查）。
        SearchResult fields = products.search("titanium", new SearchOptions()
                .returnFields("title", "price")
                .page(0, 5));
        printResult("[检索] 全文 'titanium'（只返回 title/price）", fields);

        // 语言与停用词设置（对英文文本有效；中文分词见 README 注意事项）
        SearchResult lang = products.search("noise cancelling", new SearchOptions()
                .language(RSLanguage.ENGLISH)
                .noStopwords());
        printResult("[检索] 全文 'noise cancelling'（ENGLISH + noStopwords）", lang);
    }

    /** 数值过滤 + 地理过滤 + 排序 + 分页组合。 */
    private static void filteredSearch(RediSearch products) {
        // 价格 3000~9000，按价格降序
        SearchResult byPrice = products.search("*", new SearchOptions()
                .filter(new NumericFilter("price", 3000, 9000))
                .sort(new SortBy("price", SortOrder.DESC))
                .returnFields("title", "brand", "price")
                .page(0, 10));
        printResult("[过滤] 价格 3000~9000，按价格降序", byPrice);

        // 北京天安门（116.397,39.909）周边 50km 内的商品
        SearchResult byGeo = products.search("*", new SearchOptions()
                .filter(new GeoFilter("location", 116.397, 39.909, 50, GeoFilter.Unit.KILOMETERS))
                .returnFields("title", "location")
                .page(0, 10));
        printResult("[过滤] 北京中心 50km 内商品", byGeo);

        // TAG 精确匹配：类目含"数码"
        SearchResult byTag = products.search("@category:{数码}", new SearchOptions()
                .returnFields("title", "category")
                .page(0, 10));
        printResult("[过滤] TAG @category:{数码}", byTag);
    }

    /** 高亮（HIGHLIGHT）与摘要（SUMMARIZE）。 */
    private static void highlightAndSummary(RediSearch products) {
        SearchResult result = products.search("m3", new SearchOptions()
                .highlightOptions(new HighlightOptions()
                        .fields("title", "description")
                        .tags(new Tags("<b>", "</b>")))
                .summaryOptions(new SummaryOptions()
                        .fields("description")
                        .frags(2)
                        .fragSize(25)
                        .separator(" ... "))
                .page(0, 5));
        printResult("[高亮/摘要] 'm3'", result);
    }

    /** 向量检索：KNN 查询。查询向量通过 PARAMS 绑定（$BLOB），需要 dialect 2。 */
    private static void vectorSearch(RediSearch products) {
        SearchResult knn = products.search("*=>[KNN 3 @embedding $BLOB AS score]", new SearchOptions()
                .param("BLOB", Vectors.toFloat32Bytes(1.0f, 0.0f, 0.0f, 0.0f))   // 以"iPhone 方向"的向量查询
                .dialect(2)
                .timeout(2000)
                .returnFields("title", "score")
                .sort(new SortBy("score", SortOrder.ASC))
                .page(0, 3));
        printResult("[向量] KNN 3，查询向量=[1,0,0,0]（score 为余弦距离，越小越近）", knn);
    }

    // ---------------------------------------------------------------- 聚合

    /** 按品牌聚合：计数、均价、最低/最高价、库存总和，再按均价降序。 */
    private static void aggregateDemo(RediSearch products) {
        AggregateResult agg = products.aggregate("*", new AggregateOptions()
                .groups(new Group().fields("@brand").reducers(
                        // 坑：Reducers.count() 的 .as("cnt") 别名会被库忽略（build 里不输出 AS），
                        // 实际列名固定为 __generated_aliascount，因此不能用 @cnt 排序。
                        Reducers.count(),
                        Reducers.avg("@price").as("avg_price"),
                        Reducers.min("@price").as("min_price"),
                        Reducers.max("@price").as("max_price"),
                        Reducers.sum("@stock").as("total_stock")))
                // 注意：聚合 SORTBY 的字段必须带 @ 前缀（如 @avg_price），否则服务端报错
                .sortBy(new io.github.dengliming.redismodule.redisearch.aggregate.SortBy(SortField.desc("@avg_price")))
                .limits(new io.github.dengliming.redismodule.redisearch.search.Page(0, 10)));

        System.out.println("[聚合] 按品牌统计（count 列名为 __generated_aliascount），total=" + agg.getTotal());
        for (Map<String, Object> row : agg.getRows()) {
            System.out.println("    " + row);
        }
    }

    // ---------------------------------------------------------------- 输入联想

    /** FT.SUGADD / FT.SUGGET：前缀 + 模糊联想，支持评分与 payload。重复运行时先删再写，保持幂等。 */
    private static void suggestionDemo(RediSearch products) {
        products.deleteSuggestion("iphone 15 pro");
        products.deleteSuggestion("iphone 15");
        products.deleteSuggestion("ipad pro m4");
        products.addSuggestion(new Suggestion("iphone 15 pro", 1.0d, "cat=phone"));
        products.addSuggestion(new Suggestion("iphone 15", 2.0d, "cat=phone"));
        products.addSuggestion(new Suggestion("ipad pro m4", 1.5d, "cat=pad"));
        System.out.println("[联想] 词库条数=" + products.getSuggestionLength());

        List<Suggestion> suggestions = products.getSuggestion("iph", new SuggestionOptions()
                .withScores()
                .withPayloads()
                .maxNum(5)
                .fuzzy());
        System.out.println("[联想] 前缀 'iph'（fuzzy）：");
        for (Suggestion s : suggestions) {
            System.out.println("    term=" + s.getTerm() + ", score=" + s.getScore() + ", payload=" + s.getPayload());
        }

        products.deleteSuggestion("ipad pro m4");
        System.out.println("[联想] 删除一条后条数=" + products.getSuggestionLength());
    }

    // ---------------------------------------------------------------- 其他命令

    /** 别名 / 词典 / 拼写纠错 / 同义词 / 配置 / 索引信息 / TAG 枚举 / 查询计划。 */
    private static void miscDemo(RediSearch products, RediSearchClient client) {
        // FT.ALIASADD / ALIASDEL：给索引起别名（重复运行先删再建，保持幂等）
        try {
            products.deleteAlias("products_alias");
        } catch (Exception ignored) {
            // 别名不存在时删除会报错，忽略
        }
        products.addAlias("products_alias");
        System.out.println("[别名] 添加 products_alias => OK");
        products.deleteAlias("products_alias");
        System.out.println("[别名] 删除 products_alias => OK");

        // FT.DICTADD / DICTDUMP / DICTDEL：词典（可被 spellcheck 引用）
        products.addDict("brand_dict", "apple", "huawei", "xiaomi");
        System.out.println("[词典] dump brand_dict => " + products.dumpDict("brand_dict"));
        products.deleteDict("brand_dict", "xiaomi");
        System.out.println("[词典] 删除 xiaomi 后 => " + products.dumpDict("brand_dict"));

        // FT.SPELLCHECK：对查询词做拼写纠错
        List<MisspelledTerm> misspelled = products.spellCheck("ipnone", new SpellCheckOptions().distance(2));
        System.out.println("[纠错] 'ipnone' => " + misspelled.size() + " 个疑似拼错词");
        for (MisspelledTerm term : misspelled) {
            for (MisspelledSuggestion suggestion : term.getMisspelledSuggestions()) {
                System.out.println("    " + term.getTerm() + " -> " + suggestion.getSuggestion()
                        + " (score=" + suggestion.getScore() + ")");
            }
        }

        // FT.SYNUPDATE / SYNDUMP：同义词组（组 id 不存在时即新建）
        products.updateSynonym(1L, "phone", "手机", "telephone");
        System.out.println("[同义词] dump => " + products.dumpSynonyms());

        // FT.TAGVALS 枚举 TAG 字段的全部取值。
        // 坑：默认 codec(Kryo) 解不了纯字符串列表（报 unregistered class ID），
        // 需要显式用 StringCodec 构造一个句柄来调用。
        RediSearch stringCodecHandle = new RediSearch(StringCodec.INSTANCE, client.getCommandExecutor(), INDEX_NAME);
        System.out.println("[TAG枚举] brand 全部取值 => " + stringCodecHandle.getTagVals("brand"));

        // FT.EXPLAIN：查看查询的执行计划（同样要用 StringCodec 句柄，见上面 TAGVALS 的坑）
        System.out.println("[查询计划] EXPLAIN 'iphone' =>");
        System.out.println("    " + stringCodecHandle.explain("iphone"));

        // FT.INFO（loadIndex 返回 Map）与 FT._LIST
        Map<String, Object> info = products.loadIndex();
        System.out.println("[索引信息] index_name=" + info.get("index_name")
                + ", num_docs=" + info.get("num_docs")
                + ", index_options=" + info.get("index_options"));
        System.out.println("[索引列表] " + products.listIndexes());

        // FT.CONFIG SET/GET（Redis 8 上自动回退到 CONFIG SET/GET search-*）
        try {
            products.setConfig(ConfigOption.TIMEOUT, "500");
            Map<String, String> configs = products.getConfig(ConfigOption.TIMEOUT);
            System.out.println("[配置] FT.CONFIG TIMEOUT => " + configs);
        } catch (Exception e) {
            System.out.println("[配置] 设置/读取 TIMEOUT 失败（Redis 8 个别版本无此参数，属预期）: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 异步 & 批量

    /** 每个同步方法都有 *Async 孪生方法，返回 RFuture。 */
    private static void asyncDemo(RediSearch products) throws Exception {
        RFuture<SearchResult> future = products.searchAsync("xiaomi", new SearchOptions()
                .returnFields("title", "price"));
        SearchResult result = future.get();
        printResult("[异步] searchAsync('xiaomi')", result);
    }

    /** 批量管线：一批 *Async 调用在 execute() 时一次性发出，减少 RTT。 */
    private static void batchDemo(RediSearchClient client) {
        RediSearchBatch batch = client.createRediSearchBatch();
        RediSearch bIdx = batch.getRediSearch(INDEX_NAME);
        bIdx.searchAsync("sony", new SearchOptions().returnFields("title", "price"));
        bIdx.searchAsync("*", new SearchOptions()
                .filter(new NumericFilter("price", 0, 5000))
                .returnFields("title", "price")
                .sort(new SortBy("price", SortOrder.ASC)));

        BatchResult<?> result = batch.execute();
        System.out.println("[批量] 一次 execute 发出 2 条 FT.SEARCH，返回 " + result.getResponses().size() + " 个结果");
        for (Object resp : result.getResponses()) {
            if (resp instanceof SearchResult) {
                printResult("[批量] 子查询", (SearchResult) resp);
            }
        }
    }

    // ---------------------------------------------------------------- 清理

    /** 删除示例 HASH。索引本身保留（客户端未封装 FT.DROPINDEX，见 README）。 */
    private static void cleanup(RedissonClient redisson) {
        redisson.getKeys().deleteByPattern(KEY_PREFIX + "*");
        System.out.println("[清理] 已删除 " + KEY_PREFIX + "* 示例数据（索引保留）");
    }

    // ---------------------------------------------------------------- 工具方法

    private static void printResult(String label, SearchResult result) {
        System.out.println(label + " => total=" + result.getTotal());
        for (Document doc : result.getDocuments()) {
            Map<String, Object> fields = doc.getFields();
            Map<String, Object> printable = new java.util.LinkedHashMap<>();
            if (fields != null) {
                fields.forEach((k, v) -> printable.put(k,
                        (v instanceof byte[] || "embedding".equals(k)) ? "<blob>" : v));
            }
            System.out.println("    id=" + doc.getId()
                    + ", score=" + doc.getScore()
                    + ", fields=" + printable);
        }
    }
}
