# JWT持久化Reactor响应式修复

## 问题诊断

通过日志分析发现问题：

```
✗ 保存令牌元数据失败: Failed to save token
java.lang.RuntimeException: Failed to save token
    at org.unreal.modelrouter.security.service.impl.JwtTokenPersistenceServiceImpl.lambda$saveToken$0
```

## 根本原因

在响应式（Reactor）环境中，`JwtTokenPersistenceServiceImpl.saveToken()` 方法使用了 `Mono.fromRunnable()`，这会在当前线程中同步执行阻塞操作。

当 `storeManager.saveConfig()` 调用 H2StoreManager 时，内部使用了 `.block()` 方法：

```java
configRepository.save(entity).block();  // ← 在响应式流中阻塞！
```

在响应式流的上下文中调用 `.block()` 会导致：
1. 线程阻塞
2. 可能的死锁
3. 操作失败并抛出异常

## 解决方案

### 修改1: saveToken() 方法

**之前**:
```java
public Mono<Void> saveToken(JwtTokenInfo tokenInfo) {
    return Mono.fromRunnable(() -> {
        // 同步阻塞操作
        storeManager.saveConfig(tokenKey, tokenData);
    });
}
```

**之后**:
```java
public Mono<Void> saveToken(JwtTokenInfo tokenInfo) {
    return Mono.defer(() -> {
        // ...准备数据...
        
        return Mono.fromCallable(() -> {
            storeManager.saveConfig(tokenKey, tokenData);
            // ...其他操作...
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())  // ← 在弹性线程池中执行
        .then();
    });
}
```

### 修改2: findByTokenHash() 方法

添加 `.subscribeOn(Schedulers.boundedElastic())` 确保在适当的线程池中执行：

```java
public Mono<JwtTokenInfo> findByTokenHash(String tokenHash) {
    return Mono.fromCallable(() -> {
        // ...查询逻辑...
    })
    .subscribeOn(Schedulers.boundedElastic());  // ← 添加
}
```

### 修改3: findAllTokens() 方法

同样添加线程池调度：

```java
public Mono<List<JwtTokenInfo>> findAllTokens(int page, int size) {
    return Mono.fromCallable(() -> {
        // ...查询逻辑...
    })
    .subscribeOn(Schedulers.boundedElastic());  // ← 添加
}
```

## 为什么使用 boundedElastic

`Schedulers.boundedElastic()` 是专门为阻塞I/O操作设计的线程池：

- **弹性**: 根据需要创建线程
- **有界**: 有最大线程数限制，防止资源耗尽
- **适合阻塞操作**: 专门用于数据库访问、文件I/O等阻塞操作
- **不会阻塞响应式流**: 不会影响主要的响应式处理线程

## 测试验证

### 1. 重新编译
```bash
mvn clean package -DskipTests
```

### 2. 重启应用
```bash
java -jar target/model-router.jar --spring.profiles.active=h2
```

### 3. 测试登录
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}'
```

### 4. 检查日志
应该看到：
```
保存令牌元数据到H2数据库: username=admin, ip=...
✓ 令牌元数据已成功保存到H2数据库: username=admin
```

### 5. 查询令牌列表
```bash
curl -X GET "http://localhost:8080/api/auth/jwt/tokens?page=0&size=20" \
  -H "Authorization: Bearer <token>"
```

应该返回令牌数据，不再是空列表。

## 技术说明

### Reactor调度器类型

| 调度器 | 用途 | 特点 |
|--------|------|------|
| `immediate()` | 当前线程 | 无调度 |
| `single()` | 单线程 | 复用单个线程 |
| `parallel()` | CPU密集型 | 固定大小线程池 |
| `boundedElastic()` | 阻塞I/O | 弹性有界线程池 ✓ |

### 最佳实践

1. **避免在响应式流中使用 `.block()`**
   - 会导致死锁或性能问题
   - 使用 `.subscribeOn()` 代替

2. **选择合适的调度器**
   - CPU密集型: `parallel()`
   - 阻塞I/O: `boundedElastic()`
   - 快速操作: `immediate()`

3. **使用 `Mono.defer()` 延迟执行**
   - 确保每次订阅都创建新的执行流
   - 避免共享状态问题

## 相关修改文件

- `src/main/java/org/unreal/modelrouter/security/service/impl/JwtTokenPersistenceServiceImpl.java`
  - `saveToken()` - 修复阻塞问题
  - `findByTokenHash()` - 添加线程池调度
  - `findAllTokens()` - 添加线程池调度

## 预期结果

修复后：
- ✅ Token成功保存到H2数据库
- ✅ 登录后可以查询到令牌列表
- ✅ 不再出现 "Failed to save token" 错误
- ✅ 响应式流不会被阻塞
- ✅ 性能更好，无死锁风险

## 后续优化建议

如果需要进一步优化性能，可以考虑：

1. **使用R2DBC响应式数据库驱动**
   - 完全非阻塞的数据库访问
   - 需要重写H2StoreManager

2. **实现缓存层**
   - 减少数据库访问
   - 使用Caffeine或Redis

3. **批量操作优化**
   - 批量保存token
   - 减少数据库往返次数
