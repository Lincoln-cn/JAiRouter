# 应用配置

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->

本文档详细介绍 JAiRouter 的基础应用配置，包括服务器配置、WebClient 配置、监控配置等。

## 配置文件位置

- **主配置文件**：[src/main/resources/application.yml](file://d:/IdeaProjects/model-router/src/main/resources/application.yml)
- **环境配置**：`application-{profile}.yml`
- **外部配置**：`config/application.yml`（可选）
- **模块化配置**：`config/{module}/*.yml`

## 模块化配置说明

JAiRouter 采用模块化的配置管理方式，将复杂的配置按功能拆分为多个独立的配置文件。这种设计提高了配置的可维护性、可读性和可重用性。

### 配置结构

```
# application.yml
spring:
  config:
    import:
      - classpath:config/base/server-base.yml
      - classpath:config/base/model-services-base.yml
      - classpath:config/base/monitoring-base.yml
      - classpath:config/tracing/tracing-base.yml
      - classpath:config/security/security-base.yml
      - classpath:config/monitoring/slow-query-alerts.yml
      - classpath:config/monitoring/error-tracking.yml
```

### 配置模块分类

1. **基础配置模块** (`config/base/`)
   - [server-base.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/base/server-base.yml) - 服务器基础配置
   - [model-services-base.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/base/model-services-base.yml) - 模型服务配置
   - [monitoring-base.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/base/monitoring-base.yml) - 监控基础配置

2. **功能配置模块** (`config/{feature}/`)
   - [tracing/tracing-base.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/tracing/tracing-base.yml) - 追踪功能配置
   - [security/security-base.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/security/security-base.yml) - 安全功能配置
   - [monitoring/slow-query-alerts.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/slow-query-alerts.yml) - 慢查询告警配置
   - [monitoring/error-tracking.yml](file://d:/IdeaProjects/model-router/src/main/resources/config/monitoring/error-tracking.yml) - 错误追踪配置

3. **环境配置文件** (`application-{profile}.yml`)
   - [application-dev.yml](file://d:/IdeaProjects/model-router/src/main/resources/application-dev.yml) - 开发环境配置
   - [application-staging.yml](file://d:/IdeaProjects/model-router/src/main/resources/application-staging.yml) - 预发布环境配置
   - [application-prod.yml](file://d:/IdeaProjects/model-router/src/main/resources/application-prod.yml) - 生产环境配置
   - [application-legacy.yml](file://d:/IdeaProjects/model-router/src/main/resources/application-legacy.yml) - 向后兼容配置

### 配置优先级

配置加载遵循以下优先级顺序（高优先级覆盖低优先级）：

1. 基础配置模块（最低优先级）
2. 功能配置模块
3. 环境特定配置文件
4. 外部配置文件
5. 环境变量
6. 命令行参数（最高优先级）

## 服务器配置

### 基本服务器配置

```
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

```
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

```
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

```
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

```
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

```
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

```
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

```
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

```
management:
  metrics:
    export:
      prometheus:
        enabled: true
        descriptions: true
        step: 10s
  endpoint:
    prometheus:
      enabled: true
```

## 使用指南

### 启动不同环境

```
# 启动开发环境
java -jar app.jar --spring.profiles.active=dev

# 启动预发布环境
java -jar app.jar --spring.profiles.active=staging

# 启动生产环境
java -jar app.jar --spring.profiles.active=prod

# 启动兼容模式
java -jar app.jar --spring.profiles.active=legacy
```

### 修改配置

1. **基础配置修改**：编辑 [config/base/](file://d:/IdeaProjects/model-router/src/main/resources/config/base/) 目录下的对应文件
2. **功能启用/禁用**：编辑对应的功能配置文件
3. **环境差异配置**：编辑对应的环境配置文件
4. **敏感配置**：建议使用环境变量注入

### 配置最佳实践

1. **模块化原则**：按功能将配置拆分为独立模块
2. **环境分离**：使用环境配置文件覆盖基础配置
3. **敏感信息保护**：通过环境变量注入敏感配置
4. **版本控制**：将配置文件纳入版本控制
5. **文档同步**：保持配置与文档的一致性

## 故障排除

### 配置未生效

1. 检查配置文件路径是否正确
2. 确认环境配置文件是否正确加载
3. 验证配置优先级顺序
4. 检查是否有语法错误

### 配置冲突

1. 理解配置优先级规则
2. 检查是否有重复配置项
3. 确认环境变量是否覆盖了预期配置
4. 使用 `--debug` 参数查看配置加载过程
