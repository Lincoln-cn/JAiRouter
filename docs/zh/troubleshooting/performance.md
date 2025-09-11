# 性能问题排查

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



本文档提供 JAiRouter 性能问题的诊断方法和优化策略。

## 性能监控指标

### 关键性能指标 (KPI)

| 指标类别 | 指标名称 | 正常范围 | 告警阈值 |
|----------|----------|----------|----------|
| **响应时间** | P95 响应时间 | < 2s | > 5s |
| **吞吐量** | 每秒请求数 (RPS) | > 100 | < 50 |
| **错误率** | 4xx/5xx 错误率 | < 1% | > 5% |
| **资源使用** | CPU 使用率 | < 70% | > 85% |
| **资源使用** | 内存使用率 | < 80% | > 90% |
| **连接** | 活跃连接数 | < 1000 | > 2000 |

### 监控指标获取

```bash
# 获取响应时间指标
curl -s http://localhost:8080/actuator/metrics/jairouter.request.duration | jq

# 获取请求统计
curl -s http://localhost:8080/actuator/metrics/jairouter.requests.total | jq

# 获取 JVM 指标
curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq
curl -s http://localhost:8080/actuator/metrics/jvm.gc.pause | jq

# 获取系统指标
curl -s http://localhost:8080/actuator/metrics/system.cpu.usage | jq
```

## 性能问题分类

### 1. 响应时间过长

#### 症状识别
- API 响应时间超过 5 秒
- 用户反馈系统响应缓慢
- P95 响应时间持续上升

#### 诊断步骤

**1. 确定瓶颈位置**
```bash
# 检查各组件响应时间
curl -w "@curl-format.txt" -o /dev/null -s http://localhost:8080/v1/chat/completions

# curl-format.txt 内容:
#     time_namelookup:  %{time_namelookup}\n
#        time_connect:  %{time_connect}\n
#     time_appconnect:  %{time_appconnect}\n
#    time_pretransfer:  %{time_pretransfer}\n
#       time_redirect:  %{time_redirect}\n
#  time_starttransfer:  %{time_starttransfer}\n
#                     ----------\n
#          time_total:  %{time_total}\n
```

**2. 分析请求链路**
```bash
# 启用请求追踪
java -Dlogging.level.org.unreal.modelrouter=DEBUG \
     -jar target/model-router-*.jar

# 分析日志中的时间戳
grep "Processing request" logs/jairouter-debug.log | tail -10
```

**3. 检查后端服务性能**
```bash
# 直接测试后端服务
time curl -X POST http://backend:9090/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"test","messages":[{"role":"user","content":"hello"}]}'
```

#### 优化策略

**连接池优化**
```yaml
spring:
  webflux:
    httpclient:
      pool:
        max-connections: 200        # 增加连接池大小
        max-idle-time: 60s         # 延长空闲时间
        max-life-time: 300s        # 延长连接生命周期
      connect-timeout: 5s          # 连接超时
      response-timeout: 30s        # 响应超时
```

**负载均衡优化**
```yaml
model:
  services:
    chat:
      load-balance:
        type: least-connections    # 使用最少连接策略
      timeout: 30s                 # 设置合理超时
```

**缓存策略**
```yaml
model:
  cache:
    enabled: true
    ttl: 300s                     # 5分钟缓存
    max-size: 1000               # 最大缓存条目
```

### 2. 吞吐量不足

#### 症状识别
- RPS 低于预期
- 系统无法处理高并发请求
- 请求队列积压

#### 诊断步骤

**1. 压力测试**
```bash
# 使用 Apache Bench 进行压力测试
ab -n 1000 -c 50 -H "Content-Type: application/json" \
   -p request.json http://localhost:8080/v1/chat/completions

# 使用 wrk 进行压力测试
wrk -t12 -c400 -d30s --script=post.lua http://localhost:8080/v1/chat/completions
```

**2. 线程池分析**
```bash
# 获取线程池状态
curl -s http://localhost:8080/actuator/metrics/executor.active | jq
curl -s http://localhost:8080/actuator/metrics/executor.queue.remaining | jq

# 生成线程转储
jstack <pid> > threads.dump
```

**3. 资源使用分析**
```bash
# CPU 使用情况
top -p <pid>

# 内存使用情况
jstat -gc <pid> 5s

# I/O 使用情况
iotop -p <pid>
```

#### 优化策略

**线程池调优**
```yaml
spring:
  task:
    execution:
      pool:
        core-size: 8              # 核心线程数
        max-size: 32              # 最大线程数
        queue-capacity: 200       # 队列容量
        keep-alive: 60s           # 线程保活时间
```

**Reactor 调优**
```yaml
spring:
  webflux:
    netty:
      worker-threads: 16          # 工作线程数
      initial-buffer-size: 128    # 初始缓冲区大小
      max-buffer-size: 1024       # 最大缓冲区大小
```

**异步处理**
```yaml
model:
  async:
    enabled: true
    thread-pool-size: 16
    queue-capacity: 1000
```

### 3. 内存使用过高

#### 症状识别
- JVM 堆内存持续增长
- 频繁的 Full GC
- OutOfMemoryError 异常

#### 诊断步骤

**1. 内存使用分析**
```bash
# 查看内存使用情况
curl -s http://localhost:8080/actuator/metrics/jvm.memory.used | jq
curl -s http://localhost:8080/actuator/metrics/jvm.memory.max | jq

# 查看 GC 情况
curl -s http://localhost:8080/actuator/metrics/jvm.gc.pause | jq
curl -s http://localhost:8080/actuator/metrics/jvm.gc.memory.allocated | jq
```

**2. 堆转储分析**
```bash
# 生成堆转储
jcmd <pid> GC.run_finalization
jmap -dump:format=b,file=heap.hprof <pid>

# 使用 Eclipse MAT 或 VisualVM 分析堆转储
```

**3. 内存泄漏检测**
```bash
# 启用内存泄漏检测
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/tmp/heapdump.hprof \
     -jar target/model-router-*.jar
```

#### 优化策略

**JVM 参数调优**
```bash
# G1GC 配置
java -Xms2g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:G1HeapRegionSize=16m \
     -XX:G1NewSizePercent=20 \
     -XX:G1MaxNewSizePercent=30 \
     -XX:InitiatingHeapOccupancyPercent=45 \
     -jar target/model-router-*.jar
```

**内存管理优化**
```yaml
model:
  memory:
    # 限流器清理
    rate-limiter-cleanup:
      enabled: true
      interval: 5m
      inactive-threshold: 30m
    
    # 缓存管理
    cache:
      max-size: 1000
      expire-after-write: 10m
      expire-after-access: 5m
```

**对象池化**
```yaml
spring:
  webflux:
    httpclient:
      pool:
        # 启用对象池化
        use-object-pooling: true
        pool-size: 100
```

### 4. CPU 使用率过高

#### 症状识别
- CPU 使用率持续超过 85%
- 系统负载过高
- 响应时间增加

#### 诊断步骤

**1. CPU 热点分析**
```bash
# 查看 CPU 使用情况
top -H -p <pid>

# 生成 CPU 性能分析
java -XX:+FlightRecorder \
     -XX:StartFlightRecording=duration=60s,filename=cpu-profile.jfr \
     -jar target/model-router-*.jar

# 分析性能数据
jfr print --events CPULoad cpu-profile.jfr
```

**2. 线程分析**
```bash
# 生成线程转储
jstack <pid> > threads.dump

# 分析高 CPU 线程
top -H -p <pid>  # 找到高 CPU 线程 ID
printf "%x\n" <thread-id>  # 转换为十六进制
grep <hex-thread-id> threads.dump  # 在转储中查找
```

**3. 代码热点分析**
```bash
# 启用编译日志
java -XX:+PrintCompilation \
     -XX:+UnlockDiagnosticVMOptions \
     -XX:+PrintInlining \
     -jar target/model-router-*.jar
```

#### 优化策略

**算法优化**
```java
// 优化负载均衡算法
@Component
public class OptimizedRoundRobinLoadBalancer implements LoadBalancer {
    private final AtomicInteger counter = new AtomicInteger(0);
    
    @Override
    public ServiceInstance selectInstance(List<ServiceInstance> instances, String clientInfo) {
        if (instances.isEmpty()) {
            return null;
        }
        
        // 使用位运算优化取模操作
        int index = counter.getAndIncrement() & (instances.size() - 1);
        return instances.get(index);
    }
}
```

**并发优化**
```yaml
model:
  concurrency:
    # 限制并发处理数
    max-concurrent-requests: 1000
    
    # 使用无锁数据结构
    lock-free-structures: true
    
    # 批处理优化
    batch-processing:
      enabled: true
      batch-size: 100
      timeout: 100ms
```

**缓存优化**
```java
// 使用 Caffeine 高性能缓存
@Configuration
public class CacheConfiguration {
    
    @Bean
    public Cache<String, Object> responseCache() {
        return Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .recordStats()
            .build();
    }
}
```

## 性能调优最佳实践

### 1. JVM 调优

#### 堆内存配置
```bash
# 生产环境推荐配置
java -Xms4g -Xmx4g \                    # 固定堆大小，避免动态调整
     -XX:+UseG1GC \                     # 使用 G1 垃圾收集器
     -XX:MaxGCPauseMillis=100 \         # 最大 GC 暂停时间
     -XX:G1HeapRegionSize=32m \         # G1 区域大小
     -XX:G1NewSizePercent=20 \          # 新生代比例
     -XX:G1MaxNewSizePercent=30 \       # 新生代最大比例
     -XX:InitiatingHeapOccupancyPercent=45 \  # GC 触发阈值
     -XX:+UnlockExperimentalVMOptions \ # 启用实验性选项
     -XX:+UseStringDeduplication \      # 字符串去重
     -jar target/model-router-*.jar
```

#### GC 日志配置
```bash
# GC 日志配置
-Xlog:gc*:gc.log:time,tags \
-XX:+UseGCLogFileRotation \
-XX:NumberOfGCLogFiles=5 \
-XX:GCLogFileSize=10M
```

### 2. 应用层调优

#### 连接池配置
```yaml
spring:
  webflux:
    httpclient:
      pool:
        max-connections: 500           # 根据后端服务数量调整
        max-idle-time: 30s            # 空闲连接保持时间
        max-life-time: 300s           # 连接最大生命周期
        pending-acquire-timeout: 10s  # 获取连接超时
        pending-acquire-max-count: 1000  # 等待队列大小
```

#### 响应式配置
```yaml
spring:
  webflux:
    netty:
      worker-threads: 16              # 工作线程数 = CPU 核数 * 2
      initial-buffer-size: 128        # 初始缓冲区
      max-buffer-size: 1024          # 最大缓冲区
      connection-timeout: 5s          # 连接超时
```

### 3. 监控和告警

#### 性能监控配置
```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
        step: 30s                     # 指标收集间隔
    distribution:
      percentiles-histogram:
        http.server.requests: true    # 启用响应时间直方图
      percentiles:
        http.server.requests: 0.5,0.95,0.99  # 百分位数
```

#### 告警规则
```yaml
# prometheus-alerts.yml
groups:
  - name: jairouter.performance
    rules:
      - alert: HighResponseTime
        expr: histogram_quantile(0.95, jairouter_request_duration_seconds_bucket) > 5
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "JAiRouter 响应时间过高"
          description: "P95 响应时间: {{ $value }}s"
      
      - alert: HighCPUUsage
        expr: system_cpu_usage > 0.85
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "JAiRouter CPU 使用率过高"
          description: "CPU 使用率: {{ $value | humanizePercentage }}"
      
      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes / jvm_memory_max_bytes > 0.9
        for: 3m
        labels:
          severity: critical
        annotations:
          summary: "JAiRouter 内存使用率过高"
          description: "内存使用率: {{ $value | humanizePercentage }}"
```

### 4. 容量规划

#### 性能基准测试
```bash
#!/bin/bash
# performance-benchmark.sh

echo "=== JAiRouter 性能基准测试 ==="

# 预热
echo "预热阶段..."
ab -n 100 -c 10 http://localhost:8080/v1/chat/completions

# 基准测试
echo "基准测试..."
ab -n 1000 -c 50 -g benchmark.dat http://localhost:8080/v1/chat/completions

# 压力测试
echo "压力测试..."
ab -n 5000 -c 200 -g stress.dat http://localhost:8080/v1/chat/completions

# 生成报告
echo "生成性能报告..."
gnuplot -e "
set terminal png;
set output 'performance-report.png';
set title 'JAiRouter Performance Test';
set xlabel 'Request Number';
set ylabel 'Response Time (ms)';
plot 'benchmark.dat' using 9 with lines title 'Benchmark',
     'stress.dat' using 9 with lines title 'Stress Test'
"

echo "=== 性能测试完成 ==="
```

#### 容量评估
```bash
# 计算理论最大 RPS
# RPS = 1000ms / 平均响应时间(ms) * 并发连接数

# 示例计算:
# 平均响应时间: 100ms
# 并发连接数: 200
# 理论最大 RPS = 1000 / 100 * 200 = 2000

# 考虑安全系数 (70%)
# 实际建议 RPS = 2000 * 0.7 = 1400
```

## 性能优化检查清单

### 部署前检查
- [ ] JVM 参数已优化
- [ ] 连接池配置合理
- [ ] 缓存策略已配置
- [ ] 监控指标已启用
- [ ] 告警规则已设置

### 运行时监控
- [ ] 响应时间在正常范围
- [ ] CPU 使用率 < 80%
- [ ] 内存使用率 < 85%
- [ ] GC 暂停时间 < 200ms
- [ ] 错误率 < 1%

### 定期优化
- [ ] 分析性能趋势
- [ ] 识别性能瓶颈
- [ ] 调整配置参数
- [ ] 更新优化策略
- [ ] 验证优化效果

## 性能测试工具

### 1. Apache Bench (ab)
```bash
# 基本测试
ab -n 1000 -c 50 http://localhost:8080/v1/chat/completions

# POST 请求测试
ab -n 1000 -c 50 -p request.json -T application/json http://localhost:8080/v1/chat/completions
```

### 2. wrk
```bash
# 安装 wrk
# Ubuntu: sudo apt-get install wrk
# macOS: brew install wrk

# 基本测试
wrk -t12 -c400 -d30s http://localhost:8080/v1/chat/completions

# 使用脚本
wrk -t12 -c400 -d30s --script=post.lua http://localhost:8080/v1/chat/completions
```

### 3. JMeter
```xml
<!-- JMeter 测试计划示例 -->
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2">
  <hashTree>
    <TestPlan testname="JAiRouter Performance Test">
      <elementProp name="TestPlan.arguments" elementType="Arguments" guiclass="ArgumentsPanel">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
      <stringProp name="TestPlan.user_define_classpath"></stringProp>
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
      <boolProp name="TestPlan.functional_mode">false</boolProp>
    </TestPlan>
  </hashTree>
</jmeterTestPlan>
```

通过遵循这些性能优化指南，可以确保 JAiRouter 在生产环境中提供稳定、高效的服务。