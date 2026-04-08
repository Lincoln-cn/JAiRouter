# JWT持久化调试增强总结

## 问题
用户报告 `/api/auth/jwt/tokens` 返回空数据，需要检查token颁发和移除操作是否正确持久化到H2数据库。

## 已完成的增强

### 1. 添加服务初始化日志

#### JwtTokenPersistenceServiceImpl
```java
@PostConstruct
public void init() {
    log.info("=== JwtTokenPersistenceServiceImpl initialized with StoreManager: {} ===", 
            storeManager.getClass().getSimpleName());
    log.info("JWT Token persistence is ENABLED and using H2 database storage");
}
```

#### JwtBlacklistServiceImpl
```java
@PostConstruct
public void init() {
    log.info("=== JwtBlacklistServiceImpl initialized with StoreManager: {} ===", 
            storeManager.getClass().getSimpleName());
    log.info("JWT Blacklist persistence is ENABLED and using H2 database storage");
}
```

#### JwtTokenController
```java
@PostConstruct
public void init() {
    log.info("=== JwtTokenController initialized ===");
    log.info("JwtPersistenceService available: {}", jwtPersistenceService != null);
    log.info("JwtCleanupService available: {}", jwtCleanupService != null);
    log.info("JwtBlacklistService available: {}", jwtBlacklistService != null);
    if (jwtPersistenceService != null) {
        log.info("JwtPersistenceService class: {}", jwtPersistenceService.getClass().getSimpleName());
    } else {
        log.warn("!!! JwtPersistenceService is NULL - Token persistence will NOT work !!!");
        log.warn("Check configuration: jairouter.security.jwt.persistence.enabled should be true");
    }
}
```

### 2. 增强Token颁发日志

在 `JwtTokenController.login()` 方法中：

```java
if (jwtPersistenceService != null) {
    log.info("保存令牌元数据到H2数据库: username={}, ip={}", request.getUsername(), clientIp);
    return tokenRefreshService.saveTokenMetadata(token, request.getUsername(), userAgent, clientIp, userAgent)
            .doOnSuccess(v -> log.info("✓ 令牌元数据已成功保存到H2数据库: username={}", request.getUsername()))
            .then(Mono.just(RouterResponse.success(response, "登录成功")))
            .onErrorResume(ex -> {
                log.error("✗ 保存令牌元数据失败: {}", ex.getMessage(), ex);
                return Mono.just(RouterResponse.success(response, "登录成功"));
            });
} else {
    log.warn("!!! JwtPersistenceService不可用，令牌未持久化 !!!");
    return Mono.just(RouterResponse.success(response, "登录成功"));
}
```

### 3. 增强Token撤销日志

在 `updateTokenStatusInPersistence()` 方法中：

```java
if (jwtPersistenceService == null) {
    log.warn("!!! JwtPersistenceService不可用，无法更新令牌状态到H2 !!!");
    return Mono.empty();
}

try {
    String tokenHash = calculateTokenHash(token);
    log.info("更新令牌状态到H2: tokenHash={}, status={}, reason={}", 
            tokenHash.substring(0, 10) + "...", status, reason);
    return jwtPersistenceService.findByTokenHash(tokenHash)
            .flatMap(tokenInfo -> {
                // ... 更新逻辑 ...
                return jwtPersistenceService.saveToken(tokenInfo);
            })
            .doOnSuccess(v -> log.info("✓ 令牌状态已更新到H2: status={}", status))
            .onErrorResume(ex -> {
                log.error("✗ 更新令牌持久化状态失败: {}", ex.getMessage(), ex);
                return Mono.empty();
            });
}
```

## 诊断流程

### 启动时检查

启动应用后，应该在日志中看到：

```
=== JwtTokenPersistenceServiceImpl initialized with StoreManager: H2StoreManager ===
JWT Token persistence is ENABLED and using H2 database storage

=== JwtBlacklistServiceImpl initialized with StoreManager: H2StoreManager ===
JWT Blacklist persistence is ENABLED and using H2 database storage

=== JwtTokenController initialized ===
JwtPersistenceService available: true
JwtCleanupService available: true
JwtBlacklistService available: true
JwtPersistenceService class: JwtTokenPersistenceServiceImpl
```

### 登录时检查

用户登录时，应该看到：

```
保存令牌元数据到H2数据库: username=admin, ip=127.0.0.1
✓ 令牌元数据已成功保存到H2数据库: username=admin
```

### 撤销时检查

撤销token时，应该看到：

```
更新令牌状态到H2: tokenHash=abc123..., status=REVOKED, reason=手动撤销
✓ 令牌状态已更新到H2: status=REVOKED
```

## 可能的问题和解决方案

### 问题1: 服务未初始化

**症状**:
```
JwtPersistenceService available: false
!!! JwtPersistenceService is NULL - Token persistence will NOT work !!!
```

**原因**: 
- `jairouter.security.jwt.persistence.enabled` 未设置为 `true`
- 配置文件未正确加载
- Profile未正确激活

**解决方案**:
1. 确认使用 `--spring.profiles.active=h2` 启动
2. 检查 `application-h2.yml` 中的配置
3. 查看配置加载日志

### 问题2: Token未保存

**症状**:
```
!!! JwtPersistenceService不可用，令牌未持久化 !!!
```

**原因**: 持久化服务未注入

**解决方案**: 参考问题1

### 问题3: 保存失败

**症状**:
```
✗ 保存令牌元数据失败: ...
```

**原因**: 
- 数据库连接失败
- 序列化错误
- 权限问题

**解决方案**:
1. 检查H2数据库文件权限
2. 检查磁盘空间
3. 查看详细错误堆栈

### 问题4: 查询返回空

**症状**: API返回空列表，但日志显示保存成功

**原因**:
- 查询逻辑错误
- 数据格式不匹配
- 索引问题

**解决方案**:
1. 直接查询H2数据库验证数据存在
2. 检查 `findAllTokens()` 方法的实现
3. 启用DEBUG日志查看详细查询过程

## 测试步骤

### 1. 重新编译
```bash
mvn clean package -DskipTests
```

### 2. 启动应用
```bash
java -jar target/model-router.jar --spring.profiles.active=h2
```

### 3. 检查初始化日志
```bash
# 查看服务初始化
tail -f logs/application.log | grep -E "initialized|available"
```

### 4. 测试登录
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}'
```

### 5. 检查保存日志
```bash
# 查看token保存日志
tail -f logs/application.log | grep -E "保存令牌|令牌元数据"
```

### 6. 查询token列表
```bash
curl -X GET "http://localhost:8080/api/auth/jwt/tokens?page=0&size=20" \
  -H "Authorization: Bearer <token>"
```

### 7. 验证H2数据库
访问 http://localhost:8080/h2-console

```sql
SELECT * FROM config WHERE config_key LIKE 'jwt_token_%' AND is_latest = true;
```

## 日志级别配置

如果需要更详细的日志，可以在 `application-h2.yml` 中添加：

```yaml
logging:
  level:
    org.unreal.modelrouter.security: DEBUG
    org.unreal.modelrouter.store: DEBUG
    org.unreal.modelrouter.controller.JwtTokenController: DEBUG
```

## 相关文件

- [JWT_PERSISTENCE_FIX.md](./JWT_PERSISTENCE_FIX.md) - 原始修复文档
- [JWT_PERSISTENCE_DIAGNOSTIC.md](./JWT_PERSISTENCE_DIAGNOSTIC.md) - 详细诊断指南
- [H2_JWT_STORAGE_VERIFICATION.md](./H2_JWT_STORAGE_VERIFICATION.md) - 验证指南
- [H2_JWT_CONFIGURATION_SUMMARY.md](./H2_JWT_CONFIGURATION_SUMMARY.md) - 配置总结

## 下一步

1. ✅ 重新编译应用
2. ✅ 启动应用并检查初始化日志
3. ✅ 执行登录测试
4. ✅ 检查token保存日志
5. ✅ 验证token查询
6. ✅ 检查H2数据库内容

如果所有日志都正常但仍有问题，请提供：
- 完整的启动日志
- 登录和查询的日志
- H2数据库查询结果截图
