# 存储配置

<!-- 版本信息 -->
> **文档版本**: 1.0.0
> **最后更新**: 2026-06-09
> **Git 提交**: 227a92b7
> **作者**: Lincoln
<!-- /版本信息 -->

> **版本：** 2.9.5  
> **最后更新：** 2026-06-09  
> **默认存储：** H2 数据库

JAiRouter 使用存储管理器来持久化配置数据和版本控制。本页面详细介绍存储相关的配置选项。

## 存储类型

JAiRouter 通过 **JPA** 持久化配置数据，默认连本机的 H2 文件库。`store.type` 的合法值只有两个：`jpa`（默认）与 `h2` —— 两者走的是同一套 JPA 实现，`h2` 只表示「JPA 连的是 H2」，不是另一套存储引擎；真正的区别在数据源 URL 与 `ddl-auto` 策略，见[数据库部署](../deployment/database.md)。

> 历史文档里写过 `file` 与 `memory` 两种存储类型：它们从来没有对应实现（`StoreManagerConfiguration` 无论取值都返回 JPA）。从 #875 起启动校验只接受 `jpa` / `h2`，设成别的值会在校验阶段直接报错 —— 与其「设了不生效」，不如当场拒绝。

### H2 数据库（h2）

默认形态 = JPA + 嵌入式 H2 文件库，适用于所有环境（dev、test、prod）。

## 配置选项

存储配置通过 `store` 前缀进行配置，可以在 `application.yml` 或相关配置文件中设置。

### 基本配置

```yaml
store:
  type: h2          # 存储类型，默认为 h2
```

### 配置说明

| 配置项 | 默认值 | 说明 |
|-------|--------|------|
| `store.type` | `jpa` | 存储类型，合法值只有 `jpa` 与 `h2`（均为 JPA 实现） |
| `store.path` | `config/` | 配置文件导入/导出的目录路径 |

### H2 数据库存储高级配置

```yaml
store:
  type: h2  # 默认值即为 jpa；h2 表示同一套 JPA 连 H2

spring:
  datasource:
    url: jdbc:h2:file:./data/jairouter;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE
    username: sa
    password:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
  h2:
    console:
      enabled: false
      path: /h2-console

jairouter:
```

### H2 数据库存储完整配置

```yaml
store:
  type: h2

spring:
  datasource:
    url: jdbc:h2:file:./data/jairouter;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE
    username: sa
    password:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
      max-lifetime: 1800000
  h2:
    console:
      enabled: false
      path: /h2-console

jairouter:
```

## 环境特定配置

不同环境可以有不同的存储配置：

### 开发环境 (application-dev.yml)

```yaml
store:
  type: h2

spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 10
  h2:
    console:
      enabled: true
      path: /h2-console

```

### 生产环境 (application-prod.yml)

```yaml
store:
  type: jpa          # 生产连 PostgreSQL 时用 jpa；h2 只适合单机

spring:
  h2:
    console:
      enabled: false
  datasource:
    url: ${DATABASE_URL}          # PostgreSQL JDBC URL，由环境变量注入
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 20
      connection-timeout: 30000

```

## 配置合并

`ConfigMergeService` 把**应用配置**（`application*.yml` 与各模块配置）与**持久化存储中的配置**合并成运行时生效的那一份，**持久化存储里的值优先**。它没有开关、也不需要目录：每次读取配置时按这个优先级合成；重置配置前会把当前配置备份到 `config_pre_reset_backup` 键。

> 早前的文档把这一节写成「扫描配置目录中的多版本文件」，那是文件存储时期的形态。文件存储已不存在（`store.type` 只接受 `jpa` / `h2`），合并也不依赖任何额外配置。

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
- JDBC URL: `jdbc:h2:file:./data/jairouter`
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
```

### 安全数据迁移

系统还支持将安全相关数据迁移到 H2 数据库：

```yaml
store:
  type: h2
```

### 首次启动配置

如果系统中有现有数据需要迁移，可以在首次启动时启用迁移功能：

**开发环境：**

```yaml
# application-dev.yml
store:
```

启动应用后，系统会自动：
1. 从 `./config/*.json` 迁移配置数据
2. 从内存/配置文件迁移 API Keys
3. 从配置文件迁移 JWT 账户

迁移完成后，建议关闭自动迁移：

```yaml
store:
```

**生产环境：**

生产环境建议手动迁移：

> **迁移没有 `store.migration` / `store.security-migration` 开关。**
> 当前实现通过独立的 `migrate` profile（`application-migrate.yml`）触发，
> 并用 `jairouter.migration.exit-after-run` 控制迁移后是否退出进程。

## 性能优化

### 连接池配置

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      max-lifetime: 1800000
```

> 池大小**不是**吞吐旋钮：实测把它从 10 调到 32，吞吐没有上升（384.7 → 368.6 RPS）而 p99 翻倍。容量规划请先按 `实例数 × maximum-pool-size < PostgreSQL max_connections` 折算，再到真实库上复测，见[数据库部署](../deployment/database.md#连接池)。

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

H2 数据库文件位于 `./data/jairouter.mv.db`，可以直接复制该文件进行备份：

```bash
cp ./data/jairouter.mv.db ./backup/config-$(date +%Y%m%d).mv.db
```

### 恢复

停止应用，替换数据库文件后重启：

```bash
cp ./backup/config-20241120.mv.db ./data/jairouter.mv.db
```

### 生产环境备份策略

```bash
#!/bin/bash
# /usr/local/bin/backup-jairouter-db.sh

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/var/backups/jairouter"
DB_FILE="/var/lib/jairouter/data/jairouter.mv.db"

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
- 配置目录是否存在且具有读取权限
- 配置文件命名是否符合 `model-router-config@<version>.json` 格式

### 数据库连接失败

如果遇到数据库连接问题，请检查：
- 数据库文件路径是否有写权限
- 应用日志中的详细错误信息

### 数据库文件损坏

```bash
# 尝试恢复
java -cp h2*.jar org.h2.tools.Recover -dir ./data -db jairouter

# 如果恢复失败，使用备份
cp ./backup/config-latest.mv.db ./data/jairouter.mv.db
```

### 连接池耗尽

检查日志：

```
ERROR: Pool is exhausted
```

解决方案：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20   # 先按 实例数 × 该值 < max_connections 折算，再到真实库复测
```

> 注意文案：Hikari 报的是 `Connection is not available, request timed out after 30000ms`，不是 `Pool is exhausted`（那是 R2DBC 时期的措辞，R2DBC 已在 #875 移除）。调大池之前先确认不是慢查询或长事务占着连接。