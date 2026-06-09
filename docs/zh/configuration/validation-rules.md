# 配置验证规则文档

> 版本：v2.6.11
> 最后更新：2026-06-09

---

## 概述

JAiRouter v1.8.0+ 引入了启动时配置验证机制，自动检测关键配置项的合理性，帮助管理员在应用启动前发现配置问题。

---

## 验证规则列表

### 1. 限流容量 (rate-limit-capacity)

**配置项**: `RATE_LIMIT_CAPACITY` (环境变量)

**验证规则**:
| 规则类型 | 最小值 | 最大值 | 说明 |
|----------|--------|--------|------|
| 错误 | 1 | - | 限流容量必须大于 0 |
| 警告 | - | 100000 | 限流容量过大可能导致内存压力 |

**推荐值**: 100 - 10000

**示例**:
```bash
# 正确配置
export RATE_LIMIT_CAPACITY=1000

# 错误配置（会报错）
export RATE_LIMIT_CAPACITY=0

# 警告配置（会告警）
export RATE_LIMIT_CAPACITY=200000
```

---

### 2. 限流速率 (rate-limit-rate)

**配置项**: `RATE_LIMIT_RATE` (环境变量)

**验证规则**:
| 规则类型 | 最小值 | 最大值 | 说明 |
|----------|--------|--------|------|
| 错误 | 1 | - | 限流速率必须大于 0 |
| 警告 | - | 10000 | 限流速率过大可能导致系统过载 |

**推荐值**: 10 - 1000 (请求/秒)

**示例**:
```bash
# 正确配置
export RATE_LIMIT_RATE=100

# 错误配置
export RATE_LIMIT_RATE=0
```

---

### 3. 熔断器失败阈值 (circuit-breaker-threshold)

**配置项**: `CIRCUIT_BREAKER_FAILURE_THRESHOLD` (环境变量)

**验证规则**:
| 规则类型 | 最小值 | 最大值 | 说明 |
|----------|--------|--------|------|
| 错误 | 1 | - | 熔断器失败阈值必须大于 0 |
| 警告 | - | 100 | 阈值过大可能无法及时熔断 |

**推荐值**: 5 - 20

**示例**:
```bash
# 正确配置
export CIRCUIT_BREAKER_FAILURE_THRESHOLD=5

# 警告配置
export CIRCUIT_BREAKER_FAILURE_THRESHOLD=150
```

---

### 4. 熔断器超时时间 (circuit-breaker-timeout)

**配置项**: `CIRCUIT_BREAKER_TIMEOUT` (环境变量，单位：毫秒)

**验证规则**:
| 规则类型 | 最小值 | 最大值 | 说明 |
|----------|--------|--------|------|
| 错误 | 1000 | - | 熔断器超时不能小于 1 秒 |
| 警告 | - | 300000 | 超时过长（>5 分钟） |

**推荐值**: 30000 - 120000 (30 秒 -2 分钟)

**示例**:
```bash
# 正确配置
export CIRCUIT_BREAKER_TIMEOUT=60000

# 错误配置
export CIRCUIT_BREAKER_TIMEOUT=500

# 警告配置
export CIRCUIT_BREAKER_TIMEOUT=400000
```

---

### 5. JWT 过期时间 (jwt-expiration)

**配置项**: `JWT_EXPIRATION_MINUTES` (环境变量，单位：分钟)

**验证规则**:
| 规则类型 | 最小值 | 最大值 | 说明 |
|----------|--------|--------|------|
| 错误 | 1 | - | JWT 过期时间必须大于 0 分钟 |
| 警告 | - | 1440 | 过期时间过长（>24 小时） |

**推荐值**: 60 - 480 (1 小时 -8 小时)

**示例**:
```bash
# 正确配置
export JWT_EXPIRATION_MINUTES=120

# 警告配置
export JWT_EXPIRATION_MINUTES=2000
```

---

### 6. 服务器端口 (server-port)

**配置项**: `SERVER_PORT` (环境变量)

**验证规则**:
| 规则类型 | 最小值 | 最大值 | 说明 |
|----------|--------|--------|------|
| 错误 | 1 | 65535 | 端口必须在有效范围内 |
| 警告 | - | 1023 | 端口小于 1024 可能需要 root 权限 |

**推荐值**: 8080, 8443, 3000 等高位端口

**示例**:
```bash
# 正确配置
export SERVER_PORT=8080

# 错误配置
export SERVER_PORT=70000

# 警告配置（需要 root）
export SERVER_PORT=80
```

---

### 7. 线程池大小 (thread-pool-size)

**配置项**: `SERVER_TOMCAT_THREADS_MAX` (环境变量)

**验证规则**:
| 规则类型 | 最小值 | 最大值 | 说明 |
|----------|--------|--------|------|
| 错误 | 1 | - | 线程池大小必须大于 0 |
| 警告 | - | 500 | 线程池过大可能导致资源浪费 |

**推荐值**: 50 - 200

**示例**:
```bash
# 正确配置
export SERVER_TOMCAT_THREADS_MAX=100

# 警告配置
export SERVER_TOMCAT_THREADS_MAX=600
```

---

## 验证级别说明

### ✅ OK (通过)
- 配置在合理范围内
- 不会输出日志（DEBUG 级别除外）
- 应用正常启动

### ⚠️ WARN (警告)
- 配置可能不是最优，但可以接受
- 输出警告日志
- **不阻止应用启动**
- 建议根据提示优化配置

### ❌ ERROR (错误)
- 配置存在明显问题
- 输出错误日志
- **不阻止应用启动**，但强烈建议修正
- 应用可能运行时出现问题

---

## 验证输出示例

### 全部通过
```
╔══════════════════════════════════════════════════════════════════════════════╗
║                            JAiRouter 配置验证                                ║
╚══════════════════════════════════════════════════════════════════════════════╝

✓ [rate-limit-capacity] 限流容量 - 通过
✓ [rate-limit-rate] 限流速率 - 通过
✓ [circuit-breaker-threshold] 熔断器失败阈值 - 通过
✓ [circuit-breaker-timeout] 熔断器超时时间 - 通过
✓ [jwt-expiration] JWT 过期时间 - 通过
✓ [server-port] 服务器端口 - 通过
✓ [thread-pool-size] 线程池大小 - 通过

╔══════════════════════════════════════════════════════════════════════════════╗
║  配置验证汇总                                                                ║
║                                                                              ║
║  总规则数：7                                                                 ║
║  通过：7                                                                     ║
║  警告：0                                                                     ║
║  错误：0                                                                     ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### 有警告
```
⚠️  [rate-limit-capacity] 限流容量 - 限流容量过大，可能导致内存压力
⚠️  [circuit-breaker-threshold] 熔断器失败阈值 - 熔断器失败阈值过大，可能无法及时熔断

╔══════════════════════════════════════════════════════════════════════════════╗
║  配置验证汇总                                                                ║
║                                                                              ║
║  总规则数：7                                                                 ║
║  通过：7                                                                     ║
║  警告：2                                                                     ║
║  错误：0                                                                     ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### 有错误
```
❌ [rate-limit-rate] 限流速率 - 限流速率必须大于 0
❌ [jwt-expiration] JWT 过期时间 - JWT 过期时间必须大于 0 分钟

╔══════════════════════════════════════════════════════════════════════════════╗
║  配置验证汇总                                                                ║
║                                                                              ║
║  总规则数：7                                                                 ║
║  通过：5                                                                     ║
║  警告：0                                                                     ║
║  错误：2                                                                     ║
╚══════════════════════════════════════════════════════════════════════════════╝

⚠️  检测到 2 个配置错误，请检查并修正配置后重启应用
```

---

## 启用/禁用验证

### 启用（默认）
验证功能默认启用，无需额外配置。

### 禁用
如需禁用验证，可设置以下配置：

```yaml
# application.yml
jairouter:
  config:
    validation:
      enabled: false
```

或使用环境变量：
```bash
export JAiROUTER_CONFIG_VALIDATION_ENABLED=false
```

---

## 自定义验证规则

开发者可以通过实现 `ConfigurationValidationRule` 接口添加自定义验证规则。

### 示例代码

```java
@Component
public class CustomConfigurationValidator {

    @EventListener(ApplicationReadyEvent.class)
    public void validateCustomConfig() {
        // 添加自定义验证逻辑
        String customConfig = System.getenv("CUSTOM_CONFIG");
        if (customConfig != null) {
            // 验证自定义配置
            if (!isValid(customConfig)) {
                log.error("自定义配置无效");
            }
        }
    }
    
    private boolean isValid(String config) {
        // 实现验证逻辑
        return true;
    }
}
```

---

## 最佳实践

### 1. 开发环境
```bash
export RATE_LIMIT_CAPACITY=100
export RATE_LIMIT_RATE=10
export CIRCUIT_BREAKER_FAILURE_THRESHOLD=3
export CIRCUIT_BREAKER_TIMEOUT=30000
export JWT_EXPIRATION_MINUTES=60
export SERVER_PORT=8080
export SERVER_TOMCAT_THREADS_MAX=50
```

### 2. 生产环境
```bash
export RATE_LIMIT_CAPACITY=10000
export RATE_LIMIT_RATE=500
export CIRCUIT_BREAKER_FAILURE_THRESHOLD=10
export CIRCUIT_BREAKER_TIMEOUT=60000
export JWT_EXPIRATION_MINUTES=120
export SERVER_PORT=8080
export SERVER_TOMCAT_THREADS_MAX=200
```

### 3. 高性能环境
```bash
export RATE_LIMIT_CAPACITY=50000
export RATE_LIMIT_RATE=2000
export CIRCUIT_BREAKER_FAILURE_THRESHOLD=15
export CIRCUIT_BREAKER_TIMEOUT=90000
export JWT_EXPIRATION_MINUTES=240
export SERVER_PORT=8080
export SERVER_TOMCAT_THREADS_MAX=400
```

---

## 故障排查

### 问题：验证规则未生效

**检查项**:
1. 确认 `jairouter.config.validation.enabled` 为 `true`（默认值）
2. 检查日志中是否有 "JAiRouter 配置验证" 输出
3. 确认环境变量已正确设置

### 问题：误报警告

**解决方法**:
1. 检查配置值是否确实合理
2. 如确认配置无误，可通过调整警告阈值解决
3. 或忽略警告（不影响启动）

### 问题：配置错误但未阻止启动

**说明**: 
配置验证的目的是**提前发现问题**，而不是阻止启动。即使有错误，应用仍会启动，但可能运行时出现问题。

**建议**:
- 看到错误日志后立即修正配置
- 在 CI/CD 流程中加入配置检查
- 使用 Docker 环境变量强制注入正确配置

---

## 相关文件

- [应用配置](application-config.md) - 应用配置文件说明

---

**文档版本**: v1.0  
**最后更新**: 2026 年 4 月 16 日
