# JWT Token 持久化配置指南

<!-- 版本信息 -->
> **文档版本**: 1.0.2
> **最后更新**: 2026-05-21
> **Git 提交**: 61384b4a-
> **作者**: System
<!-- /版本信息 -->

## 概述

JWT Token 持久化提供全面的令牌生命周期管理，包括持久化存储、黑名单管理、自动清理和安全审计。本指南涵盖 JWT 持久化系统的所有配置选项。

## 配置结构

JWT 持久化配置遵循 JAiRouter 的模块化配置方法：

```yaml
# 主应用配置
spring:
  config:
    import:
      - classpath:config/security/security-base.yml
      - classpath:config/security/persistence-base.yml  # JWT 持久化配置
      - classpath:config/monitoring/persistence-monitoring-base.yml
```

## 核心配置

### 基本持久化配置

```yaml
jairouter:
  security:
    jwt:
      # 令牌持久化配置
      persistence:
        enabled: false  # 默认：禁用，按环境启用
        primary-storage: redis    # 选项：redis, memory
        fallback-storage: memory  # 选项：memory

        # 清理配置
        cleanup:
          enabled: true
          schedule: "0 0 2 * * ?"  # Cron表达式：每天凌晨2点
          retention-days: 30       # 保留令牌30天
          batch-size: 1000        # 每批处理1000个令牌

        # 内存存储配置
        memory:
          max-tokens: 50000       # 内存中最大令牌数
          cleanup-threshold: 0.8  # 满80%时清理
          lru-enabled: true       # 使用LRU淘汰策略

        # Redis存储配置
        redis:
          key-prefix: "jwt:"      # Redis键前缀
          default-ttl: 3600       # 默认TTL（秒）
          connection-timeout: 5000 # 连接超时（毫秒）
          retry-attempts: 3       # 失败操作重试次数
          serialization-format: "json"  # 选项：json, binary
```

### 黑名单配置

```yaml
jairouter:
  security:
    jwt:
      # 黑名单持久化配置
      blacklist:
        persistence:
          enabled: false          # 默认：禁用
          primary-storage: redis  # 选项：redis, memory
          fallback-storage: memory
          max-memory-size: 10000  # 内存中最大黑名单条目数
          cleanup-interval: 3600  # 清理间隔（秒，1小时）
```

### 安全审计配置

```yaml
jairouter:
  security:
    # 增强审计配置
    audit:
      enabled: true
      log-level: "INFO"
      include-request-body: false
      include-response-body: false
      retention-days: 90

      # JWT操作审计
      jwt-operations:
        enabled: true
        log-token-details: false  # 安全：不记录完整令牌
        log-user-agent: true
        log-ip-address: true

      # API Key操作审计
      api-key-operations:
        enabled: true
        log-key-details: false   # 安全：不记录完整密钥
        log-usage-patterns: true
        log-ip-address: true

      # 安全事件审计
      security-events:
        enabled: true
        suspicious-activity-detection: true
        alert-thresholds:
          failed-auth-per-minute: 10
          token-revoke-per-minute: 5
          api-key-usage-per-minute: 100

      # 审计存储配置
      storage:
        type: "file"              # 选项：file, database
        file-path: "logs/security-audit.log"
        rotation:
          max-file-size: "100MB"
          max-files: 30
        # 可选：数据库存储
        database:
          enabled: false
          table-name: "security_audit_events"
```

## 环境特定配置

### 开发环境

```yaml
# application-dev.yml
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        primary-storage: memory  # 开发环境使用内存
        fallback-storage: memory
        cleanup:
          schedule: "0 */5 * * * ?"  # 每5分钟用于测试
          retention-days: 1          # 开发环境短保留期
      blacklist:
        persistence:
          enabled: true
          primary-storage: memory

    # 开发审计配置
    audit:
      jwt-operations:
        enabled: true
        log-token-details: true  # 开发环境可记录详情
      api-key-operations:
        enabled: true
        log-key-details: true
      retention-days: 7          # 开发环境短保留期

# 启用调试端点
management:
  endpoint:
    jwt-tokens:
      enabled: true
    jwt-blacklist:
      enabled: true
```

### 生产环境

```yaml
# application-prod.yml
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        primary-storage: redis   # 生产环境使用Redis
        fallback-storage: memory
        redis:
          connection-timeout: 3000
          retry-attempts: 5
      blacklist:
        persistence:
          enabled: true
          primary-storage: redis
          max-memory-size: 50000

    # 生产审计配置
    audit:
      enabled: true
      jwt-operations:
        enabled: true
        log-token-details: false  # 安全：生产环境不记录令牌详情
      api-key-operations:
        enabled: true
        log-key-details: false
      security-events:
        enabled: true
        suspicious-activity-detection: true
        alert-thresholds:
          failed-auth-per-minute: 20  # 生产环境更高阈值
          token-revoke-per-minute: 10
          api-key-usage-per-minute: 500
      storage:
        type: "file"
        # 生产环境考虑数据库存储
        database:
          enabled: false  # 需要时启用
      retention-days: 180   # 生产环境更长保留期

# 监控配置
jairouter:
  monitoring:
    jwt-persistence:
      enabled: true
```

### 预发布环境

```yaml
# application-staging.yml
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        primary-storage: redis
        fallback-storage: memory
        cleanup:
          retention-days: 7      # 预发布环境较短保留期
      blacklist:
        persistence:
          enabled: true
          primary-storage: redis

    # 预发布审计配置
    audit:
      enabled: true
      jwt-operations:
        enabled: true
        log-token-details: false
      api-key-operations:
        enabled: true
        log-key-details: false
      security-events:
        enabled: true
        suspicious-activity-detection: true
      retention-days: 30

# 预发布环境启用调试端点
management:
  endpoint:
    jwt-tokens:
      enabled: true
    jwt-blacklist:
      enabled: true
```

## 监控配置

### 指标配置

```yaml
# config/monitoring/persistence-monitoring-base.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,jwt-tokens,jwt-blacklist
  metrics:
    tags:
      application: jairouter
    export:
      prometheus:
        enabled: true
  endpoint:
    jwt-tokens:
      enabled: false  # 默认：禁用，按环境启用
    jwt-blacklist:
      enabled: false

# 自定义监控指标
jairouter:
  monitoring:
    jwt-persistence:
      enabled: false  # 默认：禁用，按环境启用
      metrics:
        token-operations:
          enabled: true
          histogram-buckets: [0.01, 0.05, 0.1, 0.5, 1.0, 2.0, 5.0]
        blacklist-operations:
          enabled: true
          histogram-buckets: [0.001, 0.01, 0.05, 0.1, 0.5, 1.0]
        storage-health:
          enabled: true
          check-interval: 30  # 秒
```

### 健康检查配置

```yaml
management:
  health:
    jwt-persistence:
      enabled: true
    redis:
      enabled: true
  endpoint:
    health:
      show-details: when-authorized
      show-components: always
```

## Redis 配置

### 基本 Redis 设置

```yaml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    timeout: 5000ms
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
        max-wait: 2000ms
```

### Redis 集群配置

```yaml
spring:
  redis:
    cluster:
      nodes:
        - ${REDIS_NODE1:localhost:7001}
        - ${REDIS_NODE2:localhost:7002}
        - ${REDIS_NODE3:localhost:7003}
      max-redirects: 3
    lettuce:
      cluster:
        refresh:
          adaptive: true
          period: 30s
```

### Redis Sentinel 配置

```yaml
spring:
  redis:
    sentinel:
      master: mymaster
      nodes:
        - ${REDIS_SENTINEL1:localhost:26379}
        - ${REDIS_SENTINEL2:localhost:26380}
        - ${REDIS_SENTINEL3:localhost:26381}
```

## 安全配置

### 密钥安全设置

```yaml
jairouter:
  security:
    jwt:
      persistence:
        # 安全设置
        redis:
          key-prefix: "jwt:"      # 命名空间隔离
          serialization-format: "json"  # 安全避免二进制格式
        memory:
          lru-enabled: true       # 防止内存耗尽
          max-tokens: 50000       # 硬限制

    audit:
      # 安全审计设置
      jwt-operations:
        log-token-details: false  # 永不记录完整令牌
      api-key-operations:
        log-key-details: false   # 永不记录完整密钥
      security-events:
        enabled: true            # 总是启用安全事件
        suspicious-activity-detection: true
```

### 访问控制

```yaml
management:
  endpoints:
    web:
      base-path: /actuator
      exposure:
        include: health,metrics
        exclude: jwt-tokens,jwt-blacklist  # 限制敏感端点
  endpoint:
    jwt-tokens:
      enabled: false  # 仅在开发/预发布环境启用
    jwt-blacklist:
      enabled: false
  security:
    enabled: true
    roles: ADMIN  # 管理端点需要管理员角色
```

## 性能调优

### 内存优化

```yaml
jairouter:
  security:
    jwt:
      persistence:
        memory:
          max-tokens: 100000      # 根据可用内存调整
          cleanup-threshold: 0.75 # 更早清理防止OOM
          lru-enabled: true       # 启用LRU淘汰
        cleanup:
          batch-size: 2000        # 更大批次提升性能
          retention-days: 15      # 高流量系统更短保留期
```

### Redis 优化

```yaml
jairouter:
  security:
    jwt:
      persistence:
        redis:
          connection-timeout: 2000  # 高性能更快超时
          retry-attempts: 2         # 更少重试更快故障转移
          serialization-format: "binary"  # 比JSON性能更好
          default-ttl: 1800        # 高流量系统更短TTL

spring:
  redis:
    lettuce:
      pool:
        max-active: 50    # 高并发更多连接
        max-idle: 20
        min-idle: 10
```

### 清理优化

```yaml
jairouter:
  security:
    jwt:
      persistence:
        cleanup:
          schedule: "0 */30 * * * ?"  # 更频繁清理
          batch-size: 5000            # 更大批次
          retention-days: 7           # 更短保留期
      blacklist:
        persistence:
          cleanup-interval: 1800      # 更频繁黑名单清理
```

## 故障排除配置

### 调试日志

```yaml
logging:
  level:
    org.unreal.modelrouter.security.jwt: DEBUG
    org.unreal.modelrouter.security.audit: DEBUG
    org.unreal.modelrouter.security.persistence: DEBUG
    org.springframework.data.redis: DEBUG
```

### 健康检查配置

```yaml
management:
  health:
    jwt-persistence:
      enabled: true
    redis:
      enabled: true
  endpoint:
    health:
      show-details: always  # 显示详细健康信息
      show-components: always
```

### 故障排除指标

```yaml
jairouter:
  monitoring:
    jwt-persistence:
      enabled: true
      metrics:
        token-operations:
          enabled: true
        blacklist-operations:
          enabled: true
        storage-health:
          enabled: true
          check-interval: 10  # 更频繁健康检查
```

## 配置验证

### 必需环境变量

生产部署需要设置这些环境变量：

```bash
# JWT 配置
export JWT_SECRET="your-production-jwt-secret-key"
export JWT_EXPIRATION_MINUTES=15
export JWT_REFRESH_EXPIRATION_DAYS=30

# Redis 配置
export REDIS_HOST="your-redis-host"
export REDIS_PORT=6379
export REDIS_PASSWORD="your-redis-password"

# 安全配置
export SECURITY_AUDIT_ENABLED=true
export SECURITY_AUDIT_RETENTION_DAYS=180
```

### 配置验证脚本

创建验证脚本检查配置：

```bash
#!/bin/bash
# validate-jwt-persistence-config.sh

echo "验证 JWT 持久化配置..."

# 检查必需环境变量
required_vars=("JWT_SECRET" "REDIS_HOST")
for var in "${required_vars[@]}"; do
    if [ -z "${!var}" ]; then
        echo "错误: 必需环境变量 $var 未设置"
        exit 1
    fi
done

# 检查 Redis 连接
if ! redis-cli -h "$REDIS_HOST" -p "${REDIS_PORT:-6379}" ping > /dev/null 2>&1; then
    echo "警告: 无法连接到 Redis $REDIS_HOST:${REDIS_PORT:-6379}"
fi

echo "配置验证完成"
```

## 迁移指南

### 在现有系统上启用持久化

1. **备份当前配置**
```bash
cp application.yml application.yml.backup
```

2. **逐步更新配置**
```yaml
# 从内存持久化开始
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        primary-storage: memory
        fallback-storage: memory
```

3. **测试后启用 Redis**
```yaml
# 升级到 Redis，保留内存作为备选
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        primary-storage: redis
        fallback-storage: memory
```

4. **启用完整审计**
```yaml
# 启用全面审计
jairouter:
  security:
    audit:
      enabled: true
      jwt-operations:
        enabled: true
      api-key-operations:
        enabled: true
```

## 最佳实践

### 安全最佳实践

1. **永不记录敏感数据**
```yaml
jairouter:
  security:
    audit:
      jwt-operations:
        log-token-details: false  # 永不记录完整令牌
      api-key-operations:
        log-key-details: false   # 永不记录完整密钥
```

2. **使用强 Redis 安全**
```yaml
spring:
  redis:
    password: ${REDIS_PASSWORD}  # 总是使用密码
    ssl: true                   # 生产环境使用SSL
```

3. **限制内存使用**
```yaml
jairouter:
  security:
    jwt:
      persistence:
        memory:
          max-tokens: 50000       # 设置合理限制
          cleanup-threshold: 0.8  # 耗尽前清理
```

### 性能最佳实践

1. **优化清理计划**
```yaml
jairouter:
  security:
    jwt:
      persistence:
        cleanup:
          schedule: "0 0 2 * * ?"  # 低流量时段运行
          batch-size: 1000         # 平衡内存和性能
```

2. **使用合适的 TTL**
```yaml
jairouter:
  security:
    jwt:
      persistence:
        redis:
          default-ttl: 3600  # 匹配或超过令牌过期时间
```

3. **监控性能**
```yaml
jairouter:
  monitoring:
    jwt-persistence:
      enabled: true
      metrics:
        storage-health:
          check-interval: 30  # 定期健康监控
```

## 相关文档

- [JWT 认证配置](../security/jwt-authentication.md)
- [安全监控](../monitoring/index.md)
- [Redis 配置](../deployment/production.md#redis-setup)
- [性能调优](../troubleshooting/performance.md)