package com.yummi.redis.demo.rest.bloom;

import io.github.dengliming.redismodule.redisbloom.client.RedisBloomClient;
import io.github.dengliming.redismodule.redisbloom.model.TDigestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.AbstractMap;
import java.util.List;

@RestController
@RequestMapping("/tdigest")
public class RedisTDigestController {

    @Autowired
    private RedisBloomClient redisBloomClient;

    /// ① 创建TDigest sketch，compression压缩系数，推荐100
    @GetMapping("/init/{name}/{compression}")
    public void init(@PathVariable("name") String name, @PathVariable("compression") Integer compression) {
        redisBloomClient.getTDigest(name).create(compression);
    }

    @GetMapping("/reset/{name}")
    public void reset(@PathVariable("name") String name) {
        redisBloomClient.getTDigest(name).reset();
    }

    /// ② 添加样本（value + weight权重）
    @GetMapping("/add/{name}/{value}/{weight}")
    public void add(@PathVariable("name") String name, @PathVariable("value") Double value, @PathVariable("weight") Double weight) {
        redisBloomClient.getTDigest(name).add(List.of(new AbstractMap.SimpleEntry<>(value, weight)));
    }

    @GetMapping("/min/{name}")
    public Double getMin(@PathVariable("name") String name) {
        return redisBloomClient.getTDigest(name).getMin();
    }

    @GetMapping("/max/{name}")
    public Double getMax(@PathVariable("name") String name) {
        return redisBloomClient.getTDigest(name).getMax();
    }

    /// ④ 计算分位数 P50 P90 P95 P99
    @GetMapping("/quantile/{name}/{value}")
    public List<Double> getQuantile(@PathVariable("name") String name, @PathVariable("value") Double value) {
        return redisBloomClient.getTDigest(name).getQuantile(value);
    }

    /// ⑤ CDF：给定value，求<=该值的数据占比
    @GetMapping("/cdf/{name}/{value}")
    public List<Double> getCdf(@PathVariable("name") String name, @PathVariable("value") Double value) {
        return redisBloomClient.getTDigest(name).getCdf(value);
    }

    @GetMapping("/mergeTo/{name1}/{name2}")
    public void mergeTo(@PathVariable("name1") String name1, @PathVariable("name2") String name2) {
        redisBloomClient.getTDigest(name1).mergeTo(name2);
    }

    /// ⑥ RANK：求value对应的排名（从0开始，总权重里小于该值的数量）
    @GetMapping("/rank/{name}/{value}")
    public List<Integer> getRank(@PathVariable("name") String name, @PathVariable("value") Double value) {
        return redisBloomClient.getTDigest(name).rank(value);
    }

    @GetMapping("/revRank/{name}/{value}")
    public List<Integer> getRevRank(@PathVariable("name") String name, @PathVariable("value") Double value) {
        return redisBloomClient.getTDigest(name).revRank(value);
    }

    /// ⑧ BYRANK：根据排名反查数值
    @GetMapping("/byRank/{name}/{rank}")
    public List<Double> getByRank(@PathVariable("name") String name, @PathVariable("rank") Integer rank) {
        return redisBloomClient.getTDigest(name).byRank(rank);
    }

    @GetMapping("/getInfo/{name}")
    public TDigestInfo getInfo(@PathVariable("name") String name) {
        return redisBloomClient.getTDigest(name).getInfo();
    }
}
