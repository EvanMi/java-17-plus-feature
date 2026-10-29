package com.yummi.redis.demo.rest.bloom;

import io.github.dengliming.redismodule.redisbloom.client.RedisBloomClient;
import io.github.dengliming.redismodule.redisbloom.model.ChunksData;
import io.github.dengliming.redismodule.redisbloom.model.CuckooFilterInfo;
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
@RequestMapping("/cuckoo")
public class RedisCuckooController {

    @Autowired
    private RedisBloomClient redisBloomClient;

    /// capacity = 3 | -- 1 -- | -- 2 -- | -- 3 -- |
    /// bucket = 2   | (1) (2) | (1) (2) | (1) (2) |

    @GetMapping("/init/{name}/{capacity}")
    public Map<String, Object> init(@PathVariable("name") String name, @PathVariable("capacity") Long capacity) {
        redisBloomClient.getCuckooFilter(name).reserve(capacity);
        return success("过滤器初始化成功", Map.of(
                "name", name,
                "capacity", capacity));
    }

    @GetMapping("/add/{name}/{item}")
    public Map<String, Object> add(@PathVariable("name") String name, @PathVariable("item") String item) {
        boolean added = redisBloomClient.getCuckooFilter(name).add(item);
        return success("元素添加完成", Map.of(
                "name", name,
                "item", item,
                "isNewlyAdded", added));
    }

    @GetMapping("/addNx/{name}/{item}")
    public Map<String, Object> addNx(@PathVariable("name") String name, @PathVariable("item") String item) {
        boolean added = redisBloomClient.getCuckooFilter(name).addNx(item);
        return success("元素添加完成(不存在才添加)", Map.of(
                "name", name,
                "item", item,
                "isNewlyAdded", added));
    }

    @GetMapping("/insert/{name}/{capacity}/{noCreate}/{item1}/{item2}")
    public Map<String, Object> insert(@PathVariable("name") String name, @PathVariable("capacity") Long capacity,
                                      @PathVariable("noCreate") Boolean noCreate, @PathVariable("item1") String item1,
                                      @PathVariable("item2") String item2) {
        redisBloomClient.getCuckooFilter(name).insert(capacity, noCreate, item1, item2);
        return success("插入完成", Map.of(
                "name", name,
                "capacity", capacity,
                "noCreate", noCreate,
                "items", List.of(item1, item2)));
    }

    @GetMapping("/insertNx/{name}/{capacity}/{noCreate}/{item1}/{item2}")
    public Map<String, Object> insertNx(@PathVariable("name") String name, @PathVariable("capacity") Long capacity,
                                        @PathVariable("noCreate") Boolean noCreate, @PathVariable("item1") String item1,
                                        @PathVariable("item2") String item2) {
        redisBloomClient.getCuckooFilter(name).insertNx(capacity, noCreate, item1, item2);
        return success("插入完成(不存在才插入)", Map.of(
                "name", name,
                "capacity", capacity,
                "noCreate", noCreate,
                "items", List.of(item1, item2)));
    }

    @GetMapping("/exists/{name}/{item}")
    public Map<String, Object> exists(@PathVariable("name") String name, @PathVariable("item") String item) {
        boolean isExist = redisBloomClient.getCuckooFilter(name).exists(item);
        return success("查询完成", Map.of(
                "name", name,
                "item", item,
                "isExist", isExist));
    }

    @GetMapping("/delete/{name}/{item}")
    public Map<String, Object> delete(@PathVariable("name") String name, @PathVariable("item") String item) {
        boolean deleted = redisBloomClient.getCuckooFilter(name).delete(item);
        return success("删除完成", Map.of(
                "name", name,
                "item", item,
                "isDeleted", deleted));
    }

    @GetMapping("/count/{name}/{item}")
    public Map<String, Object> count(@PathVariable("name") String name, @PathVariable("item") String item) {
        Integer count = redisBloomClient.getCuckooFilter(name).count(item);
        return success("计数完成", Map.of(
                "name", name,
                "item", item,
                "count", count));
    }

    @GetMapping("/getInfo/{name}")
    public Map<String, Object> getInfo(@PathVariable("name") String name) {
        CuckooFilterInfo info = redisBloomClient.getCuckooFilter(name).getInfo();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("size", info.getSize());
        data.put("bucketSize", info.getBucketSize());
        data.put("insertedNum", info.getInsertedNum());
        data.put("filterNum", info.getFilterNum());
        data.put("expansionRate", info.getExpansionRate());
        return success("过滤器信息", data);
    }

    private final List<ChunksData> datas = new ArrayList<>();

    @GetMapping("/scanDump/{name}")
    public Map<String, Object> scanDump(@PathVariable("name") String name) {
        int iter = 0;
        datas.clear();

        do {
            var chunk = redisBloomClient.getCuckooFilter(name).scanDump(iter);
            iter = chunk.getIter();
            if (iter != 0) {
                datas.add(chunk);
            }
        } while (iter != 0);

        return success("扫描完成", Map.of("source", name));
    }

    @GetMapping("/loadChunk/{name}")
    public Map<String, Object> loadChunk(@PathVariable("name") String name) {
        for (ChunksData data : datas) {
            redisBloomClient.getCuckooFilter(name).loadChunk(data);
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