# H2 数据库默认配置说明

## 概述

从当前版本开始，H2 嵌入式数据库已成为项目的**默认存储方式**，适用于所有环境（dev、prod）。

## 默认配置

### 基础配置（所有环境）

```yaml
store:
  type: h2  # 默认使用 H2 数据库
  h2:
    url: file:./data/config
  migration:
    enabled: false  # 默认关闭，首次启动时手动启用
  security-migration:
    enabled: false  # 默认关闭，首次启动时手动启用

spring:
  r2dbc:
    url: r2dbc:h2:file:///./data/config
    username: sa
    password:
    pool:
      initial-size: 10
      max-size: 20

jairouter:
  security:
    audit:
      storage: h2  # 审计日志使用 H2 存储
      retentionDays: 30
```

## 环境特定配置

### 开发环境 (dev)

```yaml
store:
  type: h2
  h2:
    url: file:./data/dev-config  # 开发环境独立数据库
  migration:
    enabled: true  # 开发环境启用自动迁移
  security-migration:
    enabled: true  # 开发环境启用安全数据迁移

spring:
  h2:
    console:
      enabled: true  # 开发环境启用 H2 控制台
      path: /h2-console

jairouter:
  security:
    audit:
      storage: h2
      retentionDays: 7  # 开发环境保留7天
```

**访问 H2 控制台：**
- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:file:./data/dev-config`
- Username: `sa`
- Password: (留空)

### 生产环境 (prod)

```yaml
store:
  type: h2
  h2:
    url: file:/var/lib/jairouter/data/config  # 生产环境标准路径
  migration:
    enabled: false  # 生产环境关闭自动迁移
  security-migration:
    enabled: false  # 生产环境关闭安全数据迁移

spring:
  h2:
    console:
      enabled: false  # 生产环境关闭控制台
  r2dbc:
    pool:
      initial-size: 20  # 生产环境更大的连接池
      max-size: 50

jairouter:
  security:
    audit:
      storage: h2
      retentionDays: 90  # 生产环境保留90天
```

## 首次启动配置

### 如果有现有数据需要迁移

**开发环境：**

```yaml
# application-dev.yml
store:
  migration:
    enabled: true  # 启用配置数据迁移
  security-migration:
    enabled: true  # 启用安全数据迁移
```

启动应用后，系统会自动：
1. 从 `./config/*.json` 迁移配置数据
2. 从内存/配置文件迁移 API Keys
3. 从配置文件迁移 JWT 账户

迁移完成后，建议关闭自动迁移：

```yaml
store:
  migration:
    enabled: false
  security-migration:
    enabled: false
```

**生产环境：**

生产环境建议手动迁移：

```bash
# 1. 在测试环境验证迁移
java -jar app.jar --spring.profiles.active=staging \
  --store.migration.enabled=true \
  --store.security-migration.enabled=true

# 2. 验证数据完整性
# 访问 H2 控制台检查数据

# 3. 备份数据库
cp ./data/config.mv.db ./backup/

# 4. 在生产环境执行迁移
java -jar app.jar --spring.profiles.active=prod \
  --store.migration.enabled=true \
  --store.security-migration.enabled=true

# 5. 验证后关闭迁移开关
```

### 如果是全新安装

无需任何额外配置，直接启动即可：

```bash
# 开发环境
java -jar app.jar --spring.profiles.active=dev

# 生产环境
java -jar app.jar --spring.profiles.active=prod
```

系统会自动创建 H2 数据库和所有必要的表。

## 切换到其他存储方式

### 切换到文件存储

```yaml
store:
  type: file
  path: ./config
```

### 切换到内存存储

```yaml
store:
  type: memory
```

### 切换审计日志到内存存储

```yaml
jairouter:
  security:
    audit:
      storage: memory  # 使用内存存储（不推荐生产环境）
```

## 数据库文件位置

### 开发环境
- 数据库文件：`./data/dev-config.mv.db`
- 日志文件：`logs/application.log`

### 生产环境
- 数据库文件：`/var/lib/jairouter/data/config.mv.db`
- 日志文件：`/var/log/jairouter/application.log`

## 备份策略

### 开发环境

```bash
# 简单备份
cp ./data/dev-config.mv.db ./backup/dev-config-$(date +%Y%m%d).mv.db
```

### 生产环境

```bash
#!/bin/bash
# /usr/local/bin/backup-jairouter-db.sh

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/var/backups/jairouter"
DB_FILE="/var/lib/jairouter/data/config.mv.db"

mkdir -p $BACKUP_DIR
cp $DB_FILE $BACKUP_DIR/config_$DATE.mv.db

# 压缩备份
gzip $BACKUP_DIR/config_$DATE.mv.db

# 保留最近30天的备份
find $BACKUP_DIR -name "config_*.mv.db.gz" -mtime +30 -delete

echo "Backup completed: config_$DATE.mv.db.gz"
```

添加到 crontab：

```bash
# 每天凌晨3点备份
0 3 * * * /usr/local/bin/backup-jairouter-db.sh
```

## 监控和维护

### 查看数据库大小

```bash
# 开发环境
du -h ./data/dev-config.mv.db

# 生产环境
du -h /var/lib/jairouter/data/config.mv.db
```

### 查看表记录数

通过 H2 控制台或 SQL：

```sql
SELECT 
    'config_data' as table_name, COUNT(*) as count FROM config_data
UNION ALL
SELECT 'security_audit', COUNT(*) FROM security_audit
UNION ALL
SELECT 'api_keys', COUNT(*) FROM api_keys
UNION ALL
SELECT 'jwt_accounts', COUNT(*) FROM jwt_accounts;
```

### 清理审计日志

审计日志会根据配置的 `retentionDays` 自动清理。

手动清理：

```sql
-- 删除30天前的审计日志
DELETE FROM security_audit 
WHERE timestamp < DATEADD('DAY', -30, CURRENT_TIMESTAMP);
```

## 性能调优

### 连接池配置

根据负载调整连接池大小：

```yaml
spring:
  r2dbc:
    pool:
      initial-size: 20  # 初始连接数
      max-size: 50      # 最大连接数
      max-idle-time: 30m  # 最大空闲时间
      max-acquire-time: 3s  # 获取连接超时
      max-create-connection-time: 5s  # 创建连接超时
```

### 数据库优化

定期执行优化：

```sql
-- 分析表
ANALYZE TABLE config_data;
ANALYZE TABLE security_audit;
ANALYZE TABLE api_keys;
ANALYZE TABLE jwt_accounts;

-- 查看索引使用情况
SELECT * FROM INFORMATION_SCHEMA.INDEXES;
```

## 故障排查

### 问题 1: 数据库文件损坏

```bash
# 尝试恢复
java -cp h2*.jar org.h2.tools.Recover -dir ./data -db config

# 如果恢复失败，使用备份
cp ./backup/config-latest.mv.db ./data/config.mv.db
```

### 问题 2: 连接池耗尽

检查日志：

```
ERROR: Pool is exhausted
```

解决方案：

```yaml
spring:
  r2dbc:
    pool:
      max-size: 100  # 增加连接池大小
```

### 问题 3: 磁盘空间不足

```bash
# 检查磁盘空间
df -h

# 清理审计日志
# 通过 H2 控制台执行清理 SQL

# 或调整保留期
jairouter:
  security:
    audit:
      retentionDays: 7  # 减少保留天数
```

## 迁移检查清单

### 首次启动前

- [ ] 备份现有配置文件（如果有）
- [ ] 确认数据库文件路径有写权限
- [ ] 检查磁盘空间是否充足
- [ ] 确认迁移开关配置正确

### 启动后

- [ ] 检查应用日志，确认迁移成功
- [ ] 访问 H2 控制台验证数据
- [ ] 测试配置读写功能
- [ ] 测试 API Key 和 JWT 账户功能
- [ ] 检查审计日志是否正常记录

### 迁移完成后

- [ ] 关闭自动迁移开关
- [ ] 备份数据库文件
- [ ] 更新监控配置
- [ ] 配置定期备份任务

## 总结

H2 数据库现在是项目的默认存储方式，提供：

- ✅ **开箱即用** - 无需额外配置
- ✅ **高性能** - 嵌入式数据库，无网络开销
- ✅ **易维护** - 单一数据库文件
- ✅ **生产就绪** - 满足生产环境要求
- ✅ **自动迁移** - 平滑从旧系统迁移

如有问题，请参考其他文档或查看应用日志。
