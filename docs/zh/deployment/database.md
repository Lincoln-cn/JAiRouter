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
| 数据库账号权限 | 需要 `CONNECT`、目标 schema 上的 `USAGE` + `CREATE`（Flyway 建表、建索引、维护 `flyway_schema_history` 都要用到） |
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

### JSON 列的物理类型约定（`tags` / `headers`）

`service_instance` 的 `tags` / `headers` 用 JSON 映射（`@JdbcTypeCode(SqlTypes.JSON)`），**实体不再硬编码 `columnDefinition`**：物理类型交给方言 —— PostgreSQL 上建为 **`jsonb`**（实测 `information_schema.columns.udt_name = jsonb`），H2 / MySQL 上为各自的 `json` 类型，读写正常。

`CompatibilitySchemaMigrator` 已与实体对齐：旧库升级路径补列时产出同一 JSON 类型，并把早期补成 `TEXT` / `CLOB` 的列**收敛为 `jsonb`**。于是**两种来历的库收敛到同一物理类型** —— 这是 Flyway 基线能配 `ddl-auto: validate` 的前置条件（原分歧见 issue #190）。

在 PostgreSQL 路径上，这项工作现在由版本化脚本 `V2__legacy_convergence.sql` 承担（见下文「老库收敛」）；该 Java 组件已收窄为**只在没有版本化迁移时**注册，即实际只剩 H2 路径。

> 核对某库的实际类型：
> `SELECT udt_name FROM information_schema.columns WHERE table_name = 'service_instance' AND column_name IN ('tags', 'headers');`

## 版本化 schema 迁移（Flyway，仅 PostgreSQL）

PostgreSQL 路径的建表改由 **Flyway** 承担（issue #191）。**不需要额外开关**：应用按数据源 URL 的方言自行决定，见 `PostgresFlywayEnvironmentPostProcessor`。

| 数据源 | Flyway | `ddl-auto` | 建表方 |
|---|---|---|---|
| `jdbc:postgresql://…` | 启用 | `validate` | `db/migration` 下的版本化脚本 |
| H2（默认 / 未设 `DATABASE_URL`） | 关闭 | `update` | Hibernate |

之所以按 URL 而不是按 profile 判定：本仓库连哪个库只由 `DATABASE_URL` 决定，同一个 `prod` profile 既可能连 H2 也可能连 PG。连到 PG 时注入 `spring.flyway.enabled=true`、`baseline-on-migrate=true`、`baseline-version=1`，并把 `ddl-auto` 覆盖为 `validate`；连 H2 时只关闭 Flyway，`ddl-auto` 保持 `update`，H2 路径行为不变。

### 基线（`V1__baseline.sql`）

`db/migration/V1__baseline.sql` 是**当前实体模型的完整 schema**，由 Hibernate 的 schema 导出生成（不是手写 DDL），因此与实体映射天然一致 —— 这是 `validate` 能通过的前提。

- **空库**：Flyway 从头执行 `V1`，建成与实体一致的 schema（历史表记 `version = 1`、`type = SQL`）。
- **已有库**（表已存在、无 `flyway_schema_history`）：`baseline-on-migrate=true` 使其**被记为基线 1 而不执行** `V1`（历史表记 `type = BASELINE`），库内容不动。

> 历史上的 `V2` / `V3` 两个脚本已随本步删除，原因可核对：它们无任何代码引用，且 `V2` 要给 `service_instance` 补 5 个**当前模型已不存在**的列（限流、熔断字段现已独立为 `instance_rate_limit` / `instance_circuit_breaker` 两张表）。留在序列里只会在新库上重新引入这类分歧。

### 老库收敛（`V2__legacy_convergence.sql`）

改造前，老库的兼容修补由两个**启动期**组件承担：`CompatibilitySchemaMigrator`（补列并收敛 JSON 列）与 `DatabaseMigrationService`（把 `security_blacklist.expires_at` 改为可空）。两者的执行时机都在 Hibernate 建 `EntityManagerFactory` **之后**，而 `validate` 恰恰就在那一步 —— 所以缺列的老库会在补丁跑到之前就启动失败。

现在 PostgreSQL 路径由 `V2__legacy_convergence.sql` 接管这批语义（在 `validate` **之前**执行）：

- **空的 PG 库**：`V1` 已建好一切，`V2` 每一步都是空操作。
- **老 PG 库**（缺列，或 `tags` / `headers` 仍是早期补成的 `text`）：`V1` 不执行（只记为基线 1），`V2` 补齐缺列并把 JSON 列收敛为 `jsonb`，随后 `validate` 通过 —— **不需要任何人工前置动作**。
- 该脚本所有语句都幂等，重复执行安全。

> 两个启动期组件已收窄为**只在没有版本化迁移时注册**（`@ConditionalOnProperty(spring.flyway.enabled=false)`，缺省即注册）：PG 上它们不再参与 schema 决定，H2 上照旧兜底。为什么 H2 还需要它们 —— 实测 H2 2.3.232 + Hibernate 6.6 下 `ddl-auto: update` **不会**给已存在的表补列；而 `update` 也从不会修改已存在列的 NOT NULL 约束。
>
> 版本号提示：这里的 `V2__legacy_convergence.sql` 与上面提到的、已删除的历史脚本 `V2__add_rate_limit_circuit_breaker_fields.sql` 只是编号撞车，内容无关。
>
> 核对老库收敛结果：
> `SELECT column_name, udt_name FROM information_schema.columns WHERE table_name = 'service_instance' AND column_name IN ('tags', 'headers');`
> 两列都应已是 `jsonb`。

### H2 路径的老库

H2 不启用 Flyway、也没有 `validate`，因此老 H2 库的兼容仍由那两个启动期组件在启动后补齐：缺列由 `CompatibilitySchemaMigrator` 补（日志形如 `Schema 迁移: 表 service_instance 已补列 tags json`），`expires_at` 由 `DatabaseMigrationService` 改可空。启动日志里能看到 `Schema 迁移` 与 `Starting database migration check...` 这两组行。

### 写新迁移

- 已发布的脚本**不可再改**（Flyway 按校验和判定）；schema 变更请新增 `V3__…`（`V2` 已被老库收敛占用）。
- 脚本只针对 PostgreSQL 方言（`jsonb`、`generated by default as identity` 等），H2 不执行它们。
- 并发语义：Flyway 在 `flyway_schema_history` 上有自己的锁，多实例同时启动只有一个真正执行、其余等待 —— 已比「每个 Pod 都跑 DDL」安全。进一步做法是让迁移由先行的 K8s Job 跑完、应用 Pod 只做 `validate`（见 `kubernetes.md`）。

## 从 H2 迁移数据到 PostgreSQL

> ⚠️ **下述步骤未在真实业务数据上端到端验证过**，请先在副本库上演练并逐表核对行数。

1. **先在 PG 上建好表结构**：以指向 PG 的 `DATABASE_URL` 启动一次应用 —— Flyway 会执行 `V1__baseline.sql` 把表建起来 —— 然后停掉。

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

   应看到 20 张业务表（另有 Flyway 自己的 `flyway_schema_history`）。
3. **确认迁移已跑且没有失败记录**：

   ```sql
   SELECT version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;
   ```

   空库应只有一行 `1 | baseline | SQL | t`；已有库应只有一行 `1 | << Flyway Baseline >> | BASELINE | t`。
4. **查索引是否齐全**（对照上一节的命名空间陷阱）：

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

- **H2 路径仍是启动期补丁**：H2 不启用 Flyway（见上文），老 H2 库的缺列与约束修正仍由 `CompatibilitySchemaMigrator` / `DatabaseMigrationService` 在启动后执行。这两个组件**不能整体删除**：实测（H2 2.3.232 + Hibernate 6.6）`ddl-auto: update` 不给已存在的表补列，`update` 也不会改已存在列的 NOT NULL 约束。要彻底下线它们，得先给 H2 也做版本化迁移，或明确放弃 H2 老库升级路径 —— 两者都不在本步范围内。
- **迁移尚无独立入口**：K8s 制品用 `jairouter-schema-init` Job 承担迁移，但依赖 `--spring.main.web-application-type=none` 加 `activeDeadlineSeconds` 兜底，Job 状态并不精确反映迁移成败（issue #193 会提供真正的 migrate-only 入口）。
- 面向生产的多副本 K8s 部署制品（清单 / Helm、PodDisruptionBudget、迁移作业）见 issue #165。
