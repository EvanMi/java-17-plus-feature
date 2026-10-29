package com.yummi.redis.demo.rest.bloom;

import io.github.dengliming.redismodule.redisbloom.BloomFilter;
import io.github.dengliming.redismodule.redisbloom.client.RedisBloomClient;
import io.github.dengliming.redismodule.redisbloom.model.BloomFilterInfo;
import io.github.dengliming.redismodule.redisbloom.model.ChunksData;
import io.github.dengliming.redismodule.redisbloom.model.InsertArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bloom")
public class RedisBloomController {

    @Autowired
    private RedisBloomClient redisBloomClient;


    @GetMapping("/create/{name}/{capacity}/{errorRate}")
    public Map<String, Object> create(@PathVariable("name") String name,
                                      @PathVariable("capacity") Long capacity,
                                      @PathVariable("errorRate") Double errorRate) {
        redisBloomClient.getRBloomFilter(name).create(errorRate, capacity);
        return success("过滤器创建成功", Map.of(
                "name", name,
                "capacity", capacity,
                "errorRate", errorRate));
    }

    @GetMapping("/add/{name}/{item}")
    public Map<String, Object> add(@PathVariable("name") String name, @PathVariable("item") String item) {
        boolean added = redisBloomClient.getRBloomFilter(name).add(item);
        return success("元素添加完成", Map.of(
                "name", name,
                "item", item,
                "isNewlyAdded", added));
    }

    @GetMapping("mAdd/{name}/{item1}/{item2}")
    public Map<String, Object> mAdd(@PathVariable("name") String name,
                                    @PathVariable("item1") String item1,
                                    @PathVariable("item2") String item2) {
        List<Boolean> results = redisBloomClient.getRBloomFilter(name).madd(item1, item2);
        return success("批量添加完成", Map.of(
                "name", name,
                "items", List.of(item1, item2),
                "isNewlyAdded", results));
    }


    @GetMapping("/exists/{name}/{item}")
    public Map<String, Object> exists(@PathVariable("name") String name, @PathVariable("item") String item) {
        boolean isExist = redisBloomClient.getRBloomFilter(name).exists(item);
        return success("查询完成", Map.of(
                "name", name,
                "item", item,
                "isExist", isExist));
    }

    @GetMapping("/mExists/{name}/{item1}/{item2}")
    public Map<String, Object> mExists(@PathVariable("name") String name,
                                       @PathVariable("item1") String item1,
                                       @PathVariable("item2") String item2) {
        List<Boolean> results = redisBloomClient.getRBloomFilter(name).existsMulti(item1, item2);
        return success("查询完成", Map.of(
                "name", name,
                "items", List.of(item1, item2),
                "isExist", results));
    }

    @GetMapping("/info/{name}")
    public Map<String, Object> info(@PathVariable("name") String name) {
        BloomFilterInfo info = redisBloomClient.getRBloomFilter(name).getInfo();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("capacity", info.getCapacity());
        data.put("size", info.getSize());
        data.put("insertedNum", info.getInsertedNum());
        data.put("filterNum", info.getFilterNum());
        data.put("expansionRate", info.getExpansionRate());
        return success("过滤器信息", data);
    }


    @GetMapping("/insert/{name}/{capacity}/{errorRate}/{item1}/{item2}")
    public Map<String, Object> insert(@PathVariable("name") String name,
                                      @PathVariable("capacity") Integer capacity,
                                      @PathVariable("errorRate") Double errorRate,
                                      @PathVariable("item1") String item1,
                                      @PathVariable("item2") String item2) {
        redisBloomClient.getRBloomFilter(name)
                .insert(new InsertArgs(capacity, errorRate, 2), item1, item2);
        return success("插入并创建成功", Map.of(
                "name", name,
                "capacity", capacity,
                "errorRate", errorRate,
                "items", List.of(item1, item2)));
    }

    private final List<ChunksData> datas = new ArrayList<>();

    @GetMapping("/scan/{name}")
    public Map<String, Object> scan(@PathVariable("name") String name) {
        int iter = 0;
        BloomFilter sourceBloomFilter = redisBloomClient.getRBloomFilter(name);
        datas.clear();

        do {
            var chunk = sourceBloomFilter.scanDump(iter);
            iter = chunk.getIter();
            if (iter != 0) {
                datas.add(chunk);
            }
        } while (iter != 0);

        return success("扫描完成", Map.of(
                "source", name));
    }

    @GetMapping("/load/{name}")
    public Map<String, Object> load(@PathVariable("name") String name) {
        for (var data : datas) {
            redisBloomClient.getRBloomFilter(name).loadChunk(data);
        }
        return success("加载完成", Map.of("name", name));
    }



    private Map<String, Object> success(String message, Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", message);
        result.put("data", data);
        return result;
    }
}