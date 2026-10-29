package com.yummi.redis.demo.rest.json;

import com.alibaba.fastjson2.JSON;
import io.github.dengliming.redismodule.redisjson.RedisJSON;
import io.github.dengliming.redismodule.redisjson.RedisJSONBatch;
import io.github.dengliming.redismodule.redisjson.args.GetArgs;
import io.github.dengliming.redismodule.redisjson.args.SetArgs;
import io.github.dengliming.redismodule.redisjson.client.RedisJSONClient;
import org.redisson.api.BatchOptions;
import org.redisson.api.RFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/json")
public class RedisJsonController {

    @Autowired
    private RedisJSONClient redisJSONClient;

    @PostMapping("/set/{key}")
    public Map<String, Object> set(@PathVariable("key") String key, @RequestBody Product product) {
        SetArgs setArgs = SetArgs.Builder.create("$", JSON.toJSONString(product));
        String result = redisJSONClient.getRedisJSON().set(key, setArgs);
        return success("JSON 设置成功", Map.of(
                "key", key,
                "result", result));
    }

    @GetMapping("/get/{key}")
    public Map<String, Object> get(@PathVariable("key") String key) {
        Product product = redisJSONClient.getRedisJSON().get(key, Product.class, new GetArgs());
        return success("JSON 获取成功", Map.of(
                "key", key,
                "product", product));
    }

    @GetMapping("/get/{key}/{path}")
    public Map<String, Object> getByPath(@PathVariable("key") String key,
                                         @PathVariable("path") String path) {
        Object value = redisJSONClient.getRedisJSON().get(key, Object.class, new GetArgs().path(path));
        return success("JSON 路径获取成功", Map.of(
                "key", key,
                "path", path,
                "value", value));
    }

    @DeleteMapping("/del/{key}")
    public Map<String, Object> del(@PathVariable("key") String key) {
        long deleted = redisJSONClient.getRedisJSON().del(key, "$");
        return success("JSON 删除成功", Map.of(
                "key", key,
                "deleted", deleted));
    }

    @GetMapping("/type/{key}/{path}")
    public Map<String, Object> type(@PathVariable("key") String key,
                                    @PathVariable("path") String path) {
        Class<?> type = redisJSONClient.getRedisJSON().getType(key, path);
        return success("JSON 类型查询成功", Map.of(
                "key", key,
                "path", path,
                "type", type == null ? "null" : type.getSimpleName()));
    }

    @GetMapping("/objLen/{key}")
    public Map<String, Object> objLen(@PathVariable("key") String key) {
        long len = redisJSONClient.getRedisJSON().objLen(key, "$");
        return success("对象字段数查询成功", Map.of(
                "key", key,
                "length", len));
    }

    @GetMapping("/objKeys/{key}/{path}")
    public Map<String, Object> objKeys(@PathVariable("key") String key,
                                       @PathVariable("path") String path) {
        var keys = redisJSONClient.getRedisJSON().objKeys(key, path);
        return success("对象字段名查询成功", Map.of(
                "key", key,
                "path", path,
                "keys", keys));
    }

    @GetMapping("/strLen/{key}/{path}")
    public Map<String, Object> strLen(@PathVariable("key") String key,
                                      @PathVariable("path") String path) {
        long len = redisJSONClient.getRedisJSON().strLen(key, path);
        return success("字符串长度查询成功", Map.of(
                "key", key,
                "path", path,
                "length", len));
    }

    @GetMapping("/arrLen/{key}/{path}")
    public Map<String, Object> arrLen(@PathVariable("key") String key,
                                      @PathVariable("path") String path) {
        long len = redisJSONClient.getRedisJSON().arrLen(key, path);
        return success("数组长度查询成功", Map.of(
                "key", key,
                "path", path,
                "length", len));
    }

    @GetMapping("/batch")
    public Map<String, Object> batch() throws ExecutionException, InterruptedException {
        BatchOptions batchOptions = BatchOptions.defaults()
                // 开启原子？Redis batch 不是事务！除非开multi，这里是pipeline
                // .atomic() // 慎用，atomic是MULTI/EXEC，失败会回滚；默认只是pipeline，无原子
                .responseTimeout(3000, TimeUnit.MILLISECONDS);
        RedisJSONBatch batch = redisJSONClient.createRedisJSONBatch(batchOptions);
        // 获取batch内的RedisJSON对象：所有命令都通过这个对象提交，才会进入batch缓存
        RedisJSON jsonClient = batch.getRedisJSON();

        // 构造两个商品
        Product p1 = new Product(
                1001L,
                "A款羊奶饼干",
                new BigDecimal("29.90"),
                List.of("零食", "无蔗糖"),
                Map.of("netWeight", "160g", "brand", "卓牧")
        );
        Product p2 = new Product(
                1002L,
                "纯羊奶",
                new BigDecimal("39.90"),
                List.of("乳品", "A2蛋白"),
                Map.of("spec", "200ml*6盒")
        );

        // 1. JSON.SET 写入整对象
        SetArgs setArgs1 = SetArgs.Builder.create("$", JSON.toJSONString(p1));
        jsonClient.setAsync("product:1001", setArgs1);
        SetArgs setArgs2 = SetArgs.Builder.create("$", JSON.toJSONString(p2));
        jsonClient.setAsync("product:1002", setArgs2);

        // 2. 读取完整对象，拿到RFuture（execute后取值）
        RFuture<Product> p1Async = jsonClient.getAsync("product:1001", Product.class, new GetArgs());
        RFuture<Product> p2Async = jsonClient.getAsync("product:1002", Product.class, new GetArgs());

        // 3. tags数组追加一个标签
        jsonClient.arrAppendAsync("product:1001", "$.tags", "早餐");
        // 批量提交
        batch.execute();
        Product product = p1Async.get();
        Product product1 = p2Async.get();

        return success("批量操作执行成功", Map.of(
                "product:1001", product,
                "product:1002", product1));
    }


    private Map<String, Object> success(String message, Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", message);
        result.put("data", data);
        return result;
    }
}