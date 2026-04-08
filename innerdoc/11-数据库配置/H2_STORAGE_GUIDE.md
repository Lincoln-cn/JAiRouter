# H2 数据库存储指南

## 概述

本项目支持使用 H2 嵌入式数据库来持久化配置数据，替代原有的文件存储方式。H2 数据库提供了更好的性能、事务支持和查询能力。

## 特性

- **嵌入式数据库**：无需额外安装数据库服务器
- **版本管理**：支持配置的多版本存储和查询
- **事务支持**：保证数据一致性
- **高性能**：比文件存储更快的读写速度
- **易于迁移**：提供从文件存储到 H2 的自动迁移工具

## 配置方式

### 1. 基本配置

在 `application.yml` 中配置：

```yaml
store:
  type: h2
  h2:
    url: file:./data/config  # 数据库文件路径
```

### 2. 使用配置文件

使用预定义的 H2 配置文件：

```bash
java -jar model-router.jar --spring.profiles.active=h2
```

### 3. 环境变量配置

```bash
export STORE_TYPE=h2
export STORE_H2_URL=file:./data/config
```

## 数据迁移

### 自动迁移

启用自动迁移功能，在应用启动时自动将文件存储的数据迁移到 H2：

```yaml
store:
  type: h2
  migration:
    enabled: true
```

### 手动迁移

如果需要手动控制迁移过程，可以通过 API 或代码调用：

```java
@Autowired
private ConfigMigrationService migrationService;

public void migrate() {
    migrationService.migrateFromFileToH2("./config");
}
```

## 数据库管理

### H2 控制台

启用 H2 控制台进行数据库管理和调试：

```yaml
spring:
  h2:
    console:
      enabled: true
      path: /h2-console
```

访问 `http://localhost:8080/h2-console` 进行管理。

连接信息：
- JDBC URL: `jdbc:h2:file:./data/config`
- Username: `sa`
- Password: (留空)

### 数据库表结构

主要表：`config_data`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| config_key | VARCHAR(255) | 配置键 |
| config_value | TEXT | 配置内容（JSON格式） |
| version | INT | 版本号 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |
| is_latest | BOOLEAN | 是否为最新版本 |

## 性能优化

### 索引

系统自动创建以下索引以提高查询性能：

- `idx_config_key`: 配置键索引
- `idx_is_latest`: 最新版本标记索引
- `idx_config_key_latest`: 组合索引

### 连接池配置

```yaml
spring:
  r2dbc:
    pool:
      initial-size: 10
      max-size: 20
      max-idle-time: 30m
```

## 备份与恢复

### 备份

H2 数据库文件位于 `./data/config.mv.db`，可以直接复制该文件进行备份：

```bash
cp ./data/config.mv.db ./backup/config-$(date +%Y%m%d).mv.db
```

### 恢复

停止应用，替换数据库文件后重启：

```bash
cp ./backup/config-20241120.mv.db ./data/config.mv.db
```

## 故障排查

### 数据库文件损坏

如果数据库文件损坏，可以尝试使用 H2 的恢复工具：

```bash
java -cp h2*.jar org.h2.tools.Recover -dir ./data -db config
```

### 迁移失败

如果迁移失败，检查日志并确保：
1. 文件存储路径正确
2. 有足够的磁盘空间
3. 文件权限正确

### 性能问题

如果遇到性能问题：
1. 检查索引是否正确创建
2. 调整连接池大小
3. 考虑使用内存模式（仅用于测试）

## 与文件存储的对比

| 特性 | 文件存储 | H2 数据库 |
|------|---------|----------|
| 性能 | 中等 | 高 |
| 事务支持 | 无 | 有 |
| 查询能力 | 有限 | 强大 |
| 版本管理 | 基于文件名 | 数据库字段 |
| 备份 | 复制文件 | 复制数据库文件 |
| 可读性 | 高（JSON文件） | 需要工具查看 |

## 最佳实践

1. **生产环境**：推荐使用 H2 文件模式，定期备份数据库文件
2. **开发环境**：可以使用内存模式快速测试
3. **迁移**：首次切换到 H2 时，先备份文件存储数据
4. **监控**：定期检查数据库文件大小和性能指标
5. **版本清理**：定期清理不需要的历史版本以节省空间

## 示例代码

### 保存配置

```java
@Autowired
private StoreManager storeManager;

public void saveConfig() {
    Map<String, Object> config = new HashMap<>();
    config.put("key", "value");
    storeManager.saveConfig("my-config", config);
}
```

### 查询配置

```java
// 获取最新版本
Map<String, Object> config = storeManager.getConfig("my-config");

// 获取指定版本
Map<String, Object> versionConfig = storeManager.getConfigByVersion("my-config", 1);

// 获取所有版本号
List<Integer> versions = storeManager.getConfigVersions("my-config");
```

### 删除配置

```java
// 删除所有版本
storeManager.deleteConfig("my-config");

// 删除指定版本
storeManager.deleteConfigVersion("my-config", 1);
```
