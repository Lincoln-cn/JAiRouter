# 安全功能故障排除指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**:   
> **作者**: 
<!-- /版本信息 -->



## 概述

本文档提供了 JAiRouter 安全功能常见问题的诊断和解决方案，包括认证失败、脱敏问题、性能问题等。

## 快速诊断

### 1. 检查安全功能状态

```bash
# 检查系统健康状态
curl http://localhost:8080/actuator/health

# 检查安全配置
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/status
```

### 2. 查看日志

```bash
# 查看应用日志
tail -f logs/jairouter.log

# 查看安全审计日志
tail -f logs/security-audit.log

# 查看错误日志
grep ERROR logs/jairouter.log
```

### 3. 检查监控指标

```bash
# 查看 Prometheus 指标
curl http://localhost:8080/actuator/prometheus | grep security

# 查看认证指标
curl http://localhost:8080/actuator/prometheus | grep authentication

# 查看脱敏指标
curl http://localhost:8080/actuator/prometheus | grep sanitization
```

## 认证问题

### 问题 1：API Key 认证失败

#### 症状
- 客户端收到 `401 Unauthorized` 错误
- 日志显示 `Invalid API Key` 或 `API Key not found`

#### 可能原因
1. API Key 不正确或不存在
2. API Key 已过期
3. API Key 已被禁用
4. 请求头名称不正确
5. API Key 格式错误

#### 诊断步骤

```bash
# 1. 检查 API Key 配置
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/api-keys

# 2. 验证请求头
curl -v -H "X-API-Key: your-api-key" \
     http://localhost:8080/v1/models

# 3. 检查 API Key 状态
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/api-keys/your-key-id/status
```

#### 解决方案

1. **检查 API Key 值**
```yaml
# 确保配置正确
jairouter:
  security:
    api-key:
      keys:
        - key-id: "test-key"
          key-value: "correct-api-key-value"
          enabled: true
```

2. **检查过期时间**
```bash
# 更新过期时间
curl -X PUT -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"expires_at": "2025-12-31T23:59:59"}' \
     http://localhost:8080/admin/security/api-keys/your-key-id
```

3. **启用 API Key**
```bash
curl -X PUT -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"enabled": true}' \
     http://localhost:8080/admin/security/api-keys/your-key-id
```

### 问题 2：JWT 认证失败

#### 症状
- 客户端收到 `401 Unauthorized` 错误
- 日志显示 `Invalid JWT token` 或 `JWT signature verification failed`

#### 可能原因
1. JWT 令牌格式不正确
2. 签名验证失败
3. 令牌已过期
4. 令牌在黑名单中
5. 密钥配置错误

#### 诊断步骤

```bash
# 1. 解析 JWT 令牌
echo "your-jwt-token" | cut -d'.' -f2 | base64 -d | jq

# 2. 检查令牌状态
curl -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"token": "your-jwt-token"}' \
     http://localhost:8080/admin/security/jwt/validate

# 3. 检查黑名单
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/jwt/blacklist
```

#### 解决方案

1. **检查 JWT 配置**
```yaml
jairouter:
  security:
    jwt:
      enabled: true
      secret: "${JWT_SECRET}"  # 确保密钥正确
      algorithm: "HS256"
```

2. **刷新令牌**
```bash
curl -X POST -H "Content-Type: application/json" \
     -d '{"refresh_token": "your-refresh-token"}' \
     http://localhost:8080/auth/refresh
```

3. **清除黑名单**
```bash
curl -X DELETE -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/jwt/blacklist/clear
```

### 问题 3：权限不足

#### 症状
- 客户端收到 `403 Forbidden` 错误
- 日志显示 `Insufficient permissions`

#### 诊断步骤

```bash
# 检查用户权限
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/permissions/your-user-id

# 检查接口权限要求
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/endpoints
```

#### 解决方案

```bash
# 更新用户权限
curl -X PUT -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"permissions": ["read", "write", "admin"]}' \
     http://localhost:8080/admin/security/api-keys/your-key-id/permissions
```

## 数据脱敏问题

### 问题 4：脱敏不生效

#### 症状
- 敏感数据未被脱敏
- 日志显示脱敏规则未匹配

#### 可能原因
1. 脱敏功能未启用
2. 正则表达式不匹配
3. 用户在白名单中
4. 规则优先级问题

#### 诊断步骤

```bash
# 1. 检查脱敏配置
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/sanitization/config

# 2. 测试脱敏规则
curl -X POST -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"text": "我的手机号是13812345678", "rules": ["phone"]}' \
     http://localhost:8080/admin/security/sanitization/test

# 3. 检查白名单
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/sanitization/whitelist
```

#### 解决方案

1. **启用脱敏功能**
```yaml
jairouter:
  security:
    sanitization:
      request:
        enabled: true
      response:
        enabled: true
```

2. **修正正则表达式**
```yaml
jairouter:
  security:
    sanitization:
      request:
        pii-patterns:
          - "\\b(?:13[0-9]|14[5-9]|15[0-3,5-9]|16[2,5,6,7]|17[0-8]|18[0-9]|19[1,3,5,8,9])\\d{8}\\b"
```

3. **检查白名单**
```bash
# 从白名单中移除用户
curl -X DELETE -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/sanitization/whitelist/users/your-user-id
```

### 问题 5：误脱敏

#### 症状
- 正常数据被错误脱敏
- 业务功能受到影响

#### 诊断步骤

```bash
# 测试具体文本的脱敏结果
curl -X POST -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"text": "your-test-text"}' \
     http://localhost:8080/admin/security/sanitization/test
```

#### 解决方案

1. **精确化正则表达式**
```yaml
# 修改前：过于宽泛
pii-patterns:
  - "\\d+"  # 匹配所有数字

# 修改后：精确匹配
pii-patterns:
  - "\\b1[3-9]\\d{9}\\b"  # 只匹配手机号
```

2. **调整规则优先级**
```yaml
jairouter:
  security:
    sanitization:
      request:
        rule-priorities:
          specific-pattern: 1    # 高优先级
          general-pattern: 10    # 低优先级
```

3. **添加例外规则**
```yaml
jairouter:
  security:
    sanitization:
      request:
        exception-patterns:
          - "订单号:\\d+"  # 订单号不脱敏
```

## 性能问题

### 问题 6：认证响应慢

#### 症状
- 认证耗时过长
- 系统响应缓慢

#### 诊断步骤

```bash
# 检查认证性能指标
curl http://localhost:8080/actuator/prometheus | grep authentication_duration

# 检查缓存命中率
curl http://localhost:8080/actuator/prometheus | grep cache_hit_rate

# 检查线程池状态
curl http://localhost:8080/actuator/prometheus | grep thread_pool
```

#### 解决方案

1. **启用缓存**
```yaml
jairouter:
  security:
    performance:
      cache:
        redis:
          enabled: true
        local:
          enabled: true
```

2. **优化线程池**
```yaml
jairouter:
  security:
    performance:
      authentication:
        async-enabled: true
        thread-pool-size: 20
        timeout-ms: 3000
```

3. **减少 API Key 数量**
```bash
# 清理无用的 API Key
curl -X DELETE -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/api-keys/unused-key-id
```

### 问题 7：脱敏性能问题

#### 症状
- 脱敏操作耗时过长
- 内存使用过高

#### 诊断步骤

```bash
# 检查脱敏性能指标
curl http://localhost:8080/actuator/prometheus | grep sanitization_duration

# 检查内存使用
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# 检查正则表达式缓存
curl http://localhost:8080/actuator/prometheus | grep regex_cache
```

#### 解决方案

1. **启用并行处理**
```yaml
jairouter:
  security:
    performance:
      sanitization:
        parallel-enabled: true
        thread-pool-size: 8
```

2. **优化正则表达式**
```yaml
# 避免复杂的正则表达式
pii-patterns:
  - "\\d{11}"  # 简单模式
  # 避免：(?=.*\\d)(?=.*[a-z])(?=.*[A-Z]).{8,}  # 复杂模式
```

3. **启用流式处理**
```yaml
jairouter:
  security:
    performance:
      sanitization:
        streaming-threshold: 1048576  # 1MB
```

## 配置问题

### 问题 8：配置不生效

#### 症状
- 修改配置后功能未变化
- 系统使用默认配置

#### 诊断步骤

```bash
# 检查当前配置
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/config/current

# 检查配置文件
cat src/main/resources/application.yml | grep -A 20 security

# 检查环境变量
env | grep -i security
```

#### 解决方案

1. **重启应用**
```bash
# 某些配置需要重启才能生效
./mvnw spring-boot:stop
./mvnw spring-boot:run
```

2. **检查配置优先级**
```yaml
# 确保配置在正确的环境文件中
# application-prod.yml 优先级高于 application.yml
```

3. **验证 YAML 格式**
```bash
# 使用 YAML 验证工具
yamllint src/main/resources/application.yml
```

### 问题 9：环境变量未生效

#### 症状
- 环境变量值未被读取
- 使用了默认值而非环境变量值

#### 解决方案

1. **检查环境变量格式**
```bash
# 正确格式
export JWT_SECRET="your-secret-here"

# 错误格式
export jwt_secret="your-secret-here"  # 大小写错误
```

2. **检查配置引用**
```yaml
# 正确引用
secret: "${JWT_SECRET}"

# 错误引用
secret: "$JWT_SECRET"  # 缺少大括号
```

3. **重新加载环境变量**
```bash
source ~/.bashrc
# 或
source ~/.profile
```

## 监控和告警问题

### 问题 10：监控指标缺失

#### 症状
- Prometheus 指标不显示
- 监控面板无数据

#### 解决方案

1. **启用监控功能**
```yaml
jairouter:
  security:
    monitoring:
      enabled: true
      metrics:
        authentication:
          enabled: true
        sanitization:
          enabled: true
```

2. **检查 Actuator 配置**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

3. **验证指标端点**
```bash
curl http://localhost:8080/actuator/prometheus
```

### 问题 11：告警不触发

#### 症状
- 达到阈值但未收到告警
- 告警配置不生效

#### 解决方案

1. **检查告警配置**
```yaml
jairouter:
  security:
    monitoring:
      alerts:
        enabled: true
        thresholds:
          authentication-failure-rate: 0.1
```

2. **测试告警通知**
```bash
curl -X POST -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/alerts/test
```

3. **检查通知配置**
```yaml
jairouter:
  security:
    monitoring:
      alerts:
        notifications:
          email:
            enabled: true
            recipients: ["admin@example.com"]
```

## 调试工具和技巧

### 1. 启用详细日志

```yaml
logging:
  level:
    org.unreal.modelrouter.security: DEBUG
    org.springframework.security: DEBUG
    org.springframework.web: DEBUG
```

### 2. 使用调试端点

```bash
# 安全状态检查
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/debug

# 配置检查
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/config/validate

# 性能分析
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/performance/analyze
```

### 3. 日志分析脚本

```bash
#!/bin/bash
# security-log-analyzer.sh

LOG_FILE="logs/jairouter.log"
AUDIT_FILE="logs/security-audit.log"

echo "=== 认证失败统计 ==="
grep "authentication failed" $LOG_FILE | wc -l

echo "=== 最近的认证错误 ==="
grep "authentication failed" $LOG_FILE | tail -10

echo "=== 脱敏操作统计 ==="
grep "sanitization applied" $AUDIT_FILE | wc -l

echo "=== 性能问题检查 ==="
grep "timeout\|slow\|performance" $LOG_FILE | tail -10
```

### 4. 健康检查脚本

```bash
#!/bin/bash
# security-health-check.sh

BASE_URL="http://localhost:8080"
ADMIN_TOKEN="your-admin-token"

echo "=== 系统健康检查 ==="
curl -s "$BASE_URL/actuator/health" | jq '.status'

echo "=== 安全功能状态 ==="
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
     "$BASE_URL/admin/security/status" | jq

echo "=== 认证性能指标 ==="
curl -s "$BASE_URL/actuator/prometheus" | \
     grep "jairouter_security_authentication_duration_seconds"

echo "=== 脱敏性能指标 ==="
curl -s "$BASE_URL/actuator/prometheus" | \
     grep "jairouter_security_sanitization_duration_seconds"
```

## 常用命令参考

### 配置管理

```bash
# 重新加载配置
curl -X POST -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/config/reload

# 验证配置
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/config/validate

# 备份配置
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/config/backup > config-backup.json
```

### 缓存管理

```bash
# 清除认证缓存
curl -X DELETE -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/cache/authentication

# 清除脱敏缓存
curl -X DELETE -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/cache/sanitization

# 查看缓存统计
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/cache/stats
```

### 日志管理

```bash
# 设置日志级别
curl -X POST -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"level": "DEBUG"}' \
     http://localhost:8080/admin/logging/org.unreal.modelrouter.security

# 下载日志
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/logs/security-audit.log > audit.log
```

## 联系支持

如果以上解决方案无法解决您的问题，请联系技术支持：

- **GitHub Issues**: https://github.com/Lincoln-cn/JAiRouter/issues
- **邮箱支持**: support@jairouter.com
- **文档中心**: https://docs.jairouter.com

提交问题时，请包含以下信息：
1. 问题详细描述
2. 错误日志
3. 配置文件（脱敏后）
4. 系统环境信息
5. 复现步骤

## 相关文档

- [API Key 管理指南](api-key-management.md)
- [JWT 认证配置说明](jwt-authentication.md)
- [数据脱敏规则配置](data-sanitization.md)
- [安全监控和告警](../monitoring/alerts.md)