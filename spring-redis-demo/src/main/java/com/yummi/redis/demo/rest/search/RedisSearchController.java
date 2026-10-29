package com.yummi.redis.demo.rest.search;

import io.github.dengliming.redismodule.redisearch.client.RediSearchClient;
import io.github.dengliming.redismodule.redisearch.index.IndexDefinition;
import io.github.dengliming.redismodule.redisearch.index.IndexOptions;
import io.github.dengliming.redismodule.redisearch.index.schema.DistanceMetric;
import io.github.dengliming.redismodule.redisearch.index.schema.Field;
import io.github.dengliming.redismodule.redisearch.index.schema.FieldType;
import io.github.dengliming.redismodule.redisearch.index.schema.Schema;
import io.github.dengliming.redismodule.redisearch.index.schema.TagField;
import io.github.dengliming.redismodule.redisearch.index.schema.TextField;
import io.github.dengliming.redismodule.redisearch.index.schema.VectorAlgorithm;
import io.github.dengliming.redismodule.redisearch.index.schema.VectorField;
import io.github.dengliming.redismodule.redisearch.index.schema.VectorType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/search")
public class RedisSearchController {
    // 使用独立命名空间，避免和业务 key / 已有索引冲突（真实接入时按业务规划前缀即可）
    private static final String INDEX_NAME = "idx:demo_products";
    private static final String KEY_PREFIX = "demo:product:";

    @Autowired
    private RediSearchClient redisSearchClient;

    /**
     * 创建商品索引，覆盖 5 种字段类型：
     * TEXT（可带权重/后缀字典树）、TAG（可自定义分隔符）、NUMERIC（可排序）、GEO、VECTOR（HNSW/FLAT）。
     * IndexDefinition 声明"索引哪些 key"：这里用前缀 product: 把原生 HASH 纳入索引。
     */
    public void createIndex() {
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

        try {
            boolean ok = redisSearchClient.getRediSearch(INDEX_NAME).createIndex(schema, options);
            System.out.println("[建索引] createIndex => " + ok);
        } catch (Exception e) {
            // 第二次运行索引已存在会抛异常，这里做幂等处理；若想真正重建，见 README「注意事项」
            System.out.println("[建索引] 索引已存在，跳过创建（" + e.getMessage() + "）");
        }
    }
}
