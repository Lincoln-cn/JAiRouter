# JWT Token 持久化配置指南

<!-- 版本信息 -->
> **文档版本**: 1.0.3
> **最后更新**: 2026-09-15
> **Git 提交**: 61384b4a
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

        # 复合存储策略（Redis + StoreManager）
        composite:
          enabled: true

        # 多存储之间的数据同步
        sync:
          enabled: true

        # 启动时从持久化存储恢复令牌
        startup-recovery:
          enabled: true

        # 清理配置
        cleanup:
          enabled: true
          schedule: "0 0 2 * * ?"  # Cron表达式：每天凌晨2点
          retention-days: 30       # 保留令牌30天
          batch-size: 1000        # 每批处理1000个令牌

        # Redis 存储只有开关，连接信息统一由 spring.data.redis.*（或 REDIS_HOST 等环境变量）提供
        redis:
          enabled: false
```

> **只有以上键会被读取。** 存储实现只有 JPA 一种（H2 只是 JPA 使用的数据库），`redis` / `memory`
> 作为独立存储实现从未落地；任何 `primary-storage`、`fallback-storage`、`memory.*`、
> `redis.key-prefix` / `default-ttl` / `connection-timeout` / `retry-attempts` /
> `serialization-format` 之类的键在今天都不是可配置项。可对照
> `src/main/resources/config/security/persistence-base.yml`。

### 黑名单配置

```yaml
jairouter:
  security:
    jwt:
      # 黑名单持久化配置
      blacklist:
        persistence:
          enabled: false          # 默认：禁用
        composite:
          enabled: true           # 复合存储策略（Redis + StoreManager）
        redis:
          enabled: false          # 只有开关，连接信息见 spring.data.redis.*
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
        composite:
          enabled: true
        cleanup:
          schedule: "0 */5 * * * ?"  # 每5分钟用于测试
          retention-days: 1          # 开发环境短保留期
      blacklist:
        persistence:
          enabled: true

    # 开发审计配置
    audit:
      retention-days: 7          # 开发环境短保留期
```

### 生产环境

```yaml
# application-prod.yml
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        composite:
          enabled: true
        redis:
          enabled: true   # 生产环境用 Redis 做共享存储
      blacklist:
        persistence:
          enabled: true
        redis:
          enabled: true

    # 生产审计配置
    audit:
      enabled: true
      retention-days: 180   # 生产环境更长保留期

# 监控配置
jairouter:
  security:
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
        composite:
          enabled: true
        redis:
          enabled: true
        cleanup:
          retention-days: 7      # 预发布环境较短保留期
      blacklist:
        persistence:
          enabled: true
        redis:
          enabled: true

    # 预发布审计配置
    audit:
      enabled: true
      retention-days: 30
```

## 监控配置

### 指标配置

```yaml
# config/monitor/persistence-monitoring-base.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  metrics:
    tags:
      application: jairouter
  health:
    show-details: when-authorized

# 自定义监控指标（前缀是 jairouter.security.monitoring，不是 jairouter.monitoring）
jairouter:
  security:
    monitoring:
      jwt-persistence:
        enabled: false  # 默认：禁用，按环境启用
        metrics:
          token-operations:
            histogram-buckets: [0.01, 0.05, 0.1, 0.5, 1.0, 2.0, 5.0]
          blacklist-operations:
            histogram-buckets: [0.001, 0.01, 0.05, 0.1, 0.5, 1.0]
          storage-health:
            include-storages:
              - redis
              - memory
              - composite
        health-checks:
          enabled: true
          check-interval: 60  # 秒
```

> 这两个 `enabled` 开关控制的只是指标与健康指标的采集端；自定义 actuator 端点
> `jwt-tokens` / `jwt-blacklist` 并不存在，写 `management.endpoint.jwt-tokens.enabled`
> 不会有任何效果。

### 健康检查配置

```yaml
management:
  health:
    redis:
      enabled: true   # Spring 内置 Redis 健康指示器
  endpoint:
    health:
      show-details: when-authorized
      show-components: always
```

> JWT 落库/缓存的健康状态由 `jwtRedisHealthIndicator` 提供（`HealthIndicator` 实现），
> 它是否装配取决于 `jairouter.security.jwt.persistence.redis.enabled` 与
> `jairouter.security.jwt.blacklist.redis.enabled` 两个开关，**没有** `management.health.jwt-persistence` 这个键。

## Redis 配置

### 基本 Redis 设置

```yaml
spring:
  data:
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
  data:
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
  data:
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
    # 密钥 / 连接凭据统一走环境变量或 spring.data.redis.password，
    # 持久化树里没有可配的 key-prefix / 序列化格式 / 内存淘汰参数
    jwt:
      persistence:
        redis:
          enabled: true

    audit:
      enabled: true
      retention-days: 90
```

### 访问控制

```yaml
management:
  endpoints:
    web:
      base-path: /actuator
      exposure:
        include: health,metrics
  endpoint:
    health:
      show-details: when-authorized
```

> actuator 的访问控制由 Spring Security 的 URL 规则决定（本仓是 `/actuator/**` 需要
> `ROLE_ADMIN`），`management.security.*` 这类 Spring Boot 1.x 的键在今天无效。

## 性能调优

### 内存优化

```yaml
jairouter:
  security:
    jwt:
      persistence:
        cleanup:
          batch-size: 2000        # 更大批次提升性能
          retention-days: 15      # 高流量系统更短保留期
```

### Redis 优化

```yaml
# 持久化树里只有 redis.enabled 一个 Redis 键；超时 / 重试 / 序列化 / TTL
# 由 spring.data.redis.* 与令牌自身的过期时间决定
spring:
  data:
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
```

## 故障排除配置

### 调试日志

```yaml
logging:
  level:
    org.unreal.modelrouter.auth.security.jwt: DEBUG
    org.unreal.modelrouter.auth.audit: DEBUG
    org.unreal.modelrouter.auth.security.persistence: DEBUG
    org.springframework.data.redis: DEBUG
```

### 健康检查配置

```yaml
management:
  health:
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
  security:
    monitoring:
      jwt-persistence:
        enabled: true
        metrics:
          token-operations:
            histogram-buckets: [0.01, 0.05, 0.1, 0.5, 1.0, 2.0, 5.0]
          blacklist-operations:
            histogram-buckets: [0.001, 0.01, 0.05, 0.1, 0.5, 1.0]
        health-checks:
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

# Redis 配置
export REDIS_HOST="your-redis-host"
export REDIS_PORT=6379
export REDIS_PASSWORD="your-redis-password"
```

> 审计没有 `SECURITY_AUDIT_*` 这类环境变量；开关与保留期写在
> `jairouter.security.audit.enabled` / `jairouter.security.audit.retention-days`。
> 没有设置 `JWT_SECRET` 时生产 profile 会直接拒绝启动（`StartupSecurityGate`）。

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
# 先只打开持久化本身（复合存储默认开启，Redis 先不接）
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        composite:
          enabled: true
        redis:
          enabled: false
```

3. **测试后启用 Redis**
```yaml
# 升级到 Redis 做共享存储，复合存储保留 StoreManager 作为落库侧
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        composite:
          enabled: true
        redis:
          enabled: true
      blacklist:
        redis:
          enabled: true
```

4. **启用完整审计**
```yaml
# 启用全面审计
jairouter:
  security:
    audit:
      enabled: true
```

## 最佳实践

### 安全最佳实践

1. **永不记录敏感数据**
```yaml
jairouter:
  security:
    audit:
```

2. **使用强 Redis 安全**
```yaml
spring:
  data:
    redis:
      password: ${REDIS_PASSWORD}  # 总是使用密码
      ssl: true                   # 生产环境使用SSL
```

3. **控制落库数据量**
```yaml
jairouter:
  security:
    jwt:
      persistence:
        cleanup:
          enabled: true
          retention-days: 30      # 过期令牌的保留上限
          batch-size: 1000        # 单次清理批量
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

2. **令牌有效期由 JWT 配置决定**
```yaml
# 持久化树里没有 default-ttl：令牌多久失效由签发时的过期时间决定
jairouter:
  security:
    jwt:
      expiration-minutes: 60   # 短期访问令牌
```

3. **监控性能**
```yaml
jairouter:
  security:
    monitoring:
      jwt-persistence:
        enabled: true
        health-checks:
          enabled: true
          check-interval: 30  # 定期健康监控（秒）
```

## 相关文档

- [JWT 认证配置](../security/jwt-authentication.md)
- [安全监控](../monitoring/index.md)
- [Redis 配置](../deployment/production.md#redis-setup)
- [性能调优](../troubleshooting/performance.md)