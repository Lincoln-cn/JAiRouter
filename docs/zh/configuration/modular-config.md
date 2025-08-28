# 模块化配置指南

## 概述

JAiRouter 采用模块化的配置管理方式，将复杂的配置按功能拆分为多个独立的配置文件。这种设计提高了配置的可维护性、可读性和可重用性。

## 配置结构

### 主配置文件

```yaml
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

## 配置优先级

配置加载遵循以下优先级顺序（高优先级覆盖低优先级）：

1. 基础配置模块（最低优先级）
2. 功能配置模块
3. 环境特定配置文件
4. 外部配置文件
5. 环境变量
6. 命令行参数（最高优先级）

## 基础配置模块详解

### 服务器基础配置 (server-base.yml)

包含服务器端口、存储路径、WebClient 基础配置等：

```yaml
server:
  port: 8080

store:
  type: file
  path: "config/"

webclient:
  connection-timeout: 10s
  read-timeout: 30s
  write-timeout: 30s
  max-in-memory-size: 10MB
```

### 模型服务基础配置 (model-services-base.yml)

包含负载均衡、限流、熔断等核心业务配置：

```yaml
model:
  load-balance:
    type: random
  adapter: gpustack
  rate-limit:
    enabled: true
    algorithm: "token-bucket"
  circuit-breaker:
    enabled: true
    failureThreshold: 5
```

### 监控基础配置 (monitoring-base.yml)

包含监控指标、管理端点等可观测性配置：

```yaml
monitoring:
  enabled: true
  metrics:
    enabled: true
    prefix: "jairouter"

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,jairouter-metrics
```

## 功能配置模块详解

### 追踪配置 (tracing-base.yml)

分布式追踪相关的完整配置：

```yaml
jairouter:
  tracing:
    enabled: true
    service-name: "jairouter"
    sampling:
      strategy: "parent_based_traceid_ratio"
      ratio: 1.0
    exporter:
      type: "logging"
```

### 安全配置 (security-base.yml)

安全功能的基础配置模板：

```yaml
jairouter:
  security:
    enabled: false  # 默认关闭，各环境按需启用
    api-key:
      enabled: false
      header-name: "X-API-Key"
    jwt:
      enabled: false
      secret: ""
```

### 慢查询告警配置 (slow-query-alerts.yml)

性能监控和告警配置：

```yaml
jairouter:
  monitoring:
    slow-query-alert:
      enabled: true
      global:
        enabled: true
        min-interval-ms: 300000
```

### 错误追踪配置 (error-tracking.yml)

错误监控和分析配置：

```yaml
jairouter:
  monitoring:
    error-tracking:
      enabled: false  # 默认关闭，各环境按需启用
      aggregation-window-minutes: 5
      sanitization:
        enabled: true
```

## 环境配置文件详解

### 开发环境 (application-dev.yml)

为开发和测试优化的配置：

```yaml
# 启用详细日志
logging:
  level:
    org.unreal.modelrouter: DEBUG

# 启用 Swagger 文档
springdoc:
  swagger-ui:
    path: /swagger-ui.html

jairouter:
  security:
    enabled: true  # 开发环境启用安全功能进行测试
```

### 预发布环境 (application-staging.yml)

接近生产环境的配置：

```yaml
# 详细日志配置
logging:
  level:
    root: INFO
    org.unreal.modelrouter.security: DEBUG

jairouter:
  security:
    enabled: true  # 预发布环境启用安全功能进行测试
```

### 生产环境 (application-prod.yml)

为生产环境优化的配置：

```yaml
# 最小化日志输出
logging:
  level:
    root: WARN

jairouter:
  security:
    enabled: false  # 生产环境安全功能默认关闭，需要显式启用
```

### 向后兼容环境 (application-legacy.yml)

确保旧版本部署平滑升级的配置：

```yaml
jairouter:
  security:
    enabled: false  # 向后兼容模式下所有安全功能默认关闭
```

## 使用指南

### 启动不同环境

```bash
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