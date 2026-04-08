# 改进的配置持久化设计方案

## 核心设计理念

1. **初始化配置**：应用启动时的默认配置，形成版本1
2. **运行时配置**：通过REST API修改的配置，形成后续版本
3. **版本管理**：每次配置变更都创建新版本，启动时加载最新版本

## 改进的存储结构

```
config/
├── security.api-keys.json          # 当前活跃配置（最新版本的软链接或副本）
├── security.api-keys.metadata.json # 配置元数据（版本信息、变更历史等）
└── versions/
    ├── security.api-keys.v1.json   # 版本1（初始化配置）
    ├── security.api-keys.v2.json   # 版本2
    └── security.api-keys.v3.json   # 版本3（当前最新）
```

## 核心接口改进

### 1. 配置版本管理接口
```java
public interface ConfigVersionManager {
    // 初始化配置（仅在首次启动时调用）
    void initializeConfig(String key, Map<String, Object> config);
    
    // 更新配置（运行时调用，自动创建新版本）
    int updateConfig(String key, Map<String, Object> config);
    
    // 获取最新配置
    Map<String, Object> getLatestConfig(String key);
    
    // 获取指定版本配置
    Map<String, Object> getConfigByVersion(String key, int version);
    
    // 获取配置元数据
    ConfigMetadata getConfigMetadata(String key);
}
```

### 2. 配置元数据结构
```java
public class ConfigMetadata {
    private String configKey;
    private int currentVersion;
    private int initialVersion;
    private List<VersionInfo> versionHistory;
    private LocalDateTime lastModified;
    private String lastModifiedBy;
}

public class VersionInfo {
    private int version;
    private LocalDateTime createdAt;
    private String createdBy;
    private String description;
    private String changeType; // INITIAL, UPDATE, ROLLBACK
}
```

## 改进的工作流程

### 应用启动流程
1. 检查配置是否存在
2. 如果不存在，使用默认配置创建版本1
3. 如果存在，加载最新版本配置
4. 更新应用配置属性

### 配置更新流程
1. 验证新配置的有效性
2. 比较与当前配置的差异
3. 如果有变化，创建新版本
4. 更新元数据
5. 更新当前活跃配置
6. 发布配置变更事件

### 配置回滚流程
1. 验证目标版本是否存在
2. 加载目标版本配置
3. 创建回滚版本（新版本号）
4. 更新当前活跃配置
5. 更新元数据
## 改进后
的实现特点

### 1. 清晰的配置生命周期
- **初始化阶段**：应用首次启动时，使用默认配置创建版本1
- **运行时更新**：通过REST API更新配置时，自动创建新版本
- **启动加载**：应用重启时，自动加载最新版本配置

### 2. 优化的存储结构
```
config/
├── security.api-keys.json              # 当前活跃配置
├── security.api-keys.metadata.json     # 配置元数据
├── security.api-keys.history.json      # 版本变更历史
└── versions/
    ├── security.api-keys.v1.json       # 版本1
    ├── security.api-keys.v2.json       # 版本2
    └── security.api-keys.v3.json       # 版本3
```

### 3. 智能版本管理
- **变更检测**：只有配置真正发生变化时才创建新版本
- **版本清理**：自动清理旧版本，避免存储空间浪费
- **回滚支持**：支持回滚到任意历史版本

### 4. 完整的元数据管理
- **版本信息**：记录每个版本的创建时间、操作用户、变更描述
- **变更类型**：区分初始化、更新、回滚等不同操作类型
- **配置统计**：跟踪总版本数、当前版本等信息

### 5. 向后兼容性
- 保持原有的 `StoreManager` 接口兼容
- 新增的 `ConfigVersionManager` 接口提供更强大的版本管理功能
- 现有代码可以无缝迁移到新的实现

## 使用示例

### 应用启动时的配置初始化
```java
@PostConstruct
public void initializeConfigurations() {
    // 检查配置是否已初始化
    if (!configVersionManager.isConfigInitialized("security.api-keys")) {
        // 使用默认配置初始化
        Map<String, Object> defaultConfig = getDefaultApiKeysConfig();
        configVersionManager.initializeConfig(
            "security.api-keys", 
            defaultConfig, 
            "Initial API keys configuration"
        );
    }
    
    // 加载最新配置到内存
    Map<String, Object> latestConfig = configVersionManager.getLatestConfig("security.api-keys");
    applyApiKeysConfig(latestConfig);
}
```

### 运行时配置更新
```java
public void updateApiKeys(List<ApiKeyInfo> newApiKeys) {
    // 转换为存储格式
    Map<String, Object> configMap = convertToMap(newApiKeys);
    
    // 更新配置（自动创建新版本）
    int newVersion = configVersionManager.updateConfig(
        "security.api-keys",
        configMap,
        "Update API keys via REST API",
        getCurrentUserId()
    );
    
    // 应用到内存配置
    applyApiKeysConfig(configMap);
    
    log.info("API keys updated to version: {}", newVersion);
}
```

### 配置回滚
```java
public void rollbackApiKeys(int targetVersion) {
    int newVersion = configVersionManager.rollbackToVersion(
        "security.api-keys",
        targetVersion,
        "Rollback due to configuration issue",
        getCurrentUserId()
    );
    
    // 重新加载配置
    Map<String, Object> rolledBackConfig = configVersionManager.getLatestConfig("security.api-keys");
    applyApiKeysConfig(rolledBackConfig);
    
    log.info("API keys rolled back to version {}, new version: {}", targetVersion, newVersion);
}
```

## 迁移建议

1. **逐步迁移**：可以先在新功能中使用 `ConfigVersionManager`，现有功能继续使用 `StoreManager`
2. **数据迁移**：为现有配置文件创建版本1，并生成相应的元数据
3. **测试验证**：在测试环境充分验证新的版本管理逻辑
4. **监控告警**：添加配置变更的监控和告警机制

这样的设计让配置管理变得更加清晰和可控，符合你提到的"初始化参数直接持久化形成第一个版本，之后配置都在该版本上进行修改"的理念。