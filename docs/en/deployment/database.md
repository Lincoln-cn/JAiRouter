# Database Deployment

JAiRouter uses an **embedded H2 database** by default, which is suitable for single-instance deployments and development. As soon as you need **multiple replicas (`replicas > 1`)**, you must switch to an **external shared database** — this page explains how to connect to PostgreSQL.

## Why multiple replicas require an external database

An H2 single-file database can be **opened by only one process at a time**. Measured (H2 2.3.232, URL parameters identical to the default configuration, two JVM processes opening the same file concurrently):

```
[B] 失败: org.h2.jdbc.JdbcSQLNonTransientConnectionException:
    Database may be already in use: ".../data/jairouter.mv.db".
    Possible solutions: close all other connection(s); use the server mode [90020-232]
```

As a result, both mounting strategies are broken with multiple replicas:

| Mounting strategy | Actual behavior |
|---|---|
| Multiple pods share one volume (`ReadWriteMany`) | The 2nd and 3rd pods **cannot open the database** ⇒ start-up fails, crash loop |
| Each pod has its own volume | Each pod holds its own copy ⇒ users / accounts / API keys / role permissions diverge ⇒ **random 401s as requests round-robin** |

> If the shared volume sits on NFS, the risk **may** escalate from a clean failure to concurrent-write corruption (NFS locking semantics are unreliable). This point was not measured.

**Conclusion**: without an external shared database, `replicas` must stay at `1`. See issue #160.

## Prerequisites

| Item | Requirement |
|---|---|
| PostgreSQL | 12 or newer (the automated tests run against `postgres:16-alpine`) |
| JDBC driver | `org.postgresql:postgresql` is bundled with the distribution — nothing extra to install |
| Database account privileges | `CONNECT`, plus `USAGE` and `CREATE` on the target schema (Flyway needs them to create tables and indexes and to maintain `flyway_schema_history`) |
| Character set | UTF-8 (default) |

## Configuration

The data source is externalized through three environment variables, and the **defaults still point at H2**, so behavior is unchanged when they are not set:

```yaml
# config/config-service/core.yml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:h2:file:./data/jairouter;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=FALSE}
    username: ${DATABASE_USERNAME:sa}
    password: ${DATABASE_PASSWORD:}
```

To connect to PostgreSQL:

```bash
export DATABASE_URL="jdbc:postgresql://pg.internal:5432/jairouter"
export DATABASE_USERNAME="jairouter"
export DATABASE_PASSWORD="<strong-password>"
```

Notes:

- **Do not set `spring.jpa.properties.hibernate.dialect`.** The dialect is auto-detected by Hibernate from the connection; hardcoding it is silently carried over when you switch databases, producing wrong DDL with hard-to-diagnose errors.
- The driver needs no explicit declaration (`driver-class-name` was removed); Spring infers it from the URL prefix.
- The database must be **created beforehand** (`CREATE DATABASE jairouter;`). The application only creates tables.

### Connection pool

No connection pool is configured explicitly today, so Spring Boot's `HikariCP` defaults apply (maximum 10 connections). Before deploying multiple replicas, budget the total connections against PostgreSQL's `max_connections`:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10      # example; keep replicas x this value < max_connections
      connection-timeout: 30000
```

> The pool settings have **not been validated under load**. Tune them to your own capacity plan.

## Cross-database caveats

The following two differences between H2 and PostgreSQL are real and were verified by automated tests against a real PostgreSQL instance.

### Index and constraint names are schema-scoped

In PostgreSQL, tables, indexes, sequences, and views **share one schema-wide namespace**, whereas index names are **table-scoped** in H2 / MySQL. Two different tables therefore **cannot use the same index name**:

```
ERROR: relation "idx_audit_timestamp" already exists
```

When this happens, Hibernate **only logs a WARN and continues**, so the **index is silently dropped** — functionality is unaffected, but the related queries lose their index and get slower. Three historical duplicates (`idx_audit_*`, `idx_instance_id`) have been fixed, and automated tests now guard both name uniqueness and the actual existence of indexes.

When adding `@Index` to a new entity, keep the name **globally unique** and prefer a table prefix (for example `idx_cb_metrics_instance_id`).

### Physical type convention for the JSON columns (`tags` / `headers`)

`tags` / `headers` on `service_instance` use JSON mapping (`@JdbcTypeCode(SqlTypes.JSON)`) and the entity **no longer hard-codes `columnDefinition`**: the physical type is left to the dialect — PostgreSQL creates **`jsonb`** (measured: `information_schema.columns.udt_name = jsonb`), H2 / MySQL use their own `json` type, and read/write works.

`CompatibilitySchemaMigrator` is aligned with the entity: the old-database upgrade path produces the same JSON type and **converges** columns that earlier migrations had added as `TEXT` / `CLOB` into `jsonb` (done automatically on PostgreSQL; on other databases it only warns — recreate those databases). Both database origins therefore converge on one physical type, which is the prerequisite for adopting Flyway and switching `ddl-auto` to `validate` (the original divergence is issue #190).

> Check a database's actual types:
> `SELECT udt_name FROM information_schema.columns WHERE table_name = 'service_instance' AND column_name IN ('tags', 'headers');`

## Versioned schema migrations (Flyway, PostgreSQL only)

On the PostgreSQL path, table creation is now owned by **Flyway** (issue #191). **No extra switch is needed**: the application derives this from the dialect of the data source URL, see `PostgresFlywayEnvironmentPostProcessor`.

| Data source | Flyway | `ddl-auto` | Who creates tables |
|---|---|---|---|
| `jdbc:postgresql://…` | enabled | `validate` | the versioned scripts under `db/migration` |
| H2 (default / `DATABASE_URL` unset) | disabled | `update` | Hibernate |

The decision is keyed on the URL rather than on a profile because which database this project connects to is decided solely by `DATABASE_URL`: the same `prod` profile may point at H2 or at PostgreSQL. When PostgreSQL is detected the processor injects `spring.flyway.enabled=true`, `baseline-on-migrate=true`, `baseline-version=1` and overrides `ddl-auto` to `validate`; on H2 it only disables Flyway and leaves `ddl-auto` at `update`, so the H2 path keeps its previous behavior.

### The baseline (`V1__baseline.sql`)

`db/migration/V1__baseline.sql` is the **complete schema of the current entity model**, produced by Hibernate's schema export (not hand-written DDL), so it matches the entity mappings by construction — which is the precondition for `validate` to pass.

- **Empty database**: Flyway runs `V1`, creating a schema identical to the entity model (history row: `version = 1`, `type = SQL`).
- **Existing database** (tables present, no `flyway_schema_history`): `baseline-on-migrate=true` records it **as baseline 1 without executing** `V1` (history row: `type = BASELINE`); the database contents are left untouched.

> The historical `V2` / `V3` scripts were deleted as part of this change. The reason is checkable: nothing referenced them, and `V2` was going to add five columns to `service_instance` that **no longer exist in the current model** (the rate-limit and circuit-breaker fields are now separate `instance_rate_limit` / `instance_circuit_breaker` tables). Keeping them would only reintroduce that divergence on fresh databases.

### Upgrade prerequisite (the likeliest place to trip)

`ddl-auto: validate` runs when Hibernate builds the `EntityManagerFactory`, which is **before** `CompatibilitySchemaMigrator` (an `ApplicationRunner`). So an existing PostgreSQL database that is missing a column, or whose `tags` / `headers` are still the `TEXT` columns added by migrations from before #190, **fails at start-up** with:

```
Schema-validation: missing column [tags] in table [service_instance]
```

The baseline does not patch those differences for you. The remedy is to **run once with the old behavior before upgrading**: start with `--spring.jpa.hibernate.ddl-auto=update` so `CompatibilitySchemaMigrator` fills in the columns and converges the JSON columns to `jsonb`, then start again with the default configuration. Turning those patches into versioned migrations — which removes this prerequisite entirely — is the scope of issue #192.

### Writing a new migration

- Published scripts must **never be edited** (Flyway verifies checksums); add `V2__…` / `V3__…` for schema changes.
- Scripts target the PostgreSQL dialect only (for example `jsonb`, `generated by default as identity`); H2 never executes them.
- Concurrency: Flyway takes its own lock on `flyway_schema_history`, so when several instances start at once only one performs the migration and the others wait — already safer than every pod running DDL. Even so, having a **Kubernetes Job run the migration first** and letting application pods only `validate` is preferable (see `kubernetes.md`).

## Migrating data from H2 to PostgreSQL

> ⚠️ **These steps have not been verified end-to-end against real production data.** Rehearse against a copy and verify row counts per table.

1. **Create the schema on PostgreSQL first**: start the application once with a `DATABASE_URL` pointing at PostgreSQL — Flyway runs `V1__baseline.sql` and creates the tables — then stop it.

2. **Export each table as CSV from H2** (run on the H2 side):

   ```sql
   CALL CSVWRITE('/tmp/export/api_call_history.csv', 'SELECT * FROM api_call_history');
   ```

3. **Import into PostgreSQL**:

   ```bash
   psql -h pg.internal -U jairouter -d jairouter \
     -c "\copy api_call_history FROM '/tmp/export/api_call_history.csv' CSV HEADER"
   ```

4. **Fix the identity sequences** (critical and easy to miss). The ID columns use `IDENTITY`, and bulk loading does **not** advance the underlying sequence, so later inserts collide with primary keys:

   ```sql
   SELECT setval(pg_get_serial_sequence('api_call_history', 'id'),
                 COALESCE((SELECT MAX(id) FROM api_call_history), 1));
   ```

   Run this for **every** table with an auto-incrementing primary key.

5. **Verify**: compare row counts between H2 and PostgreSQL for each table, and spot-check that key entities (accounts, API keys, role permissions) read back correctly.

## Verifying the connection took effect

1. **Check the start-up log**: the data source URL should be `jdbc:postgresql://…`. If it is still `jdbc:h2:file:…`, `DATABASE_URL` was not picked up.
2. **List the tables**:

   ```sql
   SELECT table_name FROM information_schema.tables
   WHERE table_schema = 'public' ORDER BY table_name;
   ```

   You should see 20 application tables (plus Flyway's own `flyway_schema_history`).
3. **Confirm the migration ran and recorded no failure**:

   ```sql
   SELECT version, description, type, success FROM flyway_schema_history ORDER BY installed_rank;
   ```

   A fresh database should show a single row `1 | baseline | SQL | t`; an existing database a single row `1 | << Flyway Baseline >> | BASELINE | t`.
4. **Check the indexes are complete** (related to the namespace trap above):

   ```sql
   SELECT indexname FROM pg_indexes WHERE schemaname = 'public' ORDER BY indexname;
   ```

## Automated tests

`PostgresCompatibilityIntegrationTest` verifies cross-database compatibility against a **real PostgreSQL** instance (table creation for every entity, `EXTRACT(HOUR …)`, large TEXT values, JSON columns, IDENTITY keys, `@Modifying` atomic increments, HQL `LIMIT`, index-name uniqueness, and actual index existence).

It is gated by environment variables and the whole class is skipped when they are absent:

```bash
export PG_TEST=true
export PG_TEST_URL="jdbc:postgresql://127.0.0.1:5432/postgres"
export PG_TEST_USERNAME=postgres
export PG_TEST_PASSWORD=postgres
./mvnw test -Dtest=PostgresCompatibilityIntegrationTest
```

CI (`.github/workflows/java-tests.yml`) provides a `postgres:16-alpine` service container, sets the variables above, and includes a "gated tests were not skipped" check — CI fails if that test gets skipped.

> Why not H2's `MODE=PostgreSQL`: the compatibility mode only covers a subset of the syntax and **cannot detect** real differences in dialect functions, column types, or generated DDL — which is exactly where cross-database issues live (for example `FUNCTION('HOUR', …)` works on H2 but there is no such function on PostgreSQL).

## Not covered yet

The following are **out of scope** for the current support and need separate work before scaling horizontally:

- **The upgrade prerequisite is still manual**: `CompatibilitySchemaMigrator` remains a start-up patch that runs after `validate`, so a legacy database with missing columns must be converged once using the remedy above (issue #192 turns these patches into versioned migrations so the component can be retired safely).
- **No dedicated migration entry point yet**: the Kubernetes artifacts use the `jairouter-schema-init` Job for migrations, but it relies on `--spring.main.web-application-type=none` plus an `activeDeadlineSeconds` fallback, so the Job status does not accurately reflect migration success (issue #193 provides a real migrate-only entry point).
- Multi-replica Kubernetes artifacts for production (manifests / Helm, PodDisruptionBudget, migration jobs): see issue #165.
