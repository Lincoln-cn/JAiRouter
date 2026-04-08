# JWT账号持久化问题修复

## 问题描述

用户报告：账号创建页面提示成功，但是没有正确保存，并且列表刷新后也没有新创建的账号。

## 根本原因

JWT账号管理系统存在以下架构问题：

1. **双存储架构不一致**：
   - 配置存储：JWT账号配置保存在 `config_data` 表中（通过 `StoreManager`）
   - 实体存储：JWT账号实体应该保存在 `jwt_accounts` 表中（通过 `JwtAccountRepository`）
   - **问题**：`JwtAccountService` 只更新了配置存储，没有同步更新实体存储

2. **数据读取源不一致**：
   - 创建/更新操作：只写入配置存储和内存
   - 查询操作：从内存读取（`jwtUserProperties.getAccounts()`）
   - **问题**：应用重启或内存刷新后，数据丢失

3. **缺少启动初始化**：
   - 应用启动时没有从数据库加载JWT账号配置到内存
   - **问题**：重启后无法恢复之前创建的账号

## 修复方案

### 1. 创建JWT账号初始化器

**文件**: `src/main/java/org/unreal/modelrouter/security/config/JwtAccountInitializer.java`

```java
@Component
@DependsOn("h2StoreManager")
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true")
public class JwtAccountInitializer {
    
    @PostConstruct
    public void initJwtAccounts() {
        if (jwtAccountService.hasPersistedAccountConfig()) {
            // 加载持久化配置
            jwtAccountService.loadLatestJwtAccountConfig();
        } else {
            // 首次启动，保存YAML配置
            jwtAccountService.initializeJwtAccountFromYaml();
        }
    }
}
```

**作用**：应用启动时自动加载JWT账号配置到内存

### 2. 修改JwtAccountService实现双写

**修改内容**：

#### 2.1 添加依赖注入
```java
private final JwtAccountRepository jwtAccountRepository;
private final ObjectMapper objectMapper;
```

#### 2.2 修改createAccount方法
```java
public Mono<Void> createAccount(JwtUserProperties.UserAccount account) {
    // 1. 保存到 jwt_accounts 表
    JwtAccountEntity entity = JwtAccountEntity.builder()...
    jwtAccountRepository.save(entity).block();
    
    // 2. 更新配置存储
    saveAccountAsNewVersion(currentConfig);
    
    // 3. 更新内存配置
    refreshAccountRuntimeConfig(currentConfig);
    
    // 4. 发布变更事件
    publishAccountChangeEvent(...);
}
```

#### 2.3 修改getAllAccounts方法
```java
public Mono<List<JwtUserProperties.UserAccount>> getAllAccounts() {
    // 从数据库表读取，而不是从内存
    return jwtAccountRepository.findAll()
        .map(entity -> convertToAccount(entity))
        .collectList();
}
```

#### 2.4 修改其他CRUD方法
- `updateAccount`: 同时更新数据库表和配置存储
- `deleteAccount`: 同时删除数据库表和配置存储
- `setAccountEnabled`: 同时更新数据库表和配置存储
- `batchUpdateAccounts`: 同时更新数据库表和配置存储

### 3. 修改loadLatestJwtAccountConfig方法

```java
public void loadLatestJwtAccountConfig() {
    int currentVersion = getCurrentAccountVersion();
    if (currentVersion > 0) {
        Map<String, Object> config = getAccountVersionConfig(currentVersion);
        if (config != null) {
            refreshAccountRuntimeConfig(config);
            log.info("已将JWT账户配置版本 {} 应用到运行时", currentVersion);
        }
    }
}
```

**作用**：真正加载并应用持久化配置到内存

## 修改的文件

1. ✅ `src/main/java/org/unreal/modelrouter/security/config/JwtAccountInitializer.java` (新建)
2. ✅ `src/main/java/org/unreal/modelrouter/security/service/JwtAccountService.java` (修改)

## 测试验证

使用测试脚本验证修复：

```bash
./test_jwt_account_fix.sh
```

测试步骤：
1. 登录获取管理员token
2. 获取创建前的账号列表
3. 创建新账号 'testuser'
4. 获取创建后的账号列表
5. 验证新账号是否存在
6. 验证账号数量是否增加
7. 测试新账号登录
8. 清理：删除测试账号

## 预期结果

- ✅ 账号创建成功后，立即在列表中可见
- ✅ 账号数据持久化到数据库表
- ✅ 应用重启后，账号数据不丢失
- ✅ 新创建的账号可以正常登录

## 技术细节

### 数据流向

**创建账号**:
```
API请求 → JwtAccountService.createAccount()
  ├─→ 1. 保存到 jwt_accounts 表 (JwtAccountRepository)
  ├─→ 2. 保存到 config_data 表 (StoreManager)
  ├─→ 3. 更新内存配置 (JwtUserProperties)
  └─→ 4. 发布变更事件 (ApplicationEventPublisher)
```

**查询账号**:
```
API请求 → JwtAccountService.getAllAccounts()
  └─→ 从 jwt_accounts 表读取 (JwtAccountRepository)
```

**应用启动**:
```
Spring启动 → JwtAccountInitializer.initJwtAccounts()
  └─→ JwtAccountService.loadLatestJwtAccountConfig()
      └─→ 从 config_data 表加载最新版本到内存
```

### 数据一致性保证

1. **写入一致性**：所有写操作同时更新数据库表和配置存储
2. **读取一致性**：查询操作直接从数据库表读取
3. **启动一致性**：应用启动时从配置存储加载到内存
4. **版本管理**：配置存储支持版本管理，可回滚

## 注意事项

1. **迁移现有数据**：如果启用了 `store.security-migration.enabled=true`，`SecurityDataMigrationService` 会在应用启动时自动迁移现有账号到数据库表

2. **密码加密**：所有密码在保存前都会通过 `PasswordEncoder` 加密

3. **事务一致性**：由于使用了 R2DBC（响应式数据库），需要确保 `.block()` 调用在正确的线程上下文中执行

4. **性能考虑**：查询操作现在直接访问数据库，如果账号数量很大，可以考虑添加缓存

## 后续优化建议

1. **添加缓存层**：在 `JwtAccountService` 中添加 Redis 缓存，减少数据库查询
2. **批量操作优化**：使用 R2DBC 的批量操作 API 提高性能
3. **异步事件处理**：配置变更事件可以异步处理，避免阻塞主流程
4. **监控和日志**：添加更详细的操作日志和监控指标
