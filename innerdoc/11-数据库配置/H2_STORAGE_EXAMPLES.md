# H2 存储使用示例

## 基础示例

### 1. 保存配置

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.store.StoreManager;

import java.util.HashMap;
import java.util.Map;

@Service
public class ConfigService {
    
    @Autowired
    private StoreManager storeManager;
    
    public void saveModelConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("adapter", "gpustack");
        config.put("rateLimit", Map.of(
            "rate", 100,
            "enabled", true
        ));
        
        storeManager.saveConfig("model-router-config", config);
        System.out.println("配置已保存");
    }
}
```

### 2. 读取配置

```java
public void loadModelConfig() {
    Map<String, Object> config = storeManager.getConfig("model-router-config");
    
    if (config != null) {
        String adapter = (String) config.get("adapter");
        Map<String, Object> rateLimit = (Map<String, Object>) config.get("rateLimit");
        
        System.out.println("Adapter: " + adapter);
        System.out.println("Rate Limit: " + rateLimit);
    } else {
        System.out.println("配置不存在");
    }
}
```

### 3. 更新配置

```java
public void updateModelConfig() {
    // 获取现有配置
    Map<String, Object> config = storeManager.getConfig("model-router-config");
    
    if (config != null) {
        // 修改配置
        config.put("adapter", "openai");
        
        // 保存更新（会创建新版本）
        storeManager.saveConfig("model-router-config", config);
        System.out.println("配置已更新");
    }
}
```

### 4. 删除配置

```java
public void deleteModelConfig() {
    storeManager.deleteConfig("model-router-config");
    System.out.println("配置已删除");
}
```

## 版本管理示例

### 1. 查看所有版本

```java
public void listVersions() {
    List<Integer> versions = storeManager.getConfigVersions("model-router-config");
    
    System.out.println("配置版本列表:");
    for (Integer version : versions) {
        System.out.println("- 版本 " + version);
    }
}
```

### 2. 获取指定版本

```java
public void getSpecificVersion() {
    // 获取版本 1 的配置
    Map<String, Object> v1Config = storeManager.getConfigByVersion("model-router-config", 1);
    
    if (v1Config != null) {
        System.out.println("版本 1 配置: " + v1Config);
    }
}
```

### 3. 比较版本差异

```java
public void compareVersions() {
    Map<String, Object> v1 = storeManager.getConfigByVersion("model-router-config", 1);
    Map<String, Object> v2 = storeManager.getConfigByVersion("model-router-config", 2);
    
    System.out.println("版本 1: " + v1);
    System.out.println("版本 2: " + v2);
    
    // 找出差异
    v2.forEach((key, value) -> {
        if (!value.equals(v1.get(key))) {
            System.out.println("字段 " + key + " 已变更:");
            System.out.println("  旧值: " + v1.get(key));
            System.out.println("  新值: " + value);
        }
    });
}
```

### 4. 回滚到指定版本

```java
public void rollbackToVersion(int version) {
    Map<String, Object> oldConfig = storeManager.getConfigByVersion("model-router-config", version);
    
    if (oldConfig != null) {
        storeManager.saveConfig("model-router-config", oldConfig);
        System.out.println("已回滚到版本 " + version);
    }
}
```

## 批量操作示例

### 1. 批量保存配置

```java
public void batchSaveConfigs() {
    Map<String, Map<String, Object>> configs = new HashMap<>();
    
    // 准备多个配置
    configs.put("config1", Map.of("key1", "value1"));
    configs.put("config2", Map.of("key2", "value2"));
    configs.put("config3", Map.of("key3", "value3"));
    
    // 批量保存
    configs.forEach((key, config) -> {
        storeManager.saveConfig(key, config);
        System.out.println("已保存: " + key);
    });
}
```

### 2. 批量读取配置

```java
public void batchLoadConfigs() {
    Iterable<String> keys = storeManager.getAllKeys();
    
    for (String key : keys) {
        Map<String, Object> config = storeManager.getConfig(key);
        System.out.println(key + ": " + config);
    }
}
```

### 3. 批量删除配置

```java
public void batchDeleteConfigs(List<String> keysToDelete) {
    for (String key : keysToDelete) {
        if (storeManager.exists(key)) {
            storeManager.deleteConfig(key);
            System.out.println("已删除: " + key);
        }
    }
}
```

## 数据迁移示例

### 1. 手动触发迁移

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.store.migration.ConfigMigrationService;

@Service
public class MigrationService {
    
    @Autowired
    private ConfigMigrationService migrationService;
    
    public void migrateData() {
        System.out.println("开始迁移数据...");
        migrationService.migrateFromFileToH2("./config");
        System.out.println("迁移完成");
    }
}
```

### 2. 条件迁移

```java
public void conditionalMigration() {
    // 检查是否需要迁移
    File configDir = new File("./config");
    if (configDir.exists() && configDir.isDirectory()) {
        File[] files = configDir.listFiles((dir, name) -> name.endsWith(".json"));
        
        if (files != null && files.length > 0) {
            System.out.println("发现 " + files.length + " 个配置文件，开始迁移...");
            migrationService.migrateFromFileToH2("./config");
        } else {
            System.out.println("没有需要迁移的配置文件");
        }
    }
}
```

## REST API 示例

### 1. 配置管理 Controller

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.store.StoreManager;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/configs")
public class ConfigController {
    
    @Autowired
    private StoreManager storeManager;
    
    @GetMapping("/{key}")
    public ResponseEntity<Map<String, Object>> getConfig(@PathVariable String key) {
        Map<String, Object> config = storeManager.getConfig(key);
        return config != null ? ResponseEntity.ok(config) : ResponseEntity.notFound().build();
    }
    
    @PostMapping("/{key}")
    public ResponseEntity<Void> saveConfig(
            @PathVariable String key,
            @RequestBody Map<String, Object> config) {
        storeManager.saveConfig(key, config);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{key}")
    public ResponseEntity<Void> deleteConfig(@PathVariable String key) {
        storeManager.deleteConfig(key);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/{key}/versions")
    public ResponseEntity<List<Integer>> getVersions(@PathVariable String key) {
        List<Integer> versions = storeManager.getConfigVersions(key);
        return ResponseEntity.ok(versions);
    }
    
    @GetMapping("/{key}/versions/{version}")
    public ResponseEntity<Map<String, Object>> getConfigByVersion(
            @PathVariable String key,
            @PathVariable Integer version) {
        Map<String, Object> config = storeManager.getConfigByVersion(key, version);
        return config != null ? ResponseEntity.ok(config) : ResponseEntity.notFound().build();
    }
}
```

## 响应式编程示例

### 1. 使用 Repository 进行响应式查询

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.store.repository.ConfigRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ReactiveConfigService {
    
    @Autowired
    private ConfigRepository configRepository;
    
    public Mono<String> getLatestConfigValue(String key) {
        return configRepository.findLatestByConfigKey(key)
                .map(entity -> entity.getConfigValue());
    }
    
    public Flux<String> getAllConfigKeys() {
        return configRepository.findAllLatestConfigKeys();
    }
    
    public Mono<Boolean> configExists(String key) {
        return configRepository.existsByConfigKey(key);
    }
}
```

## 事务示例

### 1. 使用事务保证数据一致性

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.store.StoreManager;

import java.util.Map;

@Service
public class TransactionalConfigService {
    
    @Autowired
    private StoreManager storeManager;
    
    @Transactional
    public void updateMultipleConfigs(Map<String, Map<String, Object>> configs) {
        configs.forEach((key, config) -> {
            storeManager.saveConfig(key, config);
        });
        // 如果任何一个保存失败，所有更改都会回滚
    }
    
    @Transactional
    public void migrateConfig(String oldKey, String newKey) {
        Map<String, Object> config = storeManager.getConfig(oldKey);
        if (config != null) {
            storeManager.saveConfig(newKey, config);
            storeManager.deleteConfig(oldKey);
        }
    }
}
```

## 性能优化示例

### 1. 批量查询优化

```java
public Map<String, Map<String, Object>> loadAllConfigs() {
    Map<String, Map<String, Object>> allConfigs = new HashMap<>();
    
    // 获取所有键
    Iterable<String> keys = storeManager.getAllKeys();
    
    // 批量加载
    for (String key : keys) {
        Map<String, Object> config = storeManager.getConfig(key);
        if (config != null) {
            allConfigs.put(key, config);
        }
    }
    
    return allConfigs;
}
```

### 2. 缓存配置

```java
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

@Service
public class CachedConfigService {
    
    @Autowired
    private StoreManager storeManager;
    
    @Cacheable(value = "configs", key = "#key")
    public Map<String, Object> getCachedConfig(String key) {
        return storeManager.getConfig(key);
    }
    
    @CacheEvict(value = "configs", key = "#key")
    public void updateAndEvictCache(String key, Map<String, Object> config) {
        storeManager.saveConfig(key, config);
    }
}
```

## 监控示例

### 1. 配置变更监控

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConfigMonitor {
    
    @Autowired
    private StoreManager storeManager;
    
    private Map<String, Integer> lastVersions = new HashMap<>();
    
    @Scheduled(fixedRate = 60000) // 每分钟检查一次
    public void monitorConfigChanges() {
        Iterable<String> keys = storeManager.getAllKeys();
        
        for (String key : keys) {
            List<Integer> versions = storeManager.getConfigVersions(key);
            if (!versions.isEmpty()) {
                int latestVersion = versions.get(versions.size() - 1);
                Integer lastVersion = lastVersions.get(key);
                
                if (lastVersion == null || latestVersion > lastVersion) {
                    System.out.println("配置 " + key + " 已更新到版本 " + latestVersion);
                    lastVersions.put(key, latestVersion);
                }
            }
        }
    }
}
```

## 测试示例

### 1. 单元测试

```java
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.unreal.modelrouter.store.StoreManager;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConfigServiceTest {
    
    @Autowired
    private StoreManager storeManager;
    
    @Test
    void testSaveAndLoad() {
        Map<String, Object> config = new HashMap<>();
        config.put("test", "value");
        
        storeManager.saveConfig("test-key", config);
        
        Map<String, Object> loaded = storeManager.getConfig("test-key");
        assertNotNull(loaded);
        assertEquals("value", loaded.get("test"));
        
        // 清理
        storeManager.deleteConfig("test-key");
    }
}
```

## 总结

这些示例涵盖了 H2 存储的主要使用场景：

- ✅ 基础 CRUD 操作
- ✅ 版本管理
- ✅ 批量操作
- ✅ 数据迁移
- ✅ REST API
- ✅ 响应式编程
- ✅ 事务处理
- ✅ 性能优化
- ✅ 监控
- ✅ 测试

根据实际需求选择合适的示例进行参考和修改。
