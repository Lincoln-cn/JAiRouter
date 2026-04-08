# 完整的配置持久化流程

## 问题分析

你提到的"config.json的逻辑"指的是配置从静态文件到动态存储的完整生命周期管理。通过分析现有的 `ConfigurationService.java` 和 `ModelRouterProperties`，我发现了正确的实现模式：

### 现有的成功模式（ModelRouterProperties）
1. **ModelRouterProperties**: `@ConfigurationProperties(prefix = "model")` - 从 application.yml 加载
2. **ConfigMergeService**: 处理YAML配置和持久化配置的合并
3. **ConfigurationService**: 提供完整的CRUD和版本管理
4. **StoreManager**: 底层存储，支持版本管理

### 需要实现的安全配置模式（SecurityProperties）
1. **SecurityProperties**: `@ConfigurationProperties(prefix = "jairouter.security")` - 从 application.yml 加载
2. **SecurityConfigMergeService**: 处理安全配置的合并逻辑
3. **SecurityConfigurationManager**: 提供安全配置的管理功能
4. **SecurityConfigurationInitializer**: 处理启动时的配置初始化

## 统一的配置持久化模式

### 1. 应用启动流程（参考 ConfigurationService 模式）

```
应用启动
    ↓
Spring Boot 加载 application.yml
    ↓
@ConfigurationProperties 注入到 SecurityProperties (jairouter.security)
@ConfigurationProperties 注入到 ModelRouterProperties (model)
    ↓
ApplicationReadyEvent 触发
    ↓
SecurityConfigurationInitializer.initializeSecurityConfigurations()
    ↓
SecurityConfigMergeService.hasPersistedSecurityConfig() 检查是否有持久化配置
    ↓
如果未持久化：
  - 获取 YAML 默认配置
  - 保存为版本1到 StoreManager
如果已持久化：
  - 从 StoreManager 加载最新版本
  - 与 YAML 配置合并（持久化配置优先）
  - 更新 SecurityProperties
    ↓
配置初始化完成
```

### 2. 配置更新流程（统一模式）

```
REST API 调用更新配置
    ↓
SecurityConfigurationManager.updateApiKeys()
    ↓
验证新配置
    ↓
获取当前持久化配置
    ↓
更新配置内容
    ↓
SecurityConfigurationManager.saveSecurityAsNewVersion() - 创建新版本
    ↓
更新内存中的 SecurityProperties
    ↓
发布配置变更事件
    ↓
配置更新完成
```

### 3. 应用重启流程（统一模式）

```
应用重启
    ↓
Spring Boot 加载 application.yml（作为默认配置）
    ↓
SecurityConfigurationInitializer 检测到持久化配置存在
    ↓
SecurityConfigMergeService.getMergedSecurityConfig()
  - 加载持久化配置（最新版本）
  - 与 YAML 配置深度合并（持久化优先）
    ↓
更新 SecurityProperties 中的值
    ↓
使用合并后的配置启动应用
```

## 核心组件说明（统一模式）

### 1. SecurityProperties
- **作用**：Spring Boot配置属性类，映射 `jairouter.security` 配置
- **注解**：`@ConfigurationProperties(prefix = "jairouter.security")`
- **生命周期**：应用启动时从 `application.yml` 加载，之后由持久化存储更新
- **对应关系**：类似于 `ModelRouterProperties` (`@ConfigurationProperties(prefix = "model")`)

### 2. SecurityConfigMergeService
- **作用**：安全配置合并服务，处理YAML配置和持久化配置的合并
- **参考实现**：`ConfigMergeService`
- **核心方法**：
  - `getDefaultSecurityConfig()` - 获取YAML默认配置
  - `getPersistedSecurityConfig()` - 获取持久化配置
  - `getMergedSecurityConfig()` - 获取合并后配置
  - `hasPersistedSecurityConfig()` - 检查是否有持久化配置

### 3. SecurityConfigurationManager
- **作用**：安全配置管理器，提供完整的配置管理功能
- **参考实现**：`ConfigurationService`
- **核心方法**：
  - `saveSecurityAsNewVersion()` - 保存为新版本
  - `getAllSecurityVersions()` - 获取所有版本
  - `applySecurityVersion()` - 应用指定版本
  - `getCurrentSecurityVersion()` - 获取当前版本

### 4. SecurityConfigurationInitializer
- **作用**：配置初始化器，负责启动时的配置处理
- **触发时机**：`ApplicationReadyEvent` 事件
- **核心逻辑**：
  - 检查是否有持久化配置
  - 未持久化：将YAML配置保存为版本1
  - 已持久化：加载并合并配置，更新 `SecurityProperties`

### 5. StoreManager
- **作用**：底层存储管理器，提供版本化存储
- **核心方法**：
  - `saveConfig()` - 保存当前配置
  - `saveConfigVersion()` - 保存指定版本配置
  - `getConfigVersions()` - 获取所有版本号
  - `getConfigByVersion()` - 获取指定版本配置

## 配置存储结构

```
config-store/
├── security.api-keys.json              # 当前活跃的API Keys配置
├── security.api-keys.metadata.json     # API Keys配置元数据
├── security.api-keys.history.json      # API Keys版本历史
├── security.jwt-config.json            # 当前活跃的JWT配置
├── security.jwt-config.metadata.json   # JWT配置元数据
├── security.jwt-config.history.json    # JWT版本历史
└── versions/
    ├── security.api-keys.v1.json       # API Keys版本1（初始化）
    ├── security.api-keys.v2.json       # API Keys版本2（第一次更新）
    ├── security.jwt-config.v1.json     # JWT配置版本1（初始化）
    └── security.jwt-config.v2.json     # JWT配置版本2（第一次更新）
```

## 配置优先级

1. **首次启动**：`application.yml` → `SecurityProperties` → 持久化存储（版本1）
2. **后续启动**：持久化存储（最新版本） → `SecurityProperties`
3. **运行时更新**：REST API → 验证 → 持久化存储（新版本） → `SecurityProperties`

## 关键特性

### 1. 配置版本管理
- 每次配置变更都创建新版本
- 支持回滚到任意历史版本
- 自动清理旧版本避免存储浪费

### 2. 配置变更检测
- 只有配置真正发生变化时才创建新版本
- 避免无意义的版本增长

### 3. 配置验证
- 更新前验证配置有效性
- 防止无效配置破坏系统

### 4. 事件驱动
- 配置变更时发布事件
- 其他组件可监听配置变更并做出响应

### 5. 向后兼容
- 保持原有 `StoreManager` 接口兼容
- 现有代码可无缝迁移

## 使用示例

### 初始化配置（应用启动时自动执行）
```java
// SecurityConfigurationInitializer 自动处理
// 无需手动调用
```

### 更新API Keys配置
```java
@RestController
public class SecurityConfigController {
    
    @Autowired
    private ImprovedSecurityConfigurationService configService;
    
    @PostMapping("/api/security/api-keys")
    public Mono<ResponseEntity<String>> updateApiKeys(@RequestBody List<ApiKeyInfo> apiKeys) {
        return configService.updateApiKeys(apiKeys)
                .then(Mono.just(ResponseEntity.ok("API Keys updated successfully")));
    }
}
```

### 回滚配置
```java
@PostMapping("/api/security/api-keys/rollback/{version}")
public Mono<ResponseEntity<String>> rollbackApiKeys(@PathVariable int version) {
    return Mono.fromCallable(() -> {
        configVersionManager.rollbackToVersion("security.api-keys", version, 
                                              "Manual rollback", getCurrentUserId());
        return ResponseEntity.ok("Configuration rolled back to version " + version);
    });
}
```

这样的设计完全解决了你提到的配置持久化逻辑问题，实现了清晰的配置生命周期管理。