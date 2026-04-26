# 状态持久化配置

> 文档版本：1.0
> 适用版本：v2.4.x
> 最后更新：2026-04-26

---

## 概述

JAiRouter 从 v2.4.4 开始支持状态持久化功能，采用 **三层退坡策略** 实现：

```
Redis (Tier 1) → H2 (Tier 2) → File (Tier 3)
```

- **Tier 1 (Redis)**: 最高优先级，用于分布式共享状态
- **Tier 2 (H2)**: 默认退坡层，复用现有 StoreManager 框架
- **Tier 3 (File)**: 兜底方案，极端情况下使用文件存储

---

## 配置文件

### 基础配置

配置文件位置：`src/main/resources/config/persistence/state-persistence-base.yml`

```yaml
jairouter:
  persistence:
    # 启用状态持久化
    enabled: true

    # Redis 配置 (Tier 1)
    redis:
      enabled: false
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0

    # H2 配置 (Tier 2) - 默认层
    h2:
      enabled: true
      # 复用 StoreManager 的 H2 数据库

    # File 配置 (Tier 3) - 兜底层
    file:
      enabled: true
      path: ./data/state

    # 同步配置
    sync:
      interval: 30000  # 同步间隔（毫秒）
      recoveryTimeout: 10000  # 恢复超时（毫秒）
```

---

## 状态类型

支持以下状态类型持久化：

| 状态类型 | 说明 | 状态数据 |
|----------|------|----------|
| `CIRCUIT_BREAKER` | 熔断器状态 | 状态、失败计数、成功计数、最后失败时间 |
| `LOAD_BALANCER` | 负载均衡器状态 | 实例权重、活跃连接数 |
| `RATE_LIMITER` | 限流器状态 | 令牌数、算法类型、配置参数 |
| `MODEL_STATS` | 模型统计 | 请求计数、成功计数、延迟统计 |
| `CUSTOM` | 自定义状态 | 用户定义的任意状态 |

---

## API 端点

### 状态持久化管理 API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/state-persistence/status` | GET | 获取存储层状态 |
| `/api/state-persistence/details` | GET | 获取所有状态详情 |
| `/api/state-persistence/sync` | POST | 手动触发同步 |
| `/api/state-persistence/recover` | POST | 恢复所有状态 |
| `/api/state-persistence/recover/{type}/{key}` | POST | 恢复单个状态 |
| `/api/state-persistence/rate-limiter/recover/{limiterId}` | POST | 恢复限流器状态 |

### 示例请求

**获取存储层状态**:
```bash
curl -X GET http://localhost:8080/api/state-persistence/status \
  -H "Authorization: Bearer {token}"
```

**响应示例**:
```json
{
  "success": true,
  "data": {
    "activeTier": "h2",
    "tiers": {
      "redis": false,
      "h2": true,
      "file": true
    },
    "stats": {
      "circuitBreakerCount": 5,
      "loadBalancerCount": 6,
      "rateLimiterCount": 0
    }
  }
}
```

---

## 性能指标

| 指标 | 目标 | 说明 |
|------|------|------|
| 单次状态保存 | < 100ms | H2 层延迟 |
| Redis 状态保存 | < 50ms | Redis 层延迟 |
| 批量保存吞吐量 | > 100 ops/s | 100 个状态 |
| 恢复时间 | < 10s | 启动恢复 |
| 降级切换 | < 1s | 层切换延迟 |

---

## 最佳实践

### 1. 生产环境推荐配置

```yaml
jairouter:
  persistence:
    enabled: true
    redis:
      enabled: true  # 生产环境推荐启用 Redis
    h2:
      enabled: true  # 作为退坡层
    file:
      enabled: true  # 作为兜底层
```

### 2. 开发环境配置

```yaml
jairouter:
  persistence:
    enabled: true
    redis:
      enabled: false  # 开发环境可以不启用 Redis
    h2:
      enabled: true
    file:
      enabled: false  # 开发环境可以不启用文件存储
```

### 3. 监控建议

- 监控 `activeTier` 变化，及时发现退坡切换
- 监控 `pendingSyncCount`，确保状态同步正常
- 定期检查健康状态，确保存储层可用

---

## 故障恢复

### 自动退坡

当某一层不可用时，系统自动切换到下一层：

1. Redis 不可用 → 自动切换到 H2
2. H2 不可用 → 自动切换到 File
3. 所有层不可用 → 返回错误

### 手动恢复

```bash
# 恢复所有状态
curl -X POST http://localhost:8080/api/state-persistence/recover

# 恢复单个熔断器状态
curl -X POST http://localhost:8080/api/state-persistence/recover/circuit_breaker/ollama-1

# 恢复限流器状态
curl -X POST http://localhost:8080/api/state-persistence/rate-limiter/recover/global
```

---

## 相关文档

- [熔断器配置](circuit-breaker.md)
- [限流配置](rate-limiting.md)
- [存储配置](store-config.md)

---

## 变更记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| 1.0 | 2026-04-26 | 状态持久化配置文档初始版本 |