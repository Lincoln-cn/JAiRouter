# 数据库部署

JAiRouter 默认使用 **H2 嵌入式数据库**，它适合单实例部署与开发。一旦需要**多副本（`replicas > 1`）**，就必须改用**外部共享数据库**——本文说明如何接入 PostgreSQL。

## 为什么多副本必须外置数据库

H2 的单文件库**同一时刻只能被一个进程打开**。实测（H2 2.3.232、URL 参数与默认配置一致、两个 JVM 进程并发打开）：

```
[B] 失败: org.h2.jdbc.JdbcSQLNonTransientConnectionException:
    Database may be already in use: ".../data/jairouter.mv.db".
    Possible solutions: close all other connection(s); use the server mode [90020-232]
```

因此多副本下两种挂载方式都是坏的：

| 挂载方式 | 实际表现 |
|---|---|
| 多个 Pod 共享同一卷（`ReadWriteMany`） | 第 2、3 个 Pod **无法打开库** ⇒ 启动即失败，crash-loop |
| 每个 Pod 各自独立卷 | 每 Pod 一份数据 ⇒ 用户 / 账号 / API Key / 角色权限各存一份 ⇒ **请求轮询时随机 401** |

> 共享卷若落在 NFS 上，风险**可能**从「干净失败」升级为「并发写损坏」（NFS 锁语义不可靠）。该点未实测。

**结论**：不接外部共享数据库时，`replicas` 必须保持 `1`。参见 issue #160。

## 前置要求

| 项 | 要求 |
|---|---|
| PostgreSQL | 12 或更高（自动化测试跑在 `postgres:16-alpine` 上） |
| JDBC 驱动 | 已内置 `org.postgresql:postgresql`（随发行包提供，无需另行安装） |
| 数据库账号权限 | 需要 `CONNECT`、目标 schema 上的 `USAGE` + `CREATE`（用于建表与建索引；若改用版本化迁移则由迁移账号持有） |
| 字符集 | UTF-8（默认） |

## 配置

数据源通过三个环境变量外化，**默认值仍指向 H2**，因此不设这些变量时行为与从前完全一致：

```yaml
# config/config-service/core.yml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:h2:file:./data/jairouter;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE}
    username: ${DATABASE_USERNAME:sa}
    password: ${DATABASE_PASSWORD:}
```

连接到 PostgreSQL：

```bash
export DATABASE_URL="jdbc:postgresql://pg.internal:5432/jairouter"
export DATABASE_USERNAME="jairouter"
export DATABASE_PASSWORD="<强密码>"
```

说明：

- **不要设置 `spring.jpa.properties.hibernate.dialect`**。方言由 Hibernate 按连接自动探测；显式写死会在换库时被静默沿用，生成错误的 DDL 且报错难以定位。
- 驱动无需显式声明（`driver-class-name` 已移除），Spring 会按 URL 前缀推断。
- 数据库需**预先创建**（`CREATE DATABASE jairouter;`），应用只负责建表。

### 连接池

当前**未显式配置连接池**，使用的是 Spring Boot 的 `HikariCP` 默认值（最大 10 个连接）。多副本部署前请按实例数折算总连接数，避免超过 PostgreSQL 的 `max_connections`：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10      # 示例值；按 实例数 × 该值 < max_connections 估算
      connection-timeout: 30000
```

> 连接池参数的实际取值**未在负载下验证**，请按自己的容量规划调整。

## 跨库注意事项

以下两点是 H2 与 PostgreSQL 的真实差异，均由自动化测试在真实 PG 上验证：

### 索引与约束名是 schema 级命名空间

PostgreSQL 里表、索引、序列、视图**共用一个 schema 级命名空间**，而 H2 / MySQL 的索引名是**表级**的。因此两个不同的表**不能使用同名索引**：

```
ERROR: relation "idx_audit_timestamp" already exists
```

Hibernate 遇到这种冲突**只记 WARN 并继续**，结果是**索引被静默丢失**——功能不受影响，但相关查询会失去索引而变慢。仓库已修正三处历史重名（`idx_audit_*`、`idx_instance_id`），并加了自动化测试守卫名唯一性与索引实际存在性。

新增实体声明 `@Index` 时请确保**名字全局唯一**，建议加表名前缀（如 `idx_cb_metrics_instance_id`）。

### `columnDefinition = "JSON"` 在 PG 上的实际类型

`service_instance` 的 `tags` / `headers` 使用的是 JSON 映射。在 PostgreSQL 上它们建为 **`json`** 类型（实测 `information_schema.columns.udt_name = json`），读写正常。

> 注意：`CompatibilitySchemaMigrator` 在**旧库升级**路径上给 `tags` 补的是 `CLOB`/`TEXT`，而**新库**由 Hibernate 建成 `json` —— 同一逻辑列的物理类型会按「库的来源」分歧。该分歧当前**未修复**，因为它不影响读写；若需彻底统一，见 issue #160 的后续工作。

## 从 H2 迁移数据到 PostgreSQL

> ⚠️ **下述步骤未在真实业务数据上端到端验证过**，请先在副本库上演练并逐表核对行数。

1. **先在 PG 上建好表结构**：用指向 PG 的 `DATABASE_URL` 启动一次应用，让 Hibernate 建表，然后停掉。

2. **从 H2 逐表导出为 CSV**（在 H2 端执行）：

   ```sql
   CALL CSVWRITE('/tmp/export/api_call_history.csv', 'SELECT * FROM api_call_history');
   ```

3. **导入到 PostgreSQL**：

   ```bash
   psql -h pg.internal -U jairouter -d jairouter \
     -c "\copy api_call_history FROM '/tmp/export/api_call_history.csv' CSV HEADER"
   ```

4. **修正自增序列**（关键，容易漏）。表的 ID 用的是 `IDENTITY` 列，批量导入**不会**推进底层序列，后续插入会撞主键冲突：

   ```sql
   SELECT setval(pg_get_serial_sequence('api_call_history', 'id'),
                 COALESCE((SELECT MAX(id) FROM api_call_history), 1));
   ```

   对**每一张**有自增主键的表都要执行。

5. **核对**：逐表比对 H2 与 PG 的行数，并抽查关键实体（账号、API Key、角色权限）能否正常读出。

## 验证接入是否生效

1. **看启动日志**：数据源 URL 应为 `jdbc:postgresql://…`；若仍是 `jdbc:h2:file:…`，说明 `DATABASE_URL` 没被读到。
2. **查表**：

   ```sql
   SELECT table_name FROM information_schema.tables
   WHERE table_schema = 'public' ORDER BY table_name;
   ```

   应看到 20 张业务表。
3. **查索引是否齐全**（对照上一节的命名空间陷阱）：

   ```sql
   SELECT indexname FROM pg_indexes WHERE schemaname = 'public' ORDER BY indexname;
   ```

## 自动化测试

`PostgresCompatibilityIntegrationTest` 在**真实 PostgreSQL** 上验证跨库适配（全部实体建表、`EXTRACT(HOUR …)`、TEXT 大文本、JSON 列、IDENTITY 主键、`@Modifying` 原子自增、HQL `LIMIT`、索引名唯一性与索引实际存在性）。

它由环境变量门控，未开启时整类跳过：

```bash
export PG_TEST=true
export PG_TEST_URL="jdbc:postgresql://127.0.0.1:5432/postgres"
export PG_TEST_USERNAME=postgres
export PG_TEST_PASSWORD=postgres
./mvnw test -Dtest=PostgresCompatibilityIntegrationTest
```

CI（`.github/workflows/java-tests.yml`）已配 `postgres:16-alpine` 服务容器并设置了上述变量，且有一道「门控测试未被跳过」的校验——若该测试被跳过，CI 直接失败。

> 为什么不用 H2 的 `MODE=PostgreSQL`：兼容模式只覆盖语法子集，**验不出**方言函数、列类型、DDL 生成这些真实差异——而那正是跨库问题的所在（例如 `FUNCTION('HOUR', …)` 在 H2 可用、在 PG 无此函数）。

## 尚未覆盖的部分

以下内容**不在**当前支持范围内，横向扩展前需要单独处理：

- **版本化 schema 迁移**：当前仍由 Hibernate `ddl-auto: update` 管理表结构，没有 `Flyway` / `Liquibase`。多副本同时启动会并发执行 DDL。
- **索引名唯一性的历史遗留**：`CompatibilitySchemaMigrator` 在旧库上补的 `tags` 列类型与实体声明不一致（见上文）。
- 面向生产的多副本 K8s 部署制品（清单 / Helm、PodDisruptionBudget、迁移作业）见 issue #165。
