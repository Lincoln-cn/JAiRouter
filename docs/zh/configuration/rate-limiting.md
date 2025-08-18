# 限流配置

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: 3418d3f6  
> **作者**: Lincoln
<!-- /版本信息 -->


JAiRouter 提供多种限流算法，支持全局、服务级别和实例级别的限流配置，以及基于客户端 IP 的独立限流。本文档详细介绍各种限流策略的配置和使用。

## 限流概述

### 支持的算法

| 算法 | 特点 | 适用场景 | 突发处理 |
|------|------|----------|----------|
| **Token Bucket** | 允许突发流量 | 平稳流量 + 偶发突发 | 优秀 |
| **Leaky Bucket** | 平滑输出流量 | 需要平稳输出 | 一般 |
| **Sliding Window** | 精确时间窗口控制 | 精确流量控制 | 一般 |
| **Warm Up** | 冷启动保护 | 服务启动场景 | 渐进式 |

### 限流层级

```mermaid
graph TB
    A[全局限流配置] --> B[服务级限流配置]
    B --> C[实例级限流配置]
    
    A --> D[默认算法和参数]
    B --> E[服务特定限流]
    C --> F[实例独立限流]
    
    G[客户端IP限流] --> B
    G --> C
    
    style A fill:#e1f5fe
    style B fill:#f3e5f5
    style C fill:#e8f5e8
    style G fill:#fff3e0
```

## 全局限流配置

### 基础配置

```yaml
# application.yml
model:
  rate-limit:
    enabled: true               # 启用全局限流
    algorithm: "token-bucket"   # 默认算法
    capacity: 1000             # 默认容量
    rate: 100                  # 默认速率
    scope: "service"           # 限流范围：service 或 instance
    client-ip-enable: true     # 启用客户端IP限流
```

### 高级配置

```yaml
model:
  rate-limit:
    enabled: true
    algorithm: "token-bucket"
    
    # 客户端IP限流配置
    client-ip:
      enabled: true
      cleanup-interval: 300s    # 清理间隔
      max-idle-time: 1800s     # 最大空闲时间
      max-clients: 10000       # 最大客户端数量
    
    # 限流器性能配置
    performance:
      async-processing: true    # 异步处理
      batch-size: 100          # 批量处理大小
      cache-size: 1000         # 缓存大小
```

## 限流算法详解

### 1. Token Bucket（令牌桶算法）

#### 算法原理

令牌桶算法维护一个固定容量的桶，以固定速率向桶中添加令牌。请求到达时消耗令牌，无令牌时拒绝请求。

```mermaid
graph LR
    A[令牌生成器] -->|固定速率| B[令牌桶]
    C[请求] --> D{桶中有令牌?}
    D -->|是| E[消耗令牌，允许请求]
    D -->|否| F[拒绝请求]
    B --> D
```

#### 配置示例

```yaml
model:
  services:
    chat:
      rate-limit:
        enabled: true
        algorithm: "token-bucket"
        capacity: 100            # 桶容量（最大令牌数）
        rate: 10                # 令牌补充速率（每秒）
        scope: "service"        # 限流范围
        client-ip-enable: true  # 启用客户端IP限流
```

#### JSON 配置

```json
{
  "services": {
    "chat": {
      "rateLimit": {
        "type": "token-bucket",
        "capacity": 100,
        "refillRate": 10,
        "clientIpEnable": true
      }
    }
  }
}
```

#### 参数说明

| 参数 | 类型 | 说明 | 默认值 |
|------|------|------|--------|
| `capacity` | Integer | 桶容量，最大令牌数 | 100 |
| `rate`/`refillRate` | Integer | 令牌补充速率（每秒） | 10 |
| `client-ip-enable` | Boolean | 是否启用客户端IP限流 | false |

#### 适用场景

- **平稳流量 + 偶发突发**：日常流量平稳，偶尔有突发请求
- **API 网关**：需要允许短时间突发，但控制长期平均速率
- **用户请求限制**：允许用户短时间内发送多个请求

#### 配置建议

```yaml
# 高并发场景
rate-limit:
  algorithm: "token-bucket"
  capacity: 1000              # 大容量支持突发
  rate: 100                  # 适中的补充速率

# 保护后端场景
rate-limit:
  algorithm: "token-bucket"
  capacity: 50               # 小容量限制突发
  rate: 5                    # 低速率保护后端
```

### 2. Leaky Bucket（漏桶算法）

#### 算法原理

漏桶算法维护一个固定容量的桶，请求进入桶中排队，以固定速率从桶底流出处理。

```mermaid
graph TB
    A[请求流入] --> B{桶是否满?}
    B -->|否| C[请求进入桶]
    B -->|是| D[拒绝请求]
    C --> E[桶中排队]
    E -->|固定速率| F[处理请求]
```

#### 配置示例

```yaml
model:
  services:
    embedding:
      rate-limit:
        enabled: true
        algorithm: "leaky-bucket"
        capacity: 50            # 桶容量
        rate: 5                # 漏出速率（每秒）
        scope: "service"
        client-ip-enable: true
```

#### JSON 配置

```json
{
  "services": {
    "embedding": {
      "rateLimit": {
        "type": "leaky-bucket",
        "capacity": 50,
        "leakRate": 5,
        "clientIpEnable": true
      }
    }
  }
}
```

#### 参数说明

| 参数 | 类型 | 说明 | 默认值 |
|------|------|------|--------|
| `capacity` | Integer | 桶容量，最大排队请求数 | 50 |
| `rate`/`leakRate` | Integer | 漏出速率（每秒） | 5 |

#### 适用场景

- **需要平稳输出**：要求后端接收到的请求速率平稳
- **消息队列**：需要控制消息处理速率
- **数据库保护**：保护数据库免受突发流量冲击

### 3. Sliding Window（滑动窗口算法）

#### 算法原理

滑动窗口算法在固定时间窗口内统计请求数量，超过阈值时拒绝请求。

```mermaid
graph LR
    A[时间窗口] --> B[统计请求数]
    C[新请求] --> D{窗口内请求数 < 阈值?}
    D -->|是| E[允许请求]
    D -->|否| F[拒绝请求]
    B --> D
```

#### 配置示例

```yaml
model:
  services:
    tts:
      rate-limit:
        enabled: true
        algorithm: "sliding-window"
        window-size: 60         # 时间窗口大小（秒）
        max-requests: 100       # 窗口内最大请求数
        scope: "service"
        client-ip-enable: true
```

#### JSON 配置

```json
{
  "services": {
    "tts": {
      "rateLimit": {
        "type": "sliding-window",
        "windowSize": 60,
        "maxRequests": 100,
        "clientIpEnable": true
      }
    }
  }
}
```

#### 参数说明

| 参数 | 类型 | 说明 | 默认值 |
|------|------|------|--------|
| `window-size`/`windowSize` | Integer | 时间窗口大小（秒） | 60 |
| `max-requests`/`maxRequests` | Integer | 窗口内最大请求数 | 100 |

#### 适用场景

- **精确流量控制**：需要精确控制时间窗口内的请求数量
- **API 配额管理**：按时间段分配 API 调用配额
- **防刷接口**：防止短时间内大量重复请求

### 4. Warm Up（预热算法）

#### 算法原理

预热算法在服务启动时逐渐增加允许的请求速率，避免冷启动时的性能问题。

```mermaid
graph LR
    A[服务启动] --> B[低速率]
    B --> C[逐渐增加]
    C --> D[达到目标速率]
    
    E[预热时间] --> F[当前允许速率]
    F --> G[= 目标速率 * (当前时间 / 预热时间)]
```

#### 配置示例

```yaml
model:
  services:
    chat:
      rate-limit:
        enabled: true
        algorithm: "warm-up"
        capacity: 100           # 最终容量
        rate: 10               # 最终速率
        warm-up-period: 300    # 预热时间（秒）
        cold-factor: 3         # 冷启动因子
        scope: "service"
```

#### JSON 配置

```json
{
  "services": {
    "chat": {
      "rateLimit": {
        "type": "warm-up",
        "capacity": 100,
        "warmUpPeriod": 300,
        "coldFactor": 3,
        "clientIpEnable": true
      }
    }
  }
}
```

#### 参数说明

| 参数 | 类型 | 说明 | 默认值 |
|------|------|------|--------|
| `capacity` | Integer | 最终容量 | 100 |
| `warm-up-period`/`warmUpPeriod` | Integer | 预热时间（秒） | 300 |
| `cold-factor`/`coldFactor` | Integer | 冷启动因子 | 3 |

#### 适用场景

- **服务冷启动**：服务刚启动时需要预热
- **缓存预热**：需要时间建立缓存
- **连接池预热**：数据库连接池需要时间建立

## 客户端 IP 限流

### 基础配置

```yaml
model:
  rate-limit:
    client-ip-enable: true      # 启用客户端IP限流
    
    # 客户端IP限流配置
    client-ip:
      enabled: true
      cleanup-interval: 300s    # 清理间隔
      max-idle-time: 1800s     # 最大空闲时间
      max-clients: 10000       # 最大客户端数量
      default-capacity: 100     # 默认容量
      default-rate: 10         # 默认速率
```

### 服务级别 IP 限流

```yaml
model:
  services:
    chat:
      rate-limit:
        enabled: true
        algorithm: "token-bucket"
        capacity: 1000          # 服务级别总容量
        rate: 100              # 服务级别总速率
        client-ip-enable: true  # 启用IP限流
        
        # 每个IP的限流配置
        client-ip:
          capacity: 50          # 每个IP的容量
          rate: 5              # 每个IP的速率
```

### 实例级别 IP 限流

```yaml
model:
  services:
    chat:
      instances:
        - name: "model-1"
          base-url: "http://server-1:8080"
          rate-limit:
            enabled: true
            algorithm: "token-bucket"
            capacity: 100
            rate: 10
            scope: "instance"
            client-ip-enable: true
```

### IP 限流监控

```bash
# 查看客户端IP限流统计
curl "http://localhost:8080/actuator/metrics/jairouter.ratelimit.clients"

# 查看IP限流器清理统计
curl "http://localhost:8080/actuator/metrics/jairouter.ratelimit.cleanup"
```

## 多层限流配置

### 分层限流策略

```yaml
model:
  # 全局限流：保护整个系统
  rate-limit:
    enabled: true
    algorithm: "token-bucket"
    capacity: 10000
    rate: 1000
    client-ip-enable: true
  
  services:
    chat:
      # 服务级限流：保护特定服务
      rate-limit:
        enabled: true
        algorithm: "token-bucket"
        capacity: 1000
        rate: 100
        client-ip-enable: true
        client-ip:
          capacity: 50
          rate: 5
      
      instances:
        - name: "high-perf-model"
          base-url: "http://gpu-server:8080"
          # 实例级限流：保护特定实例
          rate-limit:
            enabled: true
            algorithm: "token-bucket"
            capacity: 500
            rate: 50
            scope: "instance"
```

### 限流优先级

1. **实例级限流**：最高优先级，直接保护实例
2. **服务级限流**：中等优先级，保护服务类型
3. **全局限流**：最低优先级，保护整个系统

## 动态限流配置

### 通过配置文件动态调整

```json
{
  "services": {
    "chat": {
      "rateLimit": {
        "type": "token-bucket",
        "capacity": 200,
        "refillRate": 20,
        "clientIpEnable": true
      },
      "instances": [
        {
          "name": "model-1",
          "baseUrl": "http://server-1:8080",
          "rateLimit": {
            "type": "token-bucket",
            "capacity": 100,
            "refillRate": 10
          }
        }
      ]
    }
  }
}
```

### 限流参数调优

```bash
# 监控当前限流效果
curl "http://localhost:8080/actuator/metrics/jairouter.ratelimit.requests"

# 根据监控结果调整配置
# 如果拒绝率过高，增加容量或速率
# 如果后端压力大，减少容量或速率
```

## 限流监控和告警

### 监控指标

```bash
# 限流请求总数
curl "http://localhost:8080/actuator/metrics/jairouter.ratelimit.requests.total"

# 限流拒绝数
curl "http://localhost:8080/actuator/metrics/jairouter.ratelimit.rejected.total"

# 客户端IP数量
curl "http://localhost:8080/actuator/metrics/jairouter.ratelimit.clients.active"

# 限流器清理统计
curl "http://localhost:8080/actuator/metrics/jairouter.ratelimit.cleanup.total"
```

### Prometheus 指标

```prometheus
# 限流请求速率
rate(jairouter_ratelimit_requests_total[5m])

# 限流拒绝率
rate(jairouter_ratelimit_rejected_total[5m]) / rate(jairouter_ratelimit_requests_total[5m])

# 活跃客户端数量
jairouter_ratelimit_clients_active

# 限流器内存使用
jairouter_ratelimit_memory_usage_bytes
```

### 告警规则

```yaml
# Prometheus 告警规则
groups:
  - name: jairouter_ratelimit
    rules:
      - alert: HighRateLimitRejection
        expr: rate(jairouter_ratelimit_rejected_total[5m]) / rate(jairouter_ratelimit_requests_total[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "限流拒绝率过高"
          description: "服务 {{ $labels.service }} 的限流拒绝率超过 10%"
      
      - alert: TooManyActiveClients
        expr: jairouter_ratelimit_clients_active > 5000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "活跃客户端数量过多"
          description: "活跃客户端数量达到 {{ $value }}，可能需要调整清理策略"
```

## 性能优化

### 1. 限流器性能配置

```yaml
model:
  rate-limit:
    performance:
      async-processing: true    # 启用异步处理
      batch-size: 100          # 批量处理大小
      cache-size: 1000         # 缓存大小
      thread-pool-size: 4      # 线程池大小
```

### 2. 内存优化

```yaml
model:
  rate-limit:
    client-ip:
      cleanup-interval: 180s    # 更频繁的清理
      max-idle-time: 900s      # 更短的空闲时间
      max-clients: 5000        # 限制最大客户端数
      
      # 内存优化配置
      memory:
        initial-capacity: 1000  # 初始容量
        load-factor: 0.75      # 负载因子
        concurrency-level: 16   # 并发级别
```

### 3. 算法选择优化

```yaml
# 高并发场景：选择性能最好的算法
model:
  services:
    high-traffic:
      rate-limit:
        algorithm: "token-bucket"  # 性能最好
        
# 精确控制场景：选择精度最高的算法
model:
  services:
    precise-control:
      rate-limit:
        algorithm: "sliding-window"  # 精度最高
```

## 实际使用案例

### 案例 1：API 网关限流

```yaml
# 多层限流保护
model:
  # 全局限流：保护整个网关
  rate-limit:
    enabled: true
    algorithm: "token-bucket"
    capacity: 10000
    rate: 1000
    client-ip-enable: true
    client-ip:
      capacity: 100
      rate: 10
  
  services:
    # 聊天服务：高频使用
    chat:
      rate-limit:
        algorithm: "token-bucket"
        capacity: 5000
        rate: 500
        client-ip-enable: true
        client-ip:
          capacity: 50
          rate: 5
    
    # 图像生成：资源密集
    image-generation:
      rate-limit:
        algorithm: "leaky-bucket"
        capacity: 100
        rate: 10
        client-ip-enable: true
        client-ip:
          capacity: 5
          rate: 1
```

### 案例 2：防刷保护

```yaml
# 防止恶意刷接口
model:
  services:
    sensitive-api:
      rate-limit:
        enabled: true
        algorithm: "sliding-window"
        window-size: 300        # 5分钟窗口
        max-requests: 50        # 最多50次请求
        client-ip-enable: true
        client-ip:
          window-size: 60       # 1分钟窗口
          max-requests: 10      # 每个IP最多10次
```

### 案例 3：服务预热

```yaml
# 新服务上线预热
model:
  services:
    new-service:
      rate-limit:
        enabled: true
        algorithm: "warm-up"
        capacity: 1000
        rate: 100
        warm-up-period: 600     # 10分钟预热
        cold-factor: 5          # 初始速率为目标的1/5
```

## 故障排查

### 常见问题

1. **限流过于严格**
   ```bash
   # 检查拒绝率
   curl "http://localhost:8080/actuator/metrics/jairouter.ratelimit.rejected.total"
   
   # 解决：增加容量或速率
   ```

2. **内存使用过高**
   ```bash
   # 检查客户端数量
   curl "http://localhost:8080/actuator/metrics/jairouter.ratelimit.clients.active"
   
   # 解决：调整清理策略
   ```

3. **限流不生效**
   ```bash
   # 检查配置是否正确加载
   curl "http://localhost:8080/api/config/instance/type/chat"
   
   # 检查限流是否启用
   ```

### 调试命令

```bash
# 查看限流配置
curl "http://localhost:8080/actuator/configprops" | jq '.model.rate-limit'

# 查看限流指标
curl "http://localhost:8080/actuator/metrics" | grep ratelimit

# 测试限流效果
for i in {1..20}; do
  curl -w "%{http_code}\n" "http://localhost:8080/v1/chat/completions" \
    -H "Content-Type: application/json" \
    -d '{"model": "test", "messages": [{"role": "user", "content": "test"}]}'
done
```

## 下一步

完成限流配置后，您可以继续了解：

- **[熔断器配置](circuit-breaker.md)** - 配置故障保护机制
- **[监控指南](../monitoring/index.md)** - 设置限流监控和告警
- **[故障排查](../troubleshooting/index.md)** - 限流问题诊断和解决
- **[性能调优](../troubleshooting/performance.md)** - 限流性能优化