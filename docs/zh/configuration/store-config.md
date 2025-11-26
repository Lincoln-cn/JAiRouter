# 存储配置

JAiRouter 使用存储管理器来持久化配置数据和版本控制。本页面详细介绍存储相关的配置选项。

## 存储类型

JAiRouter 支持多种存储类型，默认使用 H2 数据库存储。

### H2 数据库存储 (h2)

H2 数据库存储是默认的存储方式，将配置数据保存在嵌入式 H2 数据库中，提供更好的性能、事务支持和查询能力。

H2 数据库现在是项目的**默认存储方式**，适用于所有环境（dev、test、prod）。

### 文件存储 (file)

文件存储将配置数据保存在本地文件系统中，适用于简单的使用场景。

### 内存存储 (memory)

内存存储将配置数据保存在内存中，适用于临时或测试场景。

## 配置选项

存储配置通过 `store` 前缀进行配置，可以在 `application.yml` 或相关配置文件中设置。

### 基本配置

```yaml
store:
  type: h2          # 存储类型，默认为 h2
  h2:
    url: file:./data/config  # H2 数据库文件路径
  auto-merge: true  # 是否启用自动合并功能，默认为 true
```

### 配置说明

| 配置项 | 默认值 | 说明 |
|-------|--------|------|
| `store.type` | `h2` | 存储类型，支持 h2、file、memory |
| `store.h2.url` | `file:./data/config` | H2 数据库文件路径 |
| `store.path` | `config/` | 配置文件存储的目录路径（仅文件存储时使用） |
| `store.auto-merge` | `true` | 是否启用自动合并功能 |

### H2 数据库存储高级配置

```yaml
store:
  type: h2  # 默认使用 H2 数据库
  h2:
    url: file:./data/config  # H2 数据库文件路径
  migration:
    enabled: false  # 是否启用配置数据迁移（从文件存储迁移到H2数据库）
  security-migration:
    enabled: false  # 是否启用安全数据迁移（API Keys、JWT 账户等）

spring:
  r2dbc:
    url: r2dbc:h2:file:///./data/config
    username: sa
    password:
    pool:
      initial-size: 10
      max-size: 20
  h2:
    console:
      enabled: false
      path: /h2-console

jairouter:
  security:
    audit:
      storage: h2  # 安全审计日志存储类型
      retentionDays: 30
```

### H2 数据库存储完整配置

```yaml
store:
  type: h2
  h2:
    url: file:./data/config
  migration:
    enabled: false
  security-migration:
    enabled: false

spring:
  r2dbc:
    url: r2dbc:h2:file:///./data/config
    username: sa
    password:
    pool:
      initial-size: 10
      max-size: 20
      max-idle-time: 30m
  h2:
    console:
      enabled: false
      path: /h2-console

jairouter:
  security:
    audit:
      storage: h2
      retentionDays: 30

## 环境特定配置

不同环境可以有不同的存储配置：

### 开发环境 (application-dev.yml)

```yaml
store:
  type: h2
  h2:
    url: file:./data/dev-config
  migration:
    enabled: true  # 开发环境启用自动迁移
  security-migration:
    enabled: true  # 开发环境启用安全数据迁移

spring:
  r2dbc:
    pool:
      initial-size: 10
      max-size: 20
  h2:
    console:
      enabled: true
      path: /h2-console

jairouter:
  security:
    audit:
      storage: h2
      retentionDays: 7
```

### 生产环境 (application-prod.yml)

```yaml
store:
  type: h2
  h2:
    url: file:/var/lib/jairouter/data/config
  migration:
    enabled: false
  security-migration:
    enabled: false

spring:
  h2:
    console:
      enabled: false
  r2dbc:
    pool:
      initial-size: 20
      max-size: 50
      max-idle-time: 30m

jairouter:
  security:
    audit:
      storage: h2
      retentionDays: 90
```

## 自动合并配置

自动合并功能用于扫描和处理配置目录中的多版本配置文件。

### 功能说明

当启用自动合并功能时，系统会定期扫描配置目录中的多版本配置文件，并提供合并功能。

### 使用示例

```yaml
# 启用自动合并功能并指定配置目录
store:
  type: file
  path: "/var/lib/jairouter/config/"
  auto-merge: true
```

## H2 数据库管理

### H2 控制台访问

在开发环境中可以启用 H2 控制台进行数据库管理和调试：

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

系统会自动创建以下表来存储不同类型的数据：

| 表名 | 用途 | 记录数预估 |
|------|------|-----------|
| config_data | 配置数据 | 10-100 |
| security_audit | 安全审计 | 10,000+ |
| api_keys | API 密钥 | 10-50 |
| jwt_accounts | JWT 账户 | 5-20 |

#### config_data 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| config_key | VARCHAR(255) | 配置键 |
| config_value | TEXT | 配置内容（JSON格式） |
| version | INT | 版本号 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |
| is_latest | BOOLEAN | 是否为最新版本 |

#### security_audit 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| event_id | VARCHAR(255) | 事件唯一标识 |
| event_type | VARCHAR(100) | 事件类型 |
| user_id | VARCHAR(255) | 用户ID |
| client_ip | VARCHAR(50) | 客户端IP |
| user_agent | VARCHAR(500) | 用户代理 |
| timestamp | TIMESTAMP | 事件时间 |
| resource | VARCHAR(500) | 访问资源 |
| action | VARCHAR(100) | 执行操作 |
| success | BOOLEAN | 是否成功 |
| failure_reason | VARCHAR(1000) | 失败原因 |
| additional_data | TEXT | 附加数据（JSON） |
| request_id | VARCHAR(255) | 请求ID |
| session_id | VARCHAR(255) | 会话ID |

#### api_keys 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| key_id | VARCHAR(255) | Key ID |
| key_value | VARCHAR(500) | Key 值 |
| description | VARCHAR(1000) | 描述 |
| permissions | TEXT | 权限列表（JSON） |
| expires_at | TIMESTAMP | 过期时间 |
| created_at | TIMESTAMP | 创建时间 |
| enabled | BOOLEAN | 是否启用 |
| metadata | TEXT | 元数据（JSON） |
| usage_statistics | TEXT | 使用统计（JSON） |

#### jwt_accounts 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| username | VARCHAR(255) | 用户名 |
| password | VARCHAR(500) | 密码（加密） |
| roles | TEXT | 角色列表（JSON） |
| enabled | BOOLEAN | 是否启用 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

## 数据迁移

### 从文件存储迁移到 H2 数据库

启用自动迁移功能，在应用启动时自动将文件存储的数据迁移到 H2：

```yaml
store:
  type: h2
  migration:
    enabled: true
```

### 安全数据迁移

系统还支持将安全相关数据迁移到 H2 数据库：

```yaml
store:
  type: h2
  security-migration:
    enabled: true  # 启用安全数据迁移（API Keys、JWT 账户等）
```

### 首次启动配置

如果系统中有现有数据需要迁移，可以在首次启动时启用迁移功能：

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

## 性能优化

### 连接池配置

```yaml
spring:
  r2dbc:
    pool:
      initial-size: 10
      max-size: 20
      max-idle-time: 30m
      max-acquire-time: 3s
      max-create-connection-time: 5s
```

### 索引优化

系统自动创建以下索引以提高查询性能：

- `idx_config_key`: 配置键索引
- `idx_is_latest`: 最新版本标记索引
- `idx_config_key_latest`: 组合索引

### 数据库优化

定期执行数据库优化：

```sql
-- 分析表
ANALYZE TABLE config_data;
ANALYZE TABLE security_audit;
ANALYZE TABLE api_keys;
ANALYZE TABLE jwt_accounts;

-- 查看索引使用情况
SELECT * FROM INFORMATION_SCHEMA.INDEXES;
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

### 生产环境备份策略

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

## 注意事项

1. 确保存储路径具有适当的读写权限
2. 在生产环境中，建议使用绝对路径以避免路径问题
3. 配置目录应定期备份以防止数据丢失
4. 不同环境应使用不同的存储路径以避免配置冲突
5. H2 数据库文件不能在多个进程间共享
6. H2 数据库现在是默认存储方式，提供更好的性能和可靠性

## 故障排除

### 存储目录不存在

如果配置的存储目录不存在，系统会记录警告日志但不会自动创建目录。请确保目录存在并具有适当的权限。

### 权限问题

如果遇到权限问题，请检查：
- 应用程序运行用户对存储目录的读写权限
- 父目录的执行权限（进入目录的权限）

### 磁盘空间不足

监控存储目录的磁盘使用情况，确保有足够的空间存储配置数据和版本历史。

### 自动合并功能未生效

如果自动合并功能未生效，请检查：
- `store.auto-merge` 配置是否设置为 true
- 配置目录是否存在且具有读取权限
- 配置文件命名是否符合 `model-router-config@<version>.json` 格式

### 数据库连接失败

如果遇到数据库连接问题，请检查：
- `store.h2.url` 配置是否正确
- 数据库文件路径是否有写权限
- 应用日志中的详细错误信息

### 数据库文件损坏

```bash
# 尝试恢复
java -cp h2*.jar org.h2.tools.Recover -dir ./data -db config

# 如果恢复失败，使用备份
cp ./backup/config-latest.mv.db ./data/config.mv.db
```

### 连接池耗尽

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