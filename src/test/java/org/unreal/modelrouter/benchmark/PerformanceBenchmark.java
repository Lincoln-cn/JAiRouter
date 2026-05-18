package org.unreal.modelrouter.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.unreal.modelrouter.router.checker.HealthStateBitSet;
import org.unreal.modelrouter.router.loadbalancer.impl.CachedWeightedSelector;
import org.unreal.modelrouter.router.model.ModelRouterProperties.ModelInstance;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * JMH 基准测试套件 - v2.7.x 性能优化验证
 *
 * 测试内容：
 * 1. CachedWeightedSelector vs 传统权重选择
 * 2. HealthStateBitSet vs Map<String, Boolean>
 * 3. 单次 Stream vs 多次 Stream
 *
 * @author JAiRouter Team
 * @since v2.7.13
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class PerformanceBenchmark {

    // ==================== 测试数据 ====================

    @Param({"10", "50", "100", "500"})
    int instanceCount;

    List<ModelInstance> instances;
    CachedWeightedSelector cachedSelector;

    // 传统方法：每次选择都重建权重列表
    Map<String, Integer> traditionalWeights;

    // HealthStateBitSet vs Map
    HealthStateBitSet healthBitSet;
    Map<String, Boolean> healthMap;
    List<String> instanceKeys;

    @Setup(Level.Trial)
    public void setup() {
        // 创建测试实例
        instances = new ArrayList<>();
        traditionalWeights = new HashMap<>();
        instanceKeys = new ArrayList<>();
        healthBitSet = new HealthStateBitSet();
        healthMap = new ConcurrentHashMap<>();

        Random random = ThreadLocalRandom.current();

        for (int i = 0; i < instanceCount; i++) {
            String instanceId = "instance-" + i;
            String baseUrl = "http://localhost:" + (8000 + i);

            ModelInstance instance = new ModelInstance();
            instance.setInstanceId(instanceId);
            instance.setBaseUrl(baseUrl);
            instance.setName("test-model");
            instance.setStatus("active");
            instance.setWeight(1 + random.nextInt(10)); // 权重 1-10
            instances.add(instance);

            // 传统权重
            traditionalWeights.put(instanceId, instance.getWeight());

            // 健康状态键
            String key = "CHAT:" + instanceId;
            instanceKeys.add(key);
            boolean healthy = random.nextBoolean();
            healthBitSet.setHealth(key, healthy);
            healthMap.put(key, healthy);
        }

        // 初始化缓存选择器
        cachedSelector = new CachedWeightedSelector();
    }

    // ==================== 权重选择基准测试 ====================

    /**
     * 传统方法：每次选择都遍历所有实例
     */
    @Benchmark
    public ModelInstance traditionalWeightedSelect(Blackhole bh) {
        int totalWeight = instances.stream()
                .mapToInt(ModelInstance::getWeight)
                .sum();

        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);
        int currentWeight = 0;

        for (ModelInstance instance : instances) {
            currentWeight += instance.getWeight();
            if (currentWeight > randomWeight) {
                return instance;
            }
        }
        return instances.get(0);
    }

    /**
     * 优化方法：Alias Method O(1) 选择
     */
    @Benchmark
    public ModelInstance cachedWeightedSelect(Blackhole bh) {
        return cachedSelector.select(instances);
    }

    // ==================== 健康状态查询基准测试 ====================

    /**
     * 传统方法：Map<String, Boolean> 查询
     */
    @Benchmark
    public boolean traditionalHealthQuery(Blackhole bh) {
        int index = ThreadLocalRandom.current().nextInt(instanceCount);
        String key = instanceKeys.get(index);
        Boolean health = healthMap.get(key);
        return health != null ? health : false;
    }

    /**
     * 优化方法：BitSet 位运算查询
     */
    @Benchmark
    public boolean bitSetHealthQuery(Blackhole bh) {
        int index = ThreadLocalRandom.current().nextInt(instanceCount);
        String key = instanceKeys.get(index);
        return healthBitSet.isHealthy(key, false);
    }

    /**
     * 批量查询：统计健康实例数量
     * 传统方法：遍历 Map
     */
    @Benchmark
    public int traditionalHealthCount(Blackhole bh) {
        int count = 0;
        for (Boolean healthy : healthMap.values()) {
            if (healthy) count++;
        }
        return count;
    }

    /**
     * 批量查询：统计健康实例数量
     * 优化方法：BitSet.cardinality()
     */
    @Benchmark
    public int bitSetHealthCount(Blackhole bh) {
        return healthBitSet.getHealthyCount();
    }

    // ==================== Stream 操作基准测试 ====================

    /**
     * 传统方法：多次 Stream 操作
     */
    @Benchmark
    public List<ModelInstance> multiStreamFilter(Blackhole bh) {
        // 模拟原 ModelServiceRegistry.selectInstance 的多次 Stream
        return instances.stream()
                .filter(i -> "test-model".equals(i.getName()))
                .filter(i -> "active".equalsIgnoreCase(i.getStatus()))
                .filter(i -> healthMap.getOrDefault("CHAT:" + i.getInstanceId(), false))
                .collect(Collectors.toList());
    }

    /**
     * 优化方法：单次 Stream 操作（模拟 SelectInstanceOptimizer）
     */
    @Benchmark
    public List<ModelInstance> singleStreamFilter(Blackhole bh) {
        // 单次 Stream 完成所有过滤
        return instances.stream()
                .filter(i -> "test-model".equals(i.getName())
                        && "active".equalsIgnoreCase(i.getStatus())
                        && healthBitSet.isHealthy("CHAT:" + i.getInstanceId(), false))
                .collect(Collectors.toList());
    }

    /**
     * 传统方法：多次 Stream + anyMatch
     */
    @Benchmark
    public boolean multiStreamAnyMatch(Blackhole bh) {
        return instances.stream()
                .filter(i -> "test-model".equals(i.getName()))
                .filter(i -> "active".equalsIgnoreCase(i.getStatus()))
                .anyMatch(i -> healthMap.getOrDefault("CHAT:" + i.getInstanceId(), false));
    }

    /**
     * 优化方法：单次 Stream anyMatch
     */
    @Benchmark
    public boolean singleStreamAnyMatch(Blackhole bh) {
        return instances.stream()
                .anyMatch(i -> "test-model".equals(i.getName())
                        && "active".equalsIgnoreCase(i.getStatus())
                        && healthBitSet.isHealthy("CHAT:" + i.getInstanceId(), false));
    }

    // ==================== 写入性能测试 ====================

    /**
     * 传统方法：Map 写入
     */
    @Benchmark
    public void traditionalHealthWrite(Blackhole bh) {
        int index = ThreadLocalRandom.current().nextInt(instanceCount);
        String key = instanceKeys.get(index);
        healthMap.put(key, ThreadLocalRandom.current().nextBoolean());
    }

    /**
     * 优化方法：BitSet 写入
     */
    @Benchmark
    public void bitSetHealthWrite(Blackhole bh) {
        int index = ThreadLocalRandom.current().nextInt(instanceCount);
        String key = instanceKeys.get(index);
        healthBitSet.setHealth(key, ThreadLocalRandom.current().nextBoolean());
    }
}
