package com.yummi.redis.demo.rest.search;

import io.github.dengliming.redismodule.redisearch.RediSearch;
import io.github.dengliming.redismodule.redisearch.aggregate.AggregateOptions;
import io.github.dengliming.redismodule.redisearch.aggregate.AggregateResult;
import io.github.dengliming.redismodule.redisearch.aggregate.Group;
import io.github.dengliming.redismodule.redisearch.aggregate.Reducers;
import io.github.dengliming.redismodule.redisearch.aggregate.SortField;
import io.github.dengliming.redismodule.redisearch.client.RediSearchClient;
import io.github.dengliming.redismodule.redisearch.index.ConfigOption;
import io.github.dengliming.redismodule.redisearch.index.Document;
import io.github.dengliming.redismodule.redisearch.index.DocumentOptions;
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
import io.github.dengliming.redismodule.redisearch.search.MisspelledSuggestion;
import io.github.dengliming.redismodule.redisearch.search.MisspelledTerm;
import io.github.dengliming.redismodule.redisearch.search.NumericFilter;
import io.github.dengliming.redismodule.redisearch.search.SearchOptions;
import io.github.dengliming.redismodule.redisearch.search.SearchResult;
import io.github.dengliming.redismodule.redisearch.search.SortBy;
import io.github.dengliming.redismodule.redisearch.search.SpellCheckOptions;
import io.github.dengliming.redismodule.redisearch.search.SummaryOptions;
import io.github.dengliming.redismodule.redisearch.search.Tags;
import io.github.dengliming.redismodule.redisearch.search.Vectors;
import org.redisson.api.SortOrder;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/search")
public class RedisSearchController {
    // 使用独立命名空间，避免和业务 key / 已有索引冲突（真实接入时按业务规划前缀即可）
    private static final String INDEX_NAME = "idx:demo_products";
    private static final String KEY_PREFIX = "demo:product:";

    @Autowired
    private RediSearchClient redisSearchClient;

    /**
     * <p>创建商品索引，覆盖 5 种字段类型：</p>
     * <p>TEXT（可带权重/后缀字典树）、TAG（可自定义分隔符）、NUMERIC（可排序）、GEO、VECTOR（HNSW/FLAT）。</p>
     * <p>IndexDefinition 声明"索引哪些 key"：这里用前缀 product: 把原生 HASH 纳入索引。</p>
     */
    @GetMapping("/createIndex")
    public Map<String, Object> createIndex() {
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
                        .setPrefixes(List.of(KEY_PREFIX))
                        .setScore(1.0d));
        // 先删索引避免冲突
        try {
            redisSearchClient.getRediSearch(INDEX_NAME).dropIndex(false);
        } catch (Exception ignored) {
        }

        try {
            boolean ok = redisSearchClient.getRediSearch(INDEX_NAME).createIndex(schema, options);
            return Map.of("success", true, "message", "索引创建成功：" + INDEX_NAME, "created", ok);
        } catch (Exception e) {
            // 第二次运行索引已存在会抛异常，这里做幂等处理；若想真正重建，见 README「注意事项」
            return Map.of("success", false, "message", "索引已存在，跳过创建（" + e.getMessage() + "）");
        }
    }


    @GetMapping("/seedProducts")
    public Map<String, Object> seedProducts() {
        RediSearch redisSearch = getRediSearch();

        Document doc1 = new Document(KEY_PREFIX + "1", 1.0,
                Map.of("title", "iPhone 15 Pro",
                        "description", "A17 Pro chip, titanium frame, 120Hz ProMotion display",
                        "brand", "Apple",
                        "category", "手机,数码",
                        "price", 7999,
                        "stock", 120,
                        "location", "116.397,39.909",
                        "embedding", Vectors.toFloat32Bytes(1.0f, 0.0f, 0.0f, 0.0f)));
        boolean addDoc1 = redisSearch.addDocument(doc1);

        // goods:2 HUAWEI Mate 60 Pro，带 DocumentOptions，第二个重载 addDocument(doc, options)
        Document doc2 = new Document(KEY_PREFIX + "2", 0.5D,
                Map.of("title", "HUAWEI Mate 60 Pro",
                        "description", "Kirin 9000s, satellite communication, 50MP camera",
                        "brand", "Huawei",
                        "category", "手机,数码",
                        "price", 6999,
                        "stock", 80,
                        "location", "116.407,39.904",
                        "embedding", Vectors.toFloat32Bytes(0.9f, 0.1f, 0.0f, 0.0f)));
        DocumentOptions docOpts = new DocumentOptions()
                .setLanguage(RSLanguage.ENGLISH)
                .setReplaceCondition(DocumentOptions.ReplacePolicy.NONE, "@stock > 0");

        boolean addDoc2 = redisSearch.addDocument(doc2, docOpts);

        // goods:3 Xiaomi 14，addDocument
        Document doc3 = new Document(KEY_PREFIX + "3", 1.0, "123".getBytes(),
                Map.of("title", "Xiaomi 14",
                        "description", "Snapdragon 8 Gen 3, Leica optics, 90W fast charging",
                        "brand", "Xiaomi",
                        "category", "手机,数码",
                        "price", 3999,
                        "stock", 300,
                        "location", "121.473,31.230",
                        "embedding", Vectors.toFloat32Bytes(0.0f, 0.9f, 0.1f, 0.0f)));
        boolean addDoc3 = redisSearch.addDocument(doc3);


        // goods:4 MacBook Pro 14，addDocument
        Document doc4 = new Document(KEY_PREFIX + "4", 1.0,
                Map.of("title", "MacBook Pro 14",
                        "description", "M3 Pro chip, 18-hour battery, Liquid Retina XDR display",
                        "brand", "Apple",
                        "category", "笔记本,数码",
                        "price", 14999,
                        "stock", 50,
                        "location", "116.397,39.909",
                        "embedding", Vectors.toFloat32Bytes(0.0f, 0.0f, 0.9f, 0.1f)));
        boolean addDoc4 = redisSearch.addDocument(doc4);

        // goods:5 Sony WH-1000XM5，addDocument
        Document doc5 = new Document(KEY_PREFIX + "5", 1.0,
                Map.of("title", "Sony WH-1000XM5",
                        "description", "Industry-leading noise cancelling, 30-hour battery, LDAC",
                        "brand", "Sony",
                        "category", "耳机,音频",
                        "price", 2399,
                        "stock", 200,
                        "location", "113.264,23.129",
                        "embedding", Vectors.toFloat32Bytes(0.1f, 0.2f, 0.3f, 0.4f)));
        boolean addDoc5 = redisSearch.addDocument(doc5);

        return Map.of(
                "success", true,
                "message", "已写入 5 条示例商品数据",
                "docs", List.of(
                        Map.of("id", KEY_PREFIX + "1", "title", "iPhone 15 Pro", "added", addDoc1),
                        Map.of("id", KEY_PREFIX + "2", "title", "HUAWEI Mate 60 Pro", "added", addDoc2),
                        Map.of("id", KEY_PREFIX + "3", "title", "Xiaomi 14", "added", addDoc3),
                        Map.of("id", KEY_PREFIX + "4", "title", "MacBook Pro 14", "added", addDoc4),
                        Map.of("id", KEY_PREFIX + "5", "title", "Sony WH-1000XM5", "added", addDoc5)
                )
        );
    }

    @GetMapping("/getDocument/{docId}")
    public Map<String, Object> getDocument(@PathVariable String docId) {
        RediSearch redisSearch = getRediSearch();
        return redisSearch.getDocument(docId);
    }

    @GetMapping("/addAlias/{aliasName}")
    public Map<String, Object> addAlias(@PathVariable String aliasName) {
        RediSearch redisSearch = getRediSearch();
        redisSearch.addAlias(aliasName);
        return Map.of("success", true, "message", "别名已添加：" + aliasName);
    }

    @GetMapping("/deleteAlias/{aliasName}")
    public Map<String, Object> deleteAlias(@PathVariable String aliasName) {
        RediSearch redisSearch = getRediSearch();
        redisSearch.deleteAlias(aliasName);
        return Map.of("success", true, "message", "别名已删除：" + aliasName);
    }

    @GetMapping("/deleteIndex")
    public Map<String, Object> deleteIndex() {
        RediSearch redisSearch = getRediSearch();
        redisSearch.dropIndex(false);
        return Map.of("success", true, "message", "索引已删除：" + INDEX_NAME);
    }

    @GetMapping("/addDict")
    public Map<String, Object> addDict() {
        RediSearch redisSearch = getRediSearch();
        redisSearch.addDict("goods_custom_dict", "iPhone 15 Pro", "Mate 60 Pro", "WH-1000XM5");
        return Map.of("success", true, "message", "自定义词典已添加：goods_custom_dict");
    }

    @GetMapping("/addSuggestion")
    public Map<String, Object> addSuggestion() {
        RediSearch redisSearch = getRediSearch();

        Suggestion sug2 = new Suggestion("HUAWEI Mate 60 Pro", 18, "goods:2");
        int sugId2 = redisSearch.addSuggestion(sug2, true);

        Suggestion sug3 = new Suggestion("Xiaomi 14", 15, "goods:3");
        int sugId3 = redisSearch.addSuggestion(sug3);

        Suggestion sug4 = new Suggestion("MacBook Pro 14", 12, "goods:4");
        int sugId4 = redisSearch.addSuggestion(sug4);

        Suggestion sug5 = new Suggestion("Sony WH-1000XM5", 10, "goods:5");
        int sugId5 = redisSearch.addSuggestion(sug5);

        return Map.of("success", true, "message", "已添加 4 条联想词",
                "suggestions", List.of(
                        Map.of("term", "HUAWEI Mate 60 Pro", "id", sugId2),
                        Map.of("term", "Xiaomi 14", "id", sugId3),
                        Map.of("term", "MacBook Pro 14", "id", sugId4),
                        Map.of("term", "Sony WH-1000XM5", "id", sugId5)
                ));
    }

    @GetMapping("/addSynonym/{groupId}")
    public Map<String, Object> addSynonym(@PathVariable long groupId) {
        RediSearch redisSearch = getRediSearch();
        //不要在调用add方法了，已经过时，直接调用update方法自定手动指定groupId即可
        redisSearch.updateSynonym(groupId, "phone", "手机", "telephone");
        return Map.of("success", true, "message", "同义词组已添加", "groupId", groupId);
    }

    @GetMapping("/dumpSynonyms")
    public Map<String, Object> dumpSynonyms() {
        RediSearch redisSearch = getRediSearch();
        Map<String, List<Long>> synonyms = redisSearch.dumpSynonyms();
        return Map.of("success", true, "message", "同义词组导出成功", "synonyms", synonyms);
    }

    @GetMapping("/getSuggestion")
    public List<Suggestion> getSuggestion() {
        RediSearch redisSearch = getRediSearch();

        // 查前缀 "HUAWEI" 的联想，同时返回payload
        SuggestionOptions suggestOptions = new SuggestionOptions()
                .withPayloads()
                .fuzzy()
                .maxNum(5);

        return redisSearch.getSuggestion("HUAWEI", suggestOptions);
    }

    @GetMapping("/doComplexSuggestion")
    public Map<String, Object> doComplexSuggestion() {
        RediSearch redisSearch = getRediSearch();

        redisSearch.deleteSuggestion("iphone 15 pro");
        redisSearch.deleteSuggestion("iphone 15");
        redisSearch.deleteSuggestion("ipad pro m4");

        redisSearch.addSuggestion(new Suggestion("iphone 15 pro", 1.0d, "cat=phone"));
        redisSearch.addSuggestion(new Suggestion("iphone 15", 2.0d, "cat=phone"));
        redisSearch.addSuggestion(new Suggestion("ipad pro m4", 1.5d, "cat=pad"));
        long length = redisSearch.getSuggestionLength();

        List<Suggestion> suggestions = redisSearch.getSuggestion("iph", new SuggestionOptions()
                .withScores()
                .withPayloads()
                .maxNum(5)
                .fuzzy());

        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (Suggestion s : suggestions) {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("term", s.getTerm());
            item.put("score", s.getScore());
            item.put("payload", s.getPayload());
            list.add(item);
        }
        return Map.of("success", true, "message", "前缀 'iph' 的联想结果（fuzzy）", "total", length, "suggestions", list);
    }


    @GetMapping("/basicSearch")
    public Map<String, Object> basicSearch() {
        RediSearch redisSearch = getRediSearch();

        SearchResult result = redisSearch.search("iphone", new SearchOptions()
                .withScores()
                .page(0, 5));

        // 指定返回字段（RETURN），只取需要的列。
        // 注意：默认全文检索只匹配 TEXT 字段；"Apple" 只存在 TAG 字段 brand 里，不会被命中（TAG 用 @brand:{Apple} 查）。
        SearchResult fields = redisSearch.search("titanium", new SearchOptions()
                .returnFields("title", "price")
                .page(0, 5));

        // 语言与停用词设置（对英文文本有效；中文分词见 README 注意事项）
        SearchResult lang = redisSearch.search("noise cancelling", new SearchOptions()
                .language(RSLanguage.ENGLISH)
                .noStopwords());

        return Map.of(
                "success", true,
                "message", "基础全文检索演示",
                "keyword_iphone", toSearchResultMap(result),
                "keyword_titanium", toSearchResultMap(fields),
                "keyword_noise_cancelling", toSearchResultMap(lang)
        );
    }

    @GetMapping("/filteredSearch")
    public Map<String, Object> filteredSearch() {
        RediSearch redisSearch = getRediSearch();

        // 价格 3000~9000，按价格降序
        SearchResult byPrice = redisSearch.search("*", new SearchOptions()
                .filter(new NumericFilter("price", 3000, 9000))
                .sort(new SortBy("price", SortOrder.DESC))
                .returnFields("title", "brand", "price")
                .page(0, 10));

        // 北京天安门（116.397,39.909）周边 50km 内的商品
        SearchResult byGeo = redisSearch.search("*", new SearchOptions()
                .filter(new GeoFilter("location", 116.397, 39.909, 50, GeoFilter.Unit.KILOMETERS))
                .returnFields("title", "location")
                .page(0, 10));

        // TAG 精确匹配：类目含"数码"
        SearchResult byTag = redisSearch.search("@category:{数码}", new SearchOptions()
                .returnFields("title", "category")
                .page(0, 10));

        return Map.of(
                "success", true,
                "message", "过滤检索演示（数值/地理/TAG）",
                "by_price_3000_9000", toSearchResultMap(byPrice),
                "by_geo_50km", toSearchResultMap(byGeo),
                "by_tag_数码", toSearchResultMap(byTag)
        );
    }

    @GetMapping("/highlightAndSummary")
    public Map<String, Object> highlightAndSummary() {
        RediSearch redisSearch = getRediSearch();

        SearchResult result = redisSearch.search("m3", new SearchOptions()
                .highlightOptions(new HighlightOptions()
                        .fields("title", "description")
                        .tags(new Tags("<b>", "</b>")))
                .summaryOptions(new SummaryOptions()
                        .fields("description")
                        .frags(2)
                        .fragSize(25)
                        .separator(" ... "))
                .page(0, 5));

        return Map.of("success", true, "message", "高亮与摘要演示（关键词 m3）", "result", toSearchResultMap(result));
    }

    /**
     * 向量检索：KNN 查询。查询向量通过 PARAMS 绑定（$BLOB），需要 dialect 2。
     */
    @GetMapping("/vectorSearch")
    public Map<String, Object> vectorSearch() {
        RediSearch redisSearch = getRediSearch();

        SearchResult knn = redisSearch.search("*=>[KNN 3 @embedding $BLOB AS score]", new SearchOptions()
                .param("BLOB", Vectors.toFloat32Bytes(1.0f, 0.0f, 0.0f, 0.0f))   // 以"iPhone 方向"的向量查询
                .dialect(2)
                .timeout(2000)
                .returnFields("title", "score")
                .sort(new SortBy("score", SortOrder.ASC))
                .page(0, 3));

        return Map.of("success", true, "message", "向量 KNN 检索（iPhone 方向向量，Top 3）", "result", toSearchResultMap(knn));
    }


    private RediSearch getRediSearch() {
        return redisSearchClient.getRediSearch(INDEX_NAME);
    }


    @GetMapping("/aggregateSearch")
    public Map<String, Object> aggregateSearch() {
        RediSearch redisSearch = getRediSearch();

        AggregateResult agg = redisSearch.aggregate("*", new AggregateOptions()
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

        return Map.of("success", true, "message", "按品牌聚合统计（count 列名为 __generated_aliascount）",
                "total", agg.getTotal(), "rows", agg.getRows());
    }


    /** 拼写纠错：FT.SPELLCHECK */
    @GetMapping("/spellCheck")
    public Map<String, Object> spellCheck() {
        RediSearch redisSearch = getRediSearch();
        List<MisspelledTerm> misspelled = redisSearch.spellCheck("ipnone", new SpellCheckOptions().distance(2));
        List<Map<String, Object>> spellList = new java.util.ArrayList<>();
        for (MisspelledTerm term : misspelled) {
            for (MisspelledSuggestion suggestion : term.getMisspelledSuggestions()) {
                Map<String, Object> spellItem = new java.util.LinkedHashMap<>();
                spellItem.put("term", term.getTerm());
                spellItem.put("suggestion", suggestion.getSuggestion());
                spellItem.put("score", suggestion.getScore());
                spellList.add(spellItem);
            }
        }
        return Map.of("success", true, "message", "拼写纠错演示（ipnone）", "spellcheck_ipnone", spellList);
    }

    /** 配置：FT.CONFIG SET/GET（Redis 8 上自动回退到 CONFIG SET/GET search-*） */
    @GetMapping("/config")
    public Map<String, Object> config() {
        RediSearch redisSearch = getRediSearch();
        Object configResult;
        try {
            redisSearch.setConfig(ConfigOption.TIMEOUT, "500");
            configResult = redisSearch.getConfig(ConfigOption.TIMEOUT);
        } catch (Exception e) {
            configResult = "设置/读取 TIMEOUT 失败（Redis 8 个别版本无此参数，属预期）: " + e.getMessage();
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "配置演示（TIMEOUT=500）");
        result.put("config_timeout", configResult);
        return result;
    }

    /** 索引信息：FT.INFO（loadIndex 返回 Map）与 FT._LIST */
    @GetMapping("/indexInfo")
    public Map<String, Object> indexInfo() {
        RediSearch redisSearch = getRediSearch();
        Map<String, Object> info = redisSearch.loadIndex();
        Map<String, Object> indexInfo = Map.of(
                "index_name", info.get("index_name"),
                "num_docs", info.get("num_docs"),
                "index_options", info.get("index_options"));
        Object indexList = redisSearch.listIndexes();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "索引信息演示");
        result.put("index_info", indexInfo);
        result.put("index_list", indexList);
        return result;
    }

    /** TAG 枚举：FT.TAGVALS（需用 StringCodec 句柄，默认 Kryo 解不了纯字符串列表） */
    @GetMapping("/tagVals")
    public Map<String, Object> tagVals() {
        RediSearch stringCodecHandle = new RediSearch(StringCodec.INSTANCE, redisSearchClient.getCommandExecutor(), INDEX_NAME);
        Object tagVals = stringCodecHandle.getTagVals("brand");
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "TAG 枚举演示（brand 字段）");
        result.put("tag_vals_brand", tagVals);
        return result;
    }

    /** 查询计划：FT.EXPLAIN（需用 StringCodec 句柄） */
    @GetMapping("/explain")
    public Map<String, Object> explain() {
        RediSearch stringCodecHandle = new RediSearch(StringCodec.INSTANCE, redisSearchClient.getCommandExecutor(), INDEX_NAME);
        Object explain = stringCodecHandle.explain("iphone");
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "查询计划演示（iphone）");
        result.put("explain_iphone", explain);
        return result;
    }

    /**
     * 将 SearchResult 转换为可序列化的 Map（避免 byte[] / 内部类型导致 JSON 序列化问题）。
     */
    private Map<String, Object> toSearchResultMap(SearchResult result) {
        List<Map<String, Object>> docs = new java.util.ArrayList<>();
        for (Document doc : result.getDocuments()) {
            Map<String, Object> fields = new java.util.LinkedHashMap<>();
            Map<String, Object> raw = doc.getFields();
            if (raw != null) {
                raw.forEach((k, v) -> fields.put(k, (v instanceof byte[] || "embedding".equals(k)) ? "<blob>" : v));
            }
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", doc.getId());
            item.put("score", doc.getScore());
            item.put("fields", fields);
            docs.add(item);
        }
        return Map.of("total", result.getTotal(), "documents", docs);
    }

}
