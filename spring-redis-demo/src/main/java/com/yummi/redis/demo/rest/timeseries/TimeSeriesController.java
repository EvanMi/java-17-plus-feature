package com.yummi.redis.demo.rest.timeseries;

import io.github.dengliming.redismodule.redistimeseries.Aggregation;
import io.github.dengliming.redismodule.redistimeseries.Align;
import io.github.dengliming.redismodule.redistimeseries.DuplicatePolicy;
import io.github.dengliming.redismodule.redistimeseries.GroupByOptions;
import io.github.dengliming.redismodule.redistimeseries.Label;
import io.github.dengliming.redismodule.redistimeseries.RangeOptions;
import io.github.dengliming.redismodule.redistimeseries.RedisTimeSeries;
import io.github.dengliming.redismodule.redistimeseries.Reducer;
import io.github.dengliming.redismodule.redistimeseries.Sample;
import io.github.dengliming.redismodule.redistimeseries.TimeSeries;
import io.github.dengliming.redismodule.redistimeseries.TimeSeriesOptions;
import io.github.dengliming.redismodule.redistimeseries.client.RedisTimeSeriesClient;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/timeseries")
public class TimeSeriesController {
    private static final String KEY_PREFIX = "metric:cpu:server_";

    @Autowired
    private RedisTimeSeriesClient redisTimeSeriesClient;

    @GetMapping("/create/{id}/{region}")
    public Map<String, Object> create(@PathVariable("id") String id, @PathVariable("region") String region) {
        var tsKey = getTsKey(id);
        var options = new TimeSeriesOptions()
                .retentionTime(3600_000L)
                .duplicatePolicy(DuplicatePolicy.LAST)
                .labels(new Label("metric", "cpu_usage"), new Label("server", "server_" + id), new Label("region", region));
        getClient().create(tsKey, options);
        return success("时序创建成功", Map.of(
                "key", tsKey,
                "retentionTime", 3600_000L,
                "duplicatePolicy", "LAST",
                "labels", List.of("metric=cpu_usage", "server=server_" + id, "region=" + region)));
    }

    @GetMapping("/alter/{id}/{retentionTime}")
    public Map<String, Object> alter(@PathVariable("id") String id, @PathVariable("retentionTime") Long retentionTime) {
        var tsKey = getTsKey(id);
        var options = new TimeSeriesOptions()
                .retentionTime(retentionTime)
                .duplicatePolicy(DuplicatePolicy.LAST);
        getClient().alter(tsKey, options);
        return success("保留时间已修改", Map.of(
                "key", tsKey,
                "retentionTime", retentionTime,
                "duplicatePolicy", "LAST"));
    }

    @GetMapping("/add/{id}/{value}")
    public Map<String, Object> add(@PathVariable("id") String id, @PathVariable("value") Double value) {
        var tsKey = getTsKey(id);
        var ts = System.currentTimeMillis();
        var timestamps = getClient().add(
                new Sample(tsKey, new Sample.Value(ts, value)),
                new Sample(tsKey, new Sample.Value(ts + 10, value)),
                new Sample(tsKey, new Sample.Value(ts + 20, value)));

        var options = new TimeSeriesOptions()
                .retentionTime(3600_000L)
                .duplicatePolicy(DuplicatePolicy.LAST);
        // 不存在时创建（使用 options 全部信息），已存在时仅 DuplicatePolicy 生效
        var tsWithOptions = getClient().add(new Sample(tsKey, new Sample.Value(ts + 25, value)), options);

        return success("样本写入完成", Map.of(
                "key", tsKey,
                "value", value,
                "timestamps", timestamps,
                "lastTimestamp", tsWithOptions));
    }

    /// - sourceKey：原始明细时序（原始高频数据，比如 1s 一个点）
    /// - destKey：聚合后的时序（目标时序，必须**提前手动创建好**，规则不会自动创建 destKey）
    /// - aggregationType：聚合方式 AVG / SUM / MAX / MIN / COUNT 等
    /// - timeBucket：聚合桶大小，单位 `ms`
    ///
    /// 核心规则：
    /// 1. destKey 必须先 `TS.CREATE`；
    /// 2. 数据写入 source 后，RedisTimeSeries 后台自动按桶聚合写入 dest；
    /// 3. 聚合是**增量实时**，不是离线一次性计算；
    /// 4. 适合冷热分离：明细短保留，聚合长保留
    @GetMapping("/createRule/{sourceKey}/{destKey}/{aggregationType}/{timeBucket}")
    public Map<String, Object> createRule(@PathVariable("sourceKey") String sourceKey,
                                          @PathVariable("destKey") String destKey,
                                          @PathVariable("aggregationType") String aggregationType,
                                          @PathVariable("timeBucket") Long timeBucket) {
        boolean created = getClient().createRule(sourceKey, destKey, Aggregation.valueOf(aggregationType), timeBucket);
        return success("聚合规则创建成功", Map.of(
                "sourceKey", sourceKey,
                "destKey", destKey,
                "aggregationType", aggregationType,
                "timeBucket", timeBucket,
                "created", created));
    }

    @GetMapping("/delRule/{sourceKey}/{destKey}")
    public Map<String, Object> delRule(@PathVariable("sourceKey") String sourceKey, @PathVariable("destKey") String destKey) {
        boolean deleted = getClient().deleteRule(sourceKey, destKey);
        return success("聚合规则删除成功", Map.of(
                "sourceKey", sourceKey,
                "destKey", destKey,
                "deleted", deleted));
    }

    /// 对应原生命令：`TS.DECRBY`
    /// 作用：**时序计数器递减**，和 `TS.INCRBY` 成对。
    ///
    /// - 适合：指标是计数器、需要做增减（比如剩余库存、剩余连接数）
    /// - `timestamp=0`：Redis 自动使用当前毫秒时间戳
    /// - `options` 规则同 `add`：仅 key 不存在时自动新建时序才生效；已存在时只有 duplicatePolicy 有意义
    @GetMapping("/decrBy/{id}/{value}")
    public Map<String, Object> decrBy(@PathVariable("id") String id, @PathVariable("value") Double value) throws InterruptedException {
        var options = new TimeSeriesOptions()
                .retentionTime(3600_000L)
                .duplicatePolicy(DuplicatePolicy.LAST);
        var tsKey = getTsKey(id);
        var ts1 = getClient().decrBy(tsKey, value);
        TimeUnit.SECONDS.sleep(1);
        var ts2 = getClient().decrBy(tsKey, value, System.currentTimeMillis() + 20);
        TimeUnit.SECONDS.sleep(1);
        var ts3 = getClient().decrBy(tsKey, value, System.currentTimeMillis() + 50, options);
        return success("计数器递减完成", Map.of(
                "key", tsKey,
                "value", value,
                "timestamps", List.of(ts1, ts2, ts3)));
    }

    @GetMapping("/incrBy/{id}/{value}")
    public Map<String, Object> incrBy(@PathVariable("id") String id, @PathVariable("value") Double value) throws InterruptedException {
        var options = new TimeSeriesOptions()
                .retentionTime(3600_000L)
                .duplicatePolicy(DuplicatePolicy.LAST);
        var tsKey = getTsKey(id);
        var ts1 = getClient().incrBy(tsKey, value);
        TimeUnit.SECONDS.sleep(1);
        var ts2 = getClient().incrBy(tsKey, value, System.currentTimeMillis() + 20);
        TimeUnit.SECONDS.sleep(1);
        var ts3 = getClient().incrBy(tsKey, value, System.currentTimeMillis() + 50, options);
        return success("计数器递增完成", Map.of(
                "key", tsKey,
                "value", value,
                "timestamps", List.of(ts1, ts2, ts3)));
    }

    @GetMapping("/get/{id}")
    public Map<String, Object> get(@PathVariable("id") String id) {
        var value = getClient().get(getTsKey(id));
        return success("最新样本查询完成", Map.of(
                "key", getTsKey(id),
                "sample", value == null ? null : Map.of(
                        "timestamp", value.getTimestamp(),
                        "value", value.getValue())));
    }

    @GetMapping("/info/{id}")
    public Map<String, Object> info(@PathVariable("id") String id) {
        var info = getClient().info(getTsKey(id));
        return success("时序信息查询完成", Map.of(
                "key", getTsKey(id),
                "info", info));
    }

    /// 对应原生命令：`TS.MGET`
    /// 作用：**根据标签过滤器，批量匹配多个时序，取出每个时序的最新一个样本点**
    ///
    /// - `withLabels`：`true` = 返回结果里带上该时序的标签；`false` = 只返回 key、时间戳、值
    /// - `filters`：标签过滤条件，格式 `k=v`，多个 filter 是 AND 关系
    /// - 返回：`List<TimeSeries>`，每一条代表一个匹配到的时序及其最新样本
    @GetMapping("/mget/{withLabels}/{filter}")
    public Map<String, Object> mget(@PathVariable("withLabels") boolean withLabels, @PathVariable("filter") String filter) {
        var result = getClient().mget(withLabels, filter);
        return success("MGET 批量查询完成", Map.of(
                "withLabels", withLabels,
                "filter", filter,
                "series", toTimeSeriesList(result)));
    }

    /// 对应原生命令：`TS.MRANGE`
    /// 按标签过滤一批时序，查询【时间区间内】的采样点，支持聚合、排序、分组（groupby）
    ///
    /// - `from`：起始时间戳（ms）
    /// - `to`：结束时间戳（ms）
    /// - `RangeOptions`：聚合、count、align、reverse 等
    /// - `GroupByOptions`：按标签分组聚合
    /// - `filters`：标签过滤条件，多个是 AND
    @GetMapping("/mrange/{from}/{to}/{aggregationType}/{timeBucket}/{filter}")
    public Map<String, Object> mrange(@PathVariable("from") long from,
                                      @PathVariable("to") long to,
                                      @PathVariable("aggregationType") String aggregationType,
                                      @PathVariable("timeBucket") long timeBucket,
                                      @PathVariable("filter") String filter) {
        RangeOptions rangeOptions = new RangeOptions()
                .aggregationType(Aggregation.valueOf(aggregationType), timeBucket, Align.END)
                .withLabels()
                .max(10);

        var mrangeList = getClient().mrange(from, to, rangeOptions, filter);

        GroupByOptions groupByOptions = new GroupByOptions()
                .groupBy("region", Reducer.SUM);
        String[] filters = new String[]{filter};
        List<TimeSeries> groupMrangeList = getClient().mrange(from, to, rangeOptions, groupByOptions, filters);

        return success("MRANGE 范围查询完成", Map.of(
                "from", from,
                "to", to,
                "aggregationType", aggregationType,
                "timeBucket", timeBucket,
                "filter", filter,
                "series", toTimeSeriesList(mrangeList),
                "groupByRegion", toTimeSeriesList(groupMrangeList)));
    }

    @GetMapping("/queryIndex/{filter}")
    public Map<String, Object> queryIndex(@PathVariable("filter") String filter) {
        var indexList = getClient().queryIndex(filter);
        return success("索引查询完成", Map.of(
                "filter", filter,
                "indexes", indexList));
    }

    @GetMapping("/range/{id}/{from}/{to}")
    public Map<String, Object> range(@PathVariable("id") String id,
                                     @PathVariable("from") long from,
                                     @PathVariable("to") long to) {
        var rangeList1 = getClient().range(getTsKey(id), from, to);
        RangeOptions rangeOptions = new RangeOptions()
                .aggregationType(Aggregation.AVG, 5 * 60 * 1000L, Align.END)
                .withLabels()
                .max(10);
        var rangeList2 = getClient().range(getTsKey(id), from, to, rangeOptions);
        return success("范围查询完成", Map.of(
                "key", getTsKey(id),
                "from", from,
                "to", to,
                "rawSamples", toValueList(rangeList1),
                "aggregatedSamples", toValueList(rangeList2)));
    }

    @GetMapping("/revRange/{id}/{from}/{to}")
    public Map<String, Object> revRange(@PathVariable("id") String id,
                                        @PathVariable("from") long from,
                                        @PathVariable("to") long to) {
        var rangeList1 = getClient().revRange(getTsKey(id), from, to);
        RangeOptions rangeOptions = new RangeOptions()
                .aggregationType(Aggregation.AVG, 5 * 60 * 1000L, Align.END)
                .withLabels()
                .max(10);
        var rangeList2 = getClient().revRange(getTsKey(id), from, to, rangeOptions);
        return success("反向范围查询完成", Map.of(
                "key", getTsKey(id),
                "from", from,
                "to", to,
                "rawSamples", toValueList(rangeList1),
                "aggregatedSamples", toValueList(rangeList2)));
    }

    private RedisTimeSeries getClient() {
        return this.redisTimeSeriesClient.getRedisTimeSeries();
    }

    private static @NonNull String getTsKey(String id) {
        return KEY_PREFIX + id;
    }

    private List<Map<String, Object>> toValueList(List<Sample.Value> values) {
        return values.stream()
                .map(v -> Map.<String, Object>of("timestamp", v.getTimestamp(), "value", v.getValue()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> toTimeSeriesList(List<TimeSeries> series) {
        return series.stream()
                .map(ts -> Map.<String, Object>of(
                        "key", ts.getKey(),
                        "labels", ts.getLabels().stream()
                                .map(l -> l.getKey() + "=" + l.getValue())
                                .collect(Collectors.toList()),
                        "values", toValueList(ts.getValues())))
                .collect(Collectors.toList());
    }

    private Map<String, Object> success(String message, Map<String, Object> data) {
        return Map.of(
                "success", true,
                "message", message,
                "data", data);
    }
}