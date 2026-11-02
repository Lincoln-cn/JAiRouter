# 性能问题排查

<!-- 版本信息 -->
> **文档版本**: 1.0.3  
> **最后更新**: 2026-09-15  
> **Git 提交**: 61384b4a  
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

🔑 **这些运维端点需要管理员凭据**：下列 `/actuator/**` 调用走**正常认证链** —— 只有 `/actuator/health`（含
> `liveness` / `readiness` 子路径）、`/actuator/info`、`/actuator/prometheus` 允许匿名（供 k8s 探针与
> Prometheus 抓取），其余都需要 ADMIN 角色。**不带凭据会得到 `401 AUTH_MISSING`。**
> （修复前即使带了 ADMIN JWT 也会 401：认证链被整段排除，与 `hasRole("ADMIN")` 互相抵消，见 issue #340。）
>
> 取 JWT 与调用示例（本机实测通过）：
>
> ```bash
> TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/jwt/login \
>   -H 'Content-Type: application/json' \
>   -d '{"username":"admin","password":"<你的管理员密码>"}' | jq -r .data.token)
> curl -s -H "X-Jairouter-Token: $TOKEN" http://localhost:8080/actuator/metrics | jq
> ```

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

## 容量饱和面指标

排障时最先要回答的是「慢在哪一层」。下面这些指标就是为了把这个问题从猜变成看 ——
它们都在 `/actuator/prometheus` 里直接可读，不需要额外配置。

| 指标 | 含义 | 饱和时的表现 |
| --- | --- | --- |
| `jairouter.outbound.requests.inflight` | 已发出、尚未收到响应头的出站请求数 | 持续高位 ⇒ 上游慢或连接池不够 |
| `jairouter.outbound.request.duration` | 出站到响应头到达的耗时 | p99 上升但 inflight 不高 ⇒ 上游抖动 |
| `reactor.netty.connection.provider.pending.connections{name="jairouter-outbound"}` | 等待出站连接的请求数 | 大于 0 且持续增长 ⇒ 连接池排队 |
| `reactor.netty.connection.provider.active.connections{name="jairouter-outbound"}` | 已占用的出站连接数 | 贴着 `max-connections` ⇒ 到上限了 |
| `hikaricp_connections_pending` | 等待数据库连接的线程数 | 大于 0 ⇒ 数据库池排队（见 [数据库部署](../deployment/database.md)） |
| `jairouter.sse.active.connections{endpoint}` | 当前打开的 SSE 连接数 | 逼近运维上限 ⇒ 长连接堆积 |
| `jairouter.ws.active.sessions{endpoint}` | 当前打开的 WebSocket 会话数（监控推送） | 只增不减 ⇒ 客户端异常断开或计数未释放 |
| `jairouter.cache.response.size` | 响应缓存条目数（估算值） | 贴着 `maxSize` ⇒ 命中率开始掉 |
| `jairouter.eventloop.delay` | 事件循环「投递到执行」的耗时（每秒采样一次） | p99 抬升而 CPU 不高 ⇒ 事件循环被同步调用或排队压住 |

判读顺序建议：先看 `hikaricp_connections_pending` 与
`reactor.netty.connection.provider.pending.connections` 这两个「排队」指标 ——
它们都不为 0 才说明瓶颈在池；都为 0 而 `inflight` 高，瓶颈在上游；
三者都不高但延迟涨，看 `jairouter.eventloop.delay` 的 p99 与 GC 停顿。

`jairouter.sse.active.connections` 的 `endpoint` 标签取值：
`health-status`（健康巡检）、`v1-chat-completions`、`chat-completions`（legacy 面）、
`anthropic-messages`。

`jairouter.ws.active.sessions` 的 `endpoint` 标签取值：`circuit-breaker-monitor`、`routing-monitor`。

## 数据库换成 PostgreSQL 时的实测（单机 Docker）

上面的基线是 H2 文件库。换成 PostgreSQL 后数字会明显变化，这一节记录原因 —— 省得有人拿 H2 的数去规划 PG 部署。

### 怎么测的

- PostgreSQL 16.15 跑在本机 Docker（`-p 55432:5432`），网关与压测器在同一台机器上。
- 必须先关三层限流：**限流开关会被持久化成配置落库，之后环境变量覆盖不生效**，所以要在 yml 里改完三处 `enabled: false`，再用一个全新的库名启动。
- dev profile 的 DEBUG 日志同样要切到 WARN，否则测的是日志而不是容量。
- 机型 8 逻辑核 / 16 GB / i7-8550U / Windows / JDK 17，mock 上游固定 20 ms。

### 结果

连接池 10（默认）：

| 并发 | RPS | p50 | p95 | p99 | 错误率 |
| --- | --- | --- | --- | --- | --- |
| 20 | 61.8 | 247.6 ms | 873.4 ms | 1222.6 ms | 0% |
| 50 | 94.4 | 469.9 ms | 1157.7 ms | 1571.3 ms | 0% |
| 100 | 95.7 | 967.0 ms | 1898.6 ms | 2411.3 ms | 0% |

连接池 32（把 `hikari.maximum-pool-size` 临时改大）：

| 并发 | RPS | p50 | p95 | p99 | 错误率 |
| --- | --- | --- | --- | --- | --- |
| 20 | 79.0 | 161.3 ms | 811.4 ms | 1113.5 ms | 0% |
| 50 | 86.5 | 501.1 ms | 1335.4 ms | 1877.2 ms | 0% |
| 100 | 93.0 | 979.8 ms | 1975.7 ms | 2518.1 ms | 0% |

对照 H2：并发 20 / 50 / 100 分别是 370.3 / 388.5 / 368.6 RPS。

### 三条结论

1. **连接池仍然不是瓶颈。** 池从 10 加到 32，三档 RPS 都在 60–96 之间，p99 反而更差。指标上确实看到 `hikaricp_connections_active` 打满、`hikaricp_connections_pending` 峰值 77，但加大池子只是把排队位置从连接池挪到了别处。
2. **热点在数据库容器本身。** 压测期间 JVM 的 `process_cpu_usage` 只有 0.18 核，而 PG 容器 `docker stats` 是 127% CPU。每个请求要写 token 用量、调用历史、配额账本，这些写入在这台机器的 Docker 里先饱和。
3. **这组数字不代表生产 PostgreSQL 的表现。** 压测器、网关、Docker 里的 PG 共享同一 8 核，CPU 是三者共同的瓶颈。它只能说明「这种部署形态下的上界」。要拿可用于容量规划的 PG 基线，得把压测器和数据库放到别的机器上跑。

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

## 本仓的测量方法（可复现）

> 上面的 KPI 表是**目标**，不是实测值。这一节给出仓库自带的测量手段与一次基线记录，任何维护者都能复现。

### 为什么要自备脚本

压测工具（k6 / wrk / jmeter）不属于本仓依赖。为了让「怎么测」不依赖个人环境，`scripts/perf/` 提供两个零依赖的 Node 脚本：

- `scripts/perf/mock-upstream.mjs`：OpenAI 兼容的最小上游（`/v1/models` 探活、非流式 JSON、流式 SSE），把**上游模型延迟**从结果里剔除；
- `scripts/perf/loadtest.mjs`：固定并发 + 固定时长的压测器，输出 RPS、p50/p95/p99 与失败样例。

### 前置：先把「护栏」关掉

默认配置里三层限流会先把请求挡回去，测不到网关本身的吞吐：

| 层级 | 配置键 | 压测时的环境变量 |
| --- | --- | --- |
| 全局 | `model.rate-limit.enabled` | `MODEL_RATELIMIT_ENABLED=false` |
| 服务级 | `model.services.chat.rate-limit.enabled` | `MODEL_SERVICES_CHAT_RATELIMIT_ENABLED=false` |
| 实例级 | `model.services.chat.instances[0].rate-limit.enabled` | `MODEL_SERVICES_CHAT_INSTANCES_0_RATELIMIT_ENABLED=false` |

**这几个开关本身是产品功能**，压测关掉它们只是为了测「无护栏时的上限」；要测「限流生效时客户端看到什么」，保留默认值即可。

建议同时用独立数据库，避免污染本地开发库：

```bash
DATABASE_URL='jdbc:h2:file:./data/perf;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE'
```

### 命令

```bash
# 1) 上游 mock（默认 127.0.0.1:18080，20ms 延迟）
node scripts/perf/mock-upstream.mjs

# 2) 网关（dev profile，指向 mock，三层限流关闭）
MODEL_UPSTREAM_BASE_URL=http://127.0.0.1:18080 \
MODEL_RATELIMIT_ENABLED=false \
MODEL_SERVICES_CHAT_RATELIMIT_ENABLED=false \
MODEL_SERVICES_CHAT_INSTANCES_0_RATELIMIT_ENABLED=false \
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3) 压测（model 用 services.yml 里的实例名）
node scripts/perf/loadtest.mjs --url http://127.0.0.1:8080/v1/chat/completions \
  --api-key sk-test-api-key-12345678 --model "qwen3:4b" --concurrency 50 --duration 20
```

### 一次基线记录（2026-11-01）

环境：8 逻辑核 / 16 GB / i7-8550U（笔记本）、Windows、JDK 17、H2 文件库、dev profile、mock 上游 20 ms、压测器与网关**同机**。

> ⚠️ **压测前必须先把业务 DEBUG 日志关掉。** `application-dev.yml` 把 `org.unreal.modelrouter` 设为 `DEBUG`，压测时每个请求会打数十行日志（实测每请求约 200 行），吞吐被日志 I/O 压到原来的三分之一左右。用 Actuator 动态切换即可：
>
> ```bash
> curl -X POST http://127.0.0.1:8080/actuator/loggers/org.unreal.modelrouter \
>   -H 'Content-Type: application/json' -H "X-Jairouter-Token: $TOKEN" \
>   -d '{"configuredLevel":"WARN"}'
> ```
>
> 下表的数字**只在关闭 DEBUG 后成立**；不关 DEBUG 得到的基线（并发 20 只有 111 RPS）是被日志污染的假象，不要拿去比对容量。

| 并发 | RPS | p50 | p95 | p99 | 错误率 |
| --- | --- | --- | --- | --- | --- |
| 20 | 370.3 | 43 ms | 109 ms | 286 ms | 0% |
| 50 | 388.5 | 64 ms | 450 ms | 837 ms | 0% |
| 100 | 368.6 | 120 ms | 867 ms | 2552 ms | 0% |

### 流式（SSE）基线（2026-11-02）

同一台机器、同样关闭 DEBUG，并且**三层限流全部关闭**、`model.circuit-breaker.enabled=false`：

| 并发 | RPS | p50 | p95 | p99 | 错误率 |
| --- | --- | --- | --- | --- | --- |
| 10 | 111.4 | 91.8 ms | 109.2 ms | 123.9 ms | 0% |
| 50 | 599.7 | 52.2 ms | 225.2 ms | 377.5 ms | 0% |
| 100 | 465.3 | 196.4 ms | 506.3 ms | 733.4 ms | 0% |

- 流式的并发吞吐比非流式高：服务端在这条链路上不做完整响应体的聚合与组装，chunk 到达即转发，连接被复用的窗口更长。
- 并发 10 的 RPS 反而低于 50：每个流式请求的持续时间由上游分块节奏决定（mock 为固定分块间隔），并发太低时吃不满一条连接的带宽。
- 并发 20 那一档在本轮出现过一次「压测器挂住不返回」的偶发（同参数重跑未复现），未计入上表。

**测流式之前必须先做两件事**，否则测出来的是限流与熔断的数字，不是网关容量：

1. 关掉三层限流（全局 `model.rate-limit`、服务级 `model.services.chat.rate-limit`、实例级 `model.services.chat.instances[0].rate-limit`）—— 只关前两层时，并发 10 就有 95% 的请求返回 429；
2. 关掉熔断（`model.circuit-breaker.enabled=false`）—— 高并发下失败率一上来，实例熔断器就会打开并**持久化状态**，后续请求全部 503，重启也不清。

> ⚠️ **不要用环境变量去关实例级限流。** `MODEL_SERVICES_CHAT_INSTANCES_0_RATELIMIT_ENABLED` 这类变量会把整个 `instances` 列表替换成「只含该属性的一个元素」，实例的 `name` / `base-url` / `path` 全部变空；**空库首次启动时这会让初始化直接失败**（`NULL not allowed for column "base_url"`）。要改就改 yml，或走配置 API。

### 怎么读这些数字

- 真实饱和点在 **约 370–390 RPS**（本机，压测器与网关同机）。并发从 20 加到 100，吞吐基本持平，p50 从 43 ms 涨到 120 ms、p99 涨到 2.5 s —— 依旧是**排队**，不是吞吐增长。
- **数据库连接池不是这个瓶颈**：把 `hikaricp.maximum-pool-size` 从默认 10 调到 32，吞吐没有提升（384.7 → 368.6 RPS），p99 反而从 1.2 s 涨到 2.6 s。池 10 时 `hikaricp_connections_pending` 峰值 72（写库并发确实高），但加池只是把排队挪个地方。
- 事件循环没有被阻塞：压测期间连抓三份线程栈，`reactor-http-nio-*` 全部停在 `Selector.select`、`boundedElastic-*` 全部 parked ⇒ 瓶颈不在「事件循环上做同步调用」。
- 单请求的固定开销（JSON 序列化、指标记录、每请求 3 次写库：token 用量 / 调用历史 / 配额账本）是目前最可疑的一段；要再往下定位，需要在 `process_cpu_usage` 打到 5–6 核的前提下做 CPU 采样（async-profiler 之类）。
- 这些数字**不代表生产容量**：mock 上游、单机压测（压测器与网关抢同一个 CPU）、H2 文件库，都会让结果偏离真实部署。

### 已知限制

- **流式（SSE）场景**已能跑通，但前提是把三层限流与熔断器都关掉（见上一节的流式基线）；开着熔断测流式，得到的是「熔断器什么时候打开」。
- 熔断器一旦打开，用 `/api/config/instance/circuit-breaker/reset` 重置时**需要传 `instanceId`**（不是实例名），而该值在实例列表里未必与 `id` 一致，目前容易弄错。
