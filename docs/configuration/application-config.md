# 应用配置

本文档详细介绍 JAiRouter 的基础应用配置，包括服务器配置、WebClient 配置、监控配置等。

## 配置文件位置

- **主配置文件**：`src/main/resources/application.yml`
- **环境配置**：`application-{profile}.yml`
- **外部配置**：`config/application.yml`（可选）

## 服务器配置

### 基本服务器配置

```yaml
server:
  port: 8080                    # 服务端口
  servlet:
    context-path: /             # 应用上下文路径
  compression:
    enabled: true               # 启用响应压缩
    mime-types: application/json,text/plain,text/html
  http2:
    enabled: true               # 启用 HTTP/2
```

### 高级服务器配置

```yaml
server:
  tomcat:
    threads:
      max: 200                  # 最大线程数
      min-spare: 10            # 最小空闲线程数
    connection-timeout: 20000   # 连接超时（毫秒）
    max-connections: 8192       # 最大连接数
    accept-count: 100          # 等待队列长度
  netty:
    connection-timeout: 45s     # Netty 连接超时
    h2c-max-content-length: 0   # H2C 最大内容长度
```

## WebClient 配置

JAiRouter 使用 WebClient 进行后端服务调用，支持详细的连接配置：

```yaml
webclient:
  connection-timeout: 10s       # 连接超时
  read-timeout: 30s            # 读取超时
  write-timeout: 30s           # 写入超时
  max-in-memory-size: 10MB     # 最大内存缓冲区大小
  
  # 连接池配置
  connection-pool:
    max-connections: 500        # 最大连接数
    max-idle-time: 20s         # 最大空闲时间
    max-life-time: 60s         # 连接最大生存时间
    pending-acquire-timeout: 45s # 获取连接超时
    evict-in-background: 120s   # 后台清理间隔
  
  # SSL 配置
  ssl:
    enabled: false              # 是否启用 SSL
    trust-all: false           # 是否信任所有证书
    key-store: classpath:keystore.p12
    key-store-password: password
    trust-store: classpath:truststore.p12
    trust-store-password: password
```

### WebClient 性能调优

```yaml
webclient:
  # 针对高并发场景的优化配置
  connection-pool:
    max-connections: 1000       # 增加最大连接数
    max-idle-time: 30s         # 适当增加空闲时间
    pending-acquire-timeout: 60s # 增加获取连接超时
  
  # 针对大文件传输的优化
  max-in-memory-size: 50MB     # 增加内存缓冲区
  read-timeout: 120s           # 增加读取超时
  write-timeout: 120s          # 增加写入超时
```

## 监控配置

### 基础监控配置

```yaml
monitoring:
  metrics:
    enabled: true               # 启用指标收集
    prefix: "jairouter"        # 指标前缀
    collection-interval: 10s    # 收集间隔
    
    # 启用的指标类别
    enabled-categories:
      - system                  # 系统指标
      - business               # 业务指标
      - infrastructure         # 基础设施指标
    
    # 自定义标签
    custom-tags:
      environment: "production"
      version: "1.0.0"
      datacenter: "us-west-1"
```

### 高级监控配置

```yaml
monitoring:
  metrics:
    # 指标采样配置
    sampling:
      request-metrics: 1.0      # 请求指标采样率（0.0-1.0）
      backend-metrics: 1.0      # 后端调用指标采样率
      infrastructure-metrics: 0.1 # 基础设施指标采样率
    
    # 性能优化配置
    performance:
      async-processing: true    # 异步处理指标
      batch-size: 100          # 批量处理大小
      buffer-size: 1000        # 缓冲区大小
      flush-interval: 5s       # 刷新间隔
    
    # 指标过滤配置
    filters:
      exclude-paths:            # 排除的路径
        - "/actuator/health"
        - "/favicon.ico"
      include-status-codes:     # 包含的状态码
        - 2xx
        - 4xx
        - 5xx
```

## Spring Actuator 配置

### 基础 Actuator 配置

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,jairouter-metrics
      base-path: /actuator      # 端点基础路径
    enabled-by-default: true    # 默认启用所有端点
  
  endpoint:
    health:
      show-details: always      # 显示健康检查详情
      show-components: always   # 显示组件状态
      cache:
        time-to-live: 10s      # 健康检查缓存时间
    
    info:
      enabled: true            # 启用信息端点
    
    metrics:
      enabled: true            # 启用指标端点
    
    prometheus:
      enabled: true            # 启用 Prometheus 端点
      cache:
        time-to-live: 10s      # Prometheus 指标缓存时间
```

### 安全配置

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info    # 仅暴露基础端点
  
  endpoint:
    health:
      show-details: when-authorized # 仅授权用户可见详情
  
  security:
    enabled: true              # 启用安全控制
    roles: ADMIN,ACTUATOR     # 允许的角色
```

## Prometheus 集成配置

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true          # 启用 Prometheus 导出
        descriptions: true     # 包含指标描述
        step: 10s             # 指标步长
        pushgateway:
          enabled: false       # 是否启用 Pushgateway
          base-url: http://localhost:9091
          job: jairouter
          push-rate: 30s
    
    # 全局标签
    tags:
      application: jairouter
      environment: ${spring.profiles.active:default}
      instance: ${spring.application.name:jairouter}
    
    # 指标分布配置
    distribution:
      percentiles-histogram:
        http.server.requests: true
        jairouter.backend.requests: true
      percentiles:
        http.server.requests: 0.5,0.9,0.95,0.99
        jairouter.backend.requests: 0.5,0.9,0.95,0.99
      sla:
        http.server.requests: 10ms,50ms,100ms,200ms,500ms,1s,2s
        jairouter.backend.requests: 100ms,500ms,1s,2s,5s
```

## 日志配置

### 基础日志配置

```yaml
logging:
  level:
    org.unreal.modelrouter: INFO
    org.springframework: WARN
    org.springframework.web: INFO
    reactor.netty: WARN
    io.netty: WARN
  
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  
  file:
    name: logs/jairouter.log
    max-size: 100MB
    max-history: 30
    total-size-cap: 1GB
```

### 环境特定日志配置

```yaml
# application-dev.yml（开发环境）
logging:
  level:
    org.unreal.modelrouter: DEBUG
    org.springframework.web: DEBUG
  pattern:
    console: "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %clr([%thread]){magenta} %clr(%-5level){highlight} %clr(%logger{36}){cyan} - %msg%n"

# application-prod.yml（生产环境）
logging:
  level:
    org.unreal.modelrouter: INFO
    org.springframework: WARN
    org.springframework.web: WARN
  file:
    name: /var/log/jairouter/jairouter.log
    max-size: 500MB
    max-history: 60
```

## 全局模型配置

### 适配器配置

```yaml
model:
  # 全局默认适配器
  adapter: gpustack             # 支持: normal, gpustack, ollama, vllm, xinference, localai
  
  # 适配器特定配置
  adapters:
    gpustack:
      api-version: v1
      timeout: 30s
    ollama:
      api-version: v1
      keep-alive: true
    vllm:
      api-version: v1
      streaming: true
```

### 全局负载均衡配置

```yaml
model:
  load-balance:
    type: round-robin           # 默认负载均衡策略
    hash-algorithm: "md5"       # IP Hash 策略的哈希算法
    
    # 健康检查配置
    health-check:
      enabled: true
      interval: 30s
      timeout: 5s
      failure-threshold: 3
      success-threshold: 2
```

## 存储配置

### 文件存储配置

```yaml
store:
  type: file                   # 存储类型：memory 或 file
  path: "config/"             # 配置文件存储路径
  
  # 文件存储特定配置
  file:
    auto-backup: true          # 自动备份
    backup-interval: 1h        # 备份间隔
    max-backups: 24           # 最大备份数量
    compression: true          # 压缩备份文件
```

### 内存存储配置

```yaml
store:
  type: memory                 # 内存存储
  
  # 内存存储特定配置
  memory:
    initial-capacity: 1000     # 初始容量
    max-size: 10000           # 最大大小
    expire-after-write: 24h    # 写入后过期时间
    expire-after-access: 12h   # 访问后过期时间
```

## 性能调优配置

### JVM 配置

```yaml
# application.yml 中的 JVM 相关配置
spring:
  application:
    name: jairouter
  
  # 线程池配置
  task:
    execution:
      pool:
        core-size: 8           # 核心线程数
        max-size: 32          # 最大线程数
        queue-capacity: 1000   # 队列容量
        keep-alive: 60s       # 线程保活时间
    
    scheduling:
      pool:
        size: 4               # 调度线程池大小
```

### 启动参数建议

```bash
# 生产环境 JVM 参数
java -Xms1g -Xmx2g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/var/log/jairouter/ \
     -Dspring.profiles.active=prod \
     -jar model-router.jar

# 开发环境 JVM 参数
java -Xms512m -Xmx1g \
     -XX:+UseG1GC \
     -Dspring.profiles.active=dev \
     -Ddebug=true \
     -jar model-router.jar
```

## 环境配置示例

### 开发环境配置

```yaml
# application-dev.yml
server:
  port: 8080

logging:
  level:
    org.unreal.modelrouter: DEBUG

webclient:
  connection-timeout: 5s
  read-timeout: 15s

monitoring:
  metrics:
    enabled: true
    collection-interval: 5s

store:
  type: memory
```

### 测试环境配置

```yaml
# application-test.yml
server:
  port: 8080

logging:
  level:
    org.unreal.modelrouter: INFO
  file:
    name: logs/jairouter-test.log

webclient:
  connection-timeout: 10s
  read-timeout: 30s

monitoring:
  metrics:
    enabled: true
    sampling:
      request-metrics: 0.5

store:
  type: file
  path: "config-test/"
```

### 生产环境配置

```yaml
# application-prod.yml
server:
  port: 8080
  tomcat:
    threads:
      max: 200
    max-connections: 8192

logging:
  level:
    org.unreal.modelrouter: INFO
    org.springframework: WARN
  file:
    name: /var/log/jairouter/jairouter.log
    max-size: 500MB

webclient:
  connection-timeout: 10s
  read-timeout: 60s
  connection-pool:
    max-connections: 1000

monitoring:
  metrics:
    enabled: true
    performance:
      async-processing: true
      batch-size: 200

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus

store:
  type: file
  path: "/etc/jairouter/config/"
  file:
    auto-backup: true
    backup-interval: 1h
```

## 配置验证

### 配置检查命令

```bash
# 检查配置文件语法
java -jar model-router.jar --spring.config.location=classpath:/application.yml --spring.profiles.active=prod --dry-run

# 验证端口可用性
netstat -tulpn | grep 8080

# 检查配置加载
curl http://localhost:8080/actuator/configprops
```

### 常见配置错误

1. **端口冲突**
   ```yaml
   # 错误：端口被占用
   server:
     port: 8080
   
   # 解决：更换端口或停止占用进程
   server:
     port: 8081
   ```

2. **超时配置不当**
   ```yaml
   # 错误：超时时间过短
   webclient:
     read-timeout: 1s
   
   # 解决：根据后端响应时间调整
   webclient:
     read-timeout: 30s
   ```

3. **内存配置不足**
   ```yaml
   # 错误：内存缓冲区过小
   webclient:
     max-in-memory-size: 1MB
   
   # 解决：根据请求大小调整
   webclient:
     max-in-memory-size: 10MB
   ```

## 下一步

完成应用配置后，您可以继续配置：

- **[动态配置](dynamic-config.md)** - 学习运行时配置管理
- **[负载均衡](load-balancing.md)** - 配置负载均衡策略
- **[限流配置](rate-limiting.md)** - 设置流量控制
- **[熔断器配置](circuit-breaker.md)** - 配置故障保护