# 调试指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



本文档提供 JAiRouter 的调试技巧、工具使用和问题定位方法。

## 调试环境配置

### 开发环境调试

#### 启用调试模式
```yaml
# application-dev.yml
logging:
  level:
    org.unreal.modelrouter: DEBUG
    org.springframework.web: DEBUG
    reactor.netty: DEBUG
    
debug: true

spring:
  profiles:
    active: dev
```

#### IDE 调试配置

**IntelliJ IDEA**
```
Run/Debug Configurations:
- Main class: org.unreal.modelrouter.ModelRouterApplication
- VM options: -Dspring.profiles.active=dev -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005
- Program arguments: --debug
- Environment variables: SPRING_PROFILES_ACTIVE=dev
```

**VS Code**
```json
{
  "type": "java",
  "name": "Debug JAiRouter",
  "request": "launch",
  "mainClass": "org.unreal.modelrouter.ModelRouterApplication",
  "projectName": "model-router",
  "args": "--debug",
  "vmArgs": "-Dspring.profiles.active=dev",
  "env": {
    "SPRING_PROFILES_ACTIVE": "dev"
  }
}
```

### 远程调试

#### 启用远程调试
```bash
# 启动应用时添加调试参数
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
     -jar target/model-router-*.jar
```

#### Docker 远程调试
```yaml
# docker-compose-debug.yml
version: '3.8'
services:
  jairouter:
    image: jairouter/model-router:latest
    ports:
      - "8080:8080"
      - "5005:5005"  # 调试端口
    environment:
      - JAVA_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
      - SPRING_PROFILES_ACTIVE=dev
```

## 日志调试

### 日志级别配置

#### 全局日志配置
```yaml
# application.yml
logging:
  level:
    root: INFO
    org.unreal.modelrouter: DEBUG
    org.springframework: INFO
    reactor: DEBUG
    
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
    
  file:
    name: logs/jairouter-debug.log
    max-size: 100MB
    max-history: 30
```

#### 动态调整日志级别
```bash
# 运行时调整日志级别
curl -X POST http://localhost:8080/actuator/loggers/org.unreal.modelrouter \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'

# 查看当前日志级别
curl http://localhost:8080/actuator/loggers/org.unreal.modelrouter
```

### 结构化日志

#### 添加追踪 ID
```java
@Component
public class TraceIdFilter implements WebFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        
        return chain.filter(exchange)
            .contextWrite(Context.of("traceId", traceId))
            .doOnEach(ReactiveUtils.addToMDC("traceId", traceId));
    }
}
```

#### 关键点日志记录
```java
@Service
public class LoadBalancerService {
    private static final Logger log = LoggerFactory.getLogger(LoadBalancerService.class);
    
    public ServiceInstance selectInstance(String serviceType, String clientInfo) {
        log.debug("Selecting instance for service: {}, client: {}", serviceType, clientInfo);
        
        List<ServiceInstance> instances = getAvailableInstances(serviceType);
        log.debug("Available instances: {}", instances.size());
        
        ServiceInstance selected = loadBalancer.selectInstance(instances, clientInfo);
        log.info("Selected instance: {} for service: {}", 
                selected != null ? selected.getName() : "null", serviceType);
        
        return selected;
    }
}
```

### 日志分析工具

#### 实时日志监控
```bash
# 实时查看日志
tail -f logs/jairouter-debug.log

# 过滤特定内容
tail -f logs/jairouter-debug.log | grep -i "error\|exception"

# 统计错误数量
grep -c "ERROR" logs/jairouter-debug.log

# 查看最近的错误
grep "ERROR" logs/jairouter-debug.log | tail -10
```

#### 日志分析脚本
```bash
#!/bin/bash
# log-analyzer.sh

LOG_FILE="logs/jairouter-debug.log"
REPORT_FILE="log-analysis-report.txt"

echo "=== JAiRouter 日志分析报告 ===" > $REPORT_FILE
echo "分析时间: $(date)" >> $REPORT_FILE
echo "" >> $REPORT_FILE

# 错误统计
echo "=== 错误统计 ===" >> $REPORT_FILE
echo "ERROR 数量: $(grep -c 'ERROR' $LOG_FILE)" >> $REPORT_FILE
echo "WARN 数量: $(grep -c 'WARN' $LOG_FILE)" >> $REPORT_FILE
echo "" >> $REPORT_FILE

# 最频繁的错误
echo "=== 最频繁的错误 ===" >> $REPORT_FILE
grep "ERROR" $LOG_FILE | cut -d'-' -f4- | sort | uniq -c | sort -nr | head -5 >> $REPORT_FILE
echo "" >> $REPORT_FILE

# 性能相关日志
echo "=== 性能相关 ===" >> $REPORT_FILE
grep -i "timeout\|slow\|performance" $LOG_FILE | tail -5 >> $REPORT_FILE
echo "" >> $REPORT_FILE

# 连接问题
echo "=== 连接问题 ===" >> $REPORT_FILE
grep -i "connection\|refused\|unreachable" $LOG_FILE | tail -5 >> $REPORT_FILE

echo "日志分析完成，报告保存到: $REPORT_FILE"
```

## 网络调试

### HTTP 请求调试

#### 使用 curl 调试
```bash
# 详细输出模式
curl -v -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"test","messages":[{"role":"user","content":"hello"}]}'

# 显示响应头
curl -I http://localhost:8080/actuator/health

# 测试连接时间
curl -w "@curl-format.txt" -o /dev/null -s http://localhost:8080/v1/chat/completions

# curl-format.txt
#      time_namelookup:  %{time_namelookup}s\n
#         time_connect:  %{time_connect}s\n
#      time_appconnect:  %{time_appconnect}s\n
#     time_pretransfer:  %{time_pretransfer}s\n
#        time_redirect:  %{time_redirect}s\n
#   time_starttransfer:  %{time_starttransfer}s\n
#                      ----------\n
#           time_total:  %{time_total}s\n
```

#### 使用 httpie 调试
```bash
# 安装 httpie
pip install httpie

# 发送请求
http POST localhost:8080/v1/chat/completions \
  Content-Type:application/json \
  model=test \
  messages:='[{"role":"user","content":"hello"}]'

# 显示详细信息
http --print=HhBb POST localhost:8080/v1/chat/completions \
  Content-Type:application/json \
  model=test \
  messages:='[{"role":"user","content":"hello"}]'
```

### 网络连接调试

#### 端口和连接检查
```bash
# 检查端口监听
netstat -tlnp | grep :8080

# 检查连接状态
ss -tuln | grep :8080

# 测试端口连通性
telnet localhost 8080

# 检查防火墙
# Ubuntu/Debian
sudo ufw status

# CentOS/RHEL
sudo firewall-cmd --list-all
```

#### 网络抓包分析
```bash
# 使用 tcpdump 抓包
sudo tcpdump -i any -w jairouter.pcap port 8080

# 使用 wireshark 分析
wireshark jairouter.pcap

# 分析 HTTP 流量
sudo tcpdump -i any -A -s 0 port 8080
```

## JVM 调试

### 内存调试

#### 堆内存分析
```bash
# 查看堆内存使用
jstat -gc <pid>

# 生成堆转储
jmap -dump:format=b,file=heap.hprof <pid>

# 分析堆转储
jhat heap.hprof
# 或使用 Eclipse MAT

# 查看对象统计
jmap -histo <pid> | head -20
```

#### 内存泄漏检测
```bash
# 启用内存泄漏检测
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/tmp/heapdump.hprof \
     -XX:+PrintGCDetails \
     -XX:+PrintGCTimeStamps \
     -Xloggc:gc.log \
     -jar target/model-router-*.jar

# 分析 GC 日志
# 使用 GCViewer 或在线工具分析 gc.log
```

### 线程调试

#### 线程转储分析
```bash
# 生成线程转储
jstack <pid> > threads.dump

# 或使用 jcmd
jcmd <pid> Thread.print > threads.dump

# 分析死锁
grep -A 5 -B 5 "deadlock" threads.dump

# 分析线程状态
grep "java.lang.Thread.State" threads.dump | sort | uniq -c
```

#### 线程监控
```java
@Component
public class ThreadMonitor {
    private static final Logger log = LoggerFactory.getLogger(ThreadMonitor.class);
    
    @Scheduled(fixedRate = 60000) // 每分钟检查一次
    public void monitorThreads() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        
        long[] deadlockedThreads = threadBean.findDeadlockedThreads();
        if (deadlockedThreads != null) {
            log.error("检测到死锁线程: {}", Arrays.toString(deadlockedThreads));
        }
        
        int threadCount = threadBean.getThreadCount();
        int peakThreadCount = threadBean.getPeakThreadCount();
        
        log.debug("当前线程数: {}, 峰值线程数: {}", threadCount, peakThreadCount);
        
        if (threadCount > 200) {
            log.warn("线程数过多: {}", threadCount);
        }
    }
}
```

### 性能调试

#### CPU 性能分析
```bash
# 使用 Java Flight Recorder
java -XX:+FlightRecorder \
     -XX:StartFlightRecording=duration=60s,filename=profile.jfr \
     -jar target/model-router-*.jar

# 分析 JFR 文件
jfr print --events CPULoad profile.jfr
jfr print --events JavaMonitorEnter profile.jfr

# 使用 async-profiler
java -jar async-profiler.jar -d 60 -f profile.html <pid>
```

#### 方法级性能分析
```java
@Component
public class PerformanceMonitor {
    private static final Logger log = LoggerFactory.getLogger(PerformanceMonitor.class);
    
    @Around("@annotation(Monitored)")
    public Object monitor(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            if (duration > 1000) {
                log.warn("方法 {} 执行时间过长: {}ms", methodName, duration);
            } else {
                log.debug("方法 {} 执行时间: {}ms", methodName, duration);
            }
            
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("方法 {} 执行失败，耗时: {}ms", methodName, duration, e);
            throw e;
        }
    }
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Monitored {
}
```

## 响应式调试

### Reactor 调试

#### 启用调试模式
```java
@SpringBootApplication
public class ModelRouterApplication {
    
    public static void main(String[] args) {
        // 启用 Reactor 调试
        Hooks.onOperatorDebug();
        
        SpringApplication.run(ModelRouterApplication.class, args);
    }
}
```

#### 响应式流调试
```java
@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    
    public Mono<String> processChat(String request) {
        return Mono.fromCallable(() -> request)
            .doOnSubscribe(s -> log.debug("开始处理聊天请求"))
            .doOnNext(req -> log.debug("处理请求: {}", req))
            .flatMap(this::callBackend)
            .doOnSuccess(response -> log.debug("后端响应: {}", response))
            .doOnError(error -> log.error("处理失败", error))
            .doFinally(signal -> log.debug("处理完成: {}", signal));
    }
    
    private Mono<String> callBackend(String request) {
        return webClient.post()
            .uri("/v1/chat/completions")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(30))
            .onErrorMap(TimeoutException.class, 
                ex -> new ServiceException("后端服务超时", ex));
    }
}
```

#### 背压调试
```java
@Component
public class BackpressureMonitor {
    private static final Logger log = LoggerFactory.getLogger(BackpressureMonitor.class);
    
    public Flux<String> processStream(Flux<String> input) {
        return input
            .onBackpressureBuffer(1000, 
                dropped -> log.warn("背压缓冲区满，丢弃元素: {}", dropped))
            .doOnRequest(n -> log.debug("请求 {} 个元素", n))
            .doOnCancel(() -> log.debug("流被取消"))
            .map(this::processItem);
    }
}
```

## 配置调试

### 配置验证

#### 配置属性检查
```bash
# 查看所有配置属性
curl http://localhost:8080/actuator/configprops | jq

# 查看环境变量
curl http://localhost:8080/actuator/env | jq

# 查看特定配置
curl http://localhost:8080/actuator/env/model.services | jq
```

#### 配置绑定调试
```java
@ConfigurationProperties(prefix = "model")
@Validated
@Data
public class ModelConfiguration {
    
    @PostConstruct
    public void validate() {
        log.info("模型配置加载完成: {}", this);
        
        if (services == null || services.isEmpty()) {
            throw new IllegalStateException("至少需要配置一个服务");
        }
        
        services.forEach((type, config) -> {
            if (config.getInstances() == null || config.getInstances().isEmpty()) {
                throw new IllegalStateException("服务 " + type + " 至少需要一个实例");
            }
        });
    }
}
```

### 动态配置调试

#### 配置更新追踪
```java
@Component
public class ConfigurationChangeListener {
    private static final Logger log = LoggerFactory.getLogger(ConfigurationChangeListener.class);
    
    @EventListener
    public void handleConfigurationChange(ConfigurationChangeEvent event) {
        log.info("配置变更: 类型={}, 键={}, 旧值={}, 新值={}", 
                event.getType(), event.getKey(), event.getOldValue(), event.getNewValue());
    }
}
```

## 调试工具集成

### Spring Boot Actuator

#### 启用调试端点
```yaml
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always
    env:
      show-values: always
```

#### 自定义健康检查
```java
@Component
public class BackendHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        try {
            // 检查后端服务健康状态
            boolean isHealthy = checkBackendHealth();
            
            if (isHealthy) {
                return Health.up()
                    .withDetail("backend", "All services are healthy")
                    .build();
            } else {
                return Health.down()
                    .withDetail("backend", "Some services are unhealthy")
                    .build();
            }
        } catch (Exception e) {
            return Health.down(e)
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

### 外部调试工具

#### Zipkin 链路追踪
```yaml
# 添加依赖
spring:
  zipkin:
    base-url: http://zipkin:9411
  sleuth:
    sampler:
      probability: 1.0  # 开发环境全采样
```

#### Prometheus 监控
```java
@Component
public class CustomMetrics {
    private final Counter requestCounter;
    private final Timer requestTimer;
    
    public CustomMetrics(MeterRegistry meterRegistry) {
        this.requestCounter = Counter.builder("jairouter.requests.custom")
            .description("自定义请求计数器")
            .register(meterRegistry);
            
        this.requestTimer = Timer.builder("jairouter.request.duration.custom")
            .description("自定义请求耗时")
            .register(meterRegistry);
    }
    
    public void recordRequest(String serviceType, Duration duration) {
        requestCounter.increment(Tags.of("service", serviceType));
        requestTimer.record(duration, Tags.of("service", serviceType));
    }
}
```

## 调试最佳实践

### 1. 分层调试策略

#### 网络层调试
- 使用网络抓包工具分析请求/响应
- 检查 DNS 解析和连接建立
- 验证 SSL/TLS 握手过程

#### 应用层调试
- 添加关键路径的日志记录
- 使用断点调试业务逻辑
- 监控方法执行时间

#### 数据层调试
- 检查配置数据的加载和解析
- 验证数据转换和序列化
- 监控缓存命中率

### 2. 问题定位流程

#### 快速定位
1. 检查应用健康状态
2. 查看最近的错误日志
3. 验证配置是否正确
4. 测试网络连通性

#### 深入分析
1. 启用详细日志记录
2. 使用性能分析工具
3. 分析内存和线程状态
4. 进行压力测试验证

### 3. 调试环境管理

#### 开发环境
```yaml
# application-dev.yml
logging:
  level:
    org.unreal.modelrouter: DEBUG
    
management:
  endpoints:
    web:
      exposure:
        include: "*"
        
spring:
  webflux:
    netty:
      access-log: true
```

#### 测试环境
```yaml
# application-test.yml
logging:
  level:
    org.unreal.modelrouter: INFO
    
management:
  endpoints:
    web:
      exposure:
        include: "health,info,metrics"
```

#### 生产环境
```yaml
# application-prod.yml
logging:
  level:
    org.unreal.modelrouter: WARN
    
management:
  endpoints:
    web:
      exposure:
        include: "health,info"
```

### 4. 调试工具箱

#### 必备工具
- **curl/httpie**: HTTP 请求测试
- **jstack/jmap**: JVM 调试
- **tcpdump/wireshark**: 网络分析
- **ab/wrk**: 性能测试

#### 推荐工具
- **VisualVM**: JVM 性能分析
- **Eclipse MAT**: 内存分析
- **async-profiler**: CPU 性能分析
- **Arthas**: 在线诊断工具

通过掌握这些调试技巧和工具，可以快速定位和解决 JAiRouter 运行中遇到的各种问题。