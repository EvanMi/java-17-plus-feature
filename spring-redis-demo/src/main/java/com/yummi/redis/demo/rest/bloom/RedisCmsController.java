package com.yummi.redis.demo.rest.bloom;

import io.github.dengliming.redismodule.redisbloom.client.RedisBloomClient;
import io.github.dengliming.redismodule.redisbloom.model.CountMinSketchInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
///🧠 核心原理
/// ‌数据结构‌：维护一个d × w的二维计数器数组，搭配d个两两独立的哈希函数。
/// ‌更新操作‌：元素到达后，用每个哈希函数映射到对应行的桶，将所有映射位置的计数器统一+1。
/// ‌查询操作‌：查询频率时，用同样的哈希函数算出d个位置，取其中‌最小值‌作为该元素的频率估计值。
/// ‌误差特性‌：估计值只会‌高估不会低估‌，真实频率f(x) ≤ 估计值，且误差有严格数学上界保证。
@RestController
@RequestMapping("/cms")
public class RedisCmsController {
    @Autowired
    private RedisBloomClient redisBloomClient;

    @GetMapping("/create/{name}")
    public Map<String, Object> create(@PathVariable("name") String name) {
        // 创建：宽度、深度。宽度深度越大，误差越小，内存越大
        redisBloomClient.getCountMinSketch(name + "_0").create(1000, 5);
        // errorRatio: 估算值最多会高估 ε × total_sum
        // probability: 出现「误差超过上面设置的 errorRatio」这件事的概率; 数值越小 → 置信度越高，内部 depth（深度）越大
        // width = e / errorRatio  depth = ln(1 / probability)
        redisBloomClient.getCountMinSketch(name + "_1").create(0.01D, 0.9D);
        return success("两个 Count-Min Sketch 创建成功", Map.of(
                "name", name,
                "sketch1", name + "_0 (宽度1000, 深度5)",
                "sketch2", name + "_1 (errorRatio=0.01, probability=0.9)"));
    }

    @GetMapping("/incrby/{name}/{item1}/{value1}/{item2}/{value2}")
    public Map<String, Object> incrby(@PathVariable("name") String name,
                                      @PathVariable("item1") String item1, @PathVariable("value1") Integer value1,
                                      @PathVariable("item2") String item2, @PathVariable("value2") Integer value2) {
        List<Integer> counts0 = redisBloomClient.getCountMinSketch(name + "_0")
                .incrby(new String[]{item1, item2}, new int[]{value1, value2});
        List<Integer> counts1 = redisBloomClient.getCountMinSketch(name + "_1")
                .incrby(new String[]{item1, item2}, new int[]{value1, value2});
        return success("计数增加完成", Map.of(
                "name", name,
                "items", List.of(item1, item2),
                "increments", List.of(value1, value2),
                "sketch1_minCount", counts0,
                "sketch2_minCount", counts1));
    }

    @GetMapping("/query/{name}/{item1}/{item2}")
    public Map<String, Object> query(@PathVariable("name") String name,
                                     @PathVariable("item1") String item1, @PathVariable("item2") String item2) {
        List<Integer> counts0 = redisBloomClient.getCountMinSketch(name + "_0").query(item1, item2);
        List<Integer> counts1 = redisBloomClient.getCountMinSketch(name + "_1").query(item1, item2);
        return success("查询完成", Map.of(
                "name", name,
                "items", List.of(item1, item2),
                "sketch1_counts", counts0,
                "sketch2_counts", counts1));
    }

    @GetMapping("/getInfo/{name}")
    public Map<String, Object> getInfo(@PathVariable("name") String name) {
        CountMinSketchInfo info0 = redisBloomClient.getCountMinSketch(name + "_0").getInfo();
        CountMinSketchInfo info1 = redisBloomClient.getCountMinSketch(name + "_1").getInfo();
        return success("过滤器信息", Map.of(
                "name", name,
                "sketch1", toInfoMap(name + "_0", info0),
                "sketch2", toInfoMap(name + "_1", info1)));
    }

    @GetMapping("/merge/{name1}/{name2}/{name3}")
    public Map<String, Object> merge(@PathVariable("name1") String name1,
                                     @PathVariable("name2") String name2,
                                     @PathVariable("name3") String name3) {
        boolean merged = redisBloomClient.getCountMinSketch(name3)
                .merge(new String[]{name1}, new int [] {});
        return success("合并完成", Map.of(
                "target", name3,
                "sources", List.of(name1, name2),
                "merged", merged));
    }

    private Map<String, Object> toInfoMap(String name, CountMinSketchInfo info) {
        return Map.of(
                "name", name,
                "width", info.getWidth(),
                "depth", info.getDepth(),
                "count", info.getCount());
    }

    private Map<String, Object> success(String message, Map<String, Object> data) {
        return Map.of(
                "success", true,
                "message", message,
                "data", data);
    }
}