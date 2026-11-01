# Store Configuration

<!-- 版本信息 -->
> **Doc Version**: 1.0.0
> **Last Updated**: 2026-06-09
> **Git Commit**: 227a92b7
> **Author**: Lincoln
<!-- /版本信息 -->

> **Version:** 2.9.5  
> **Last Updated:** 2026-06-09  
> **Default Storage:** H2 Database

JAiRouter uses a storage manager to persist configuration data and version control. This page details the storage-related configuration options.

## Storage Types

JAiRouter persists configuration data through **JPA**, pointing at the local H2 file database by default. `store.type` accepts exactly two values: `jpa` (the default) and `h2` — both are the same JPA implementation; `h2` only says "the JPA datasource is H2", it is not a second storage engine. What actually differs is the datasource URL and the `ddl-auto` strategy, see [database deployment](../deployment/database.md).

> Earlier revisions of this page described `file` and `memory` storage types. Neither ever had an implementation (`StoreManagerConfiguration` returns the JPA one whatever the value). Since #875 the startup validator accepts only `jpa` / `h2` and rejects anything else outright — better a hard failure than a setting that silently does nothing.

### H2 Database (h2)

The default shape = JPA over an embedded H2 file database, suitable for all environments (dev, test, prod).

## Configuration Options

Storage configuration is configured through the `store` prefix and can be set in `application.yml` or related configuration files.

### Basic Configuration

```yaml
store:
  type: h2          # Storage type, default is h2
```

### Configuration Description

| Configuration Item | Default Value | Description |
|--------------------|---------------|-------------|
| `store.type` | `jpa` | Storage type; only `jpa` and `h2` are accepted (both are the JPA implementation) |
| `store.path` | `config/` | Directory used to import/export configuration files |

### H2 Database Storage Advanced Configuration

```yaml
store:
  type: h2  # the default is jpa; h2 means the same JPA implementation over H2

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

### H2 Database Storage Complete Configuration

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

## Environment-Specific Configuration

Different environments can have different storage configurations:

### Development Environment (application-dev.yml)

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

### Production Environment (application-prod.yml)

```yaml
store:
  type: jpa          # use jpa when production points at PostgreSQL; h2 is single-node only

spring:
  h2:
    console:
      enabled: false
  datasource:
    url: ${DATABASE_URL}          # PostgreSQL JDBC URL, injected through the environment
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 20
      connection-timeout: 30000

```

## Configuration Merging

`ConfigMergeService` merges the **application configuration** (`application*.yml` plus the per-module files) with the **configuration held in persistent storage**, and the persisted values win. There is no switch and no directory: every configuration read composes the two in that order, and a reset first backs up the effective configuration under the `config_pre_reset_backup` key.

> Earlier revisions described this section as "scanning multi-version files in the configuration directory", which was the shape of the removed file storage. File storage no longer exists (`store.type` accepts only `jpa` / `h2`), and merging needs no extra configuration.

## H2 Database Management

### H2 Console Access

The H2 console can be enabled in development environments for database management and debugging:

```yaml
spring:
  h2:
    console:
      enabled: true
      path: /h2-console
```

Access `http://localhost:8080/h2-console` for management.

Connection information:
- JDBC URL: `jdbc:h2:file:./data/jairouter`
- Username: `sa`
- Password: (empty)

### Database Table Structure

The system automatically creates the following tables to store different types of data:

| Table Name | Purpose | Estimated Records |
|------------|---------|------------------|
| config_data | Configuration data | 10-100 |
| security_audit | Security audit | 10,000+ |
| api_keys | API Keys | 10-50 |
| jwt_accounts | JWT Accounts | 5-20 |

#### config_data Table

| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT | Primary key |
| config_key | VARCHAR(255) | Configuration key |
| config_value | TEXT | Configuration content (JSON format) |
| version | INT | Version number |
| created_at | TIMESTAMP | Creation time |
| updated_at | TIMESTAMP | Update time |
| is_latest | BOOLEAN | Whether it is the latest version |

#### security_audit Table

| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT | Primary key |
| event_id | VARCHAR(255) | Unique event identifier |
| event_type | VARCHAR(100) | Event type |
| user_id | VARCHAR(255) | User ID |
| client_ip | VARCHAR(50) | Client IP |
| user_agent | VARCHAR(500) | User agent |
| timestamp | TIMESTAMP | Event time |
| resource | VARCHAR(500) | Accessed resource |
| action | VARCHAR(100) | Performed action |
| success | BOOLEAN | Whether successful |
| failure_reason | VARCHAR(1000) | Failure reason |
| additional_data | TEXT | Additional data (JSON) |
| request_id | VARCHAR(255) | Request ID |
| session_id | VARCHAR(255) | Session ID |

#### api_keys Table

| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT | Primary key |
| key_id | VARCHAR(255) | Key ID |
| key_value | VARCHAR(500) | Key value |
| description | VARCHAR(1000) | Description |
| permissions | TEXT | Permission list (JSON) |
| expires_at | TIMESTAMP | Expiration time |
| created_at | TIMESTAMP | Creation time |
| enabled | BOOLEAN | Whether enabled |
| metadata | TEXT | Metadata (JSON) |
| usage_statistics | TEXT | Usage statistics (JSON) |

#### jwt_accounts Table

| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT | Primary key |
| username | VARCHAR(255) | Username |
| password | VARCHAR(500) | Password (encrypted) |
| roles | TEXT | Role list (JSON) |
| enabled | BOOLEAN | Whether enabled |
| created_at | TIMESTAMP | Creation time |
| updated_at | TIMESTAMP | Update time |

## Data Migration

### Migrating from File Storage to H2 Database

Enable automatic migration function to automatically migrate file storage data to H2 during application startup:

```yaml
store:
  type: h2
```

### Security Data Migration

The system also supports migrating security-related data to H2 database:

```yaml
store:
  type: h2
```

### First-time Startup Configuration

If there is existing data in the system that needs to be migrated, you can enable the migration function during first-time startup:

**Development Environment:**

```yaml
# application-dev.yml
store:
```

After starting the application, the system will automatically:
1. Migrate configuration data from `./config/*.json`
2. Migrate API Keys from memory/configuration files
3. Migrate JWT accounts from configuration files

After migration is complete, it is recommended to disable automatic migration:

```yaml
store:
```

**Production Environment:**

It is recommended to manually migrate in production environments:

> **There are no `store.migration` / `store.security-migration` switches.**
> Migration runs through the dedicated `migrate` profile (`application-migrate.yml`),
> and `jairouter.migration.exit-after-run` controls whether the process exits afterwards.

## Performance Optimization

### Connection Pool Configuration

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      max-lifetime: 1800000
```

> Pool size is **not** a throughput knob: in measurement, raising it from 10 to 32 did not raise throughput (384.7 to 368.6 RPS) and doubled p99. Budget it against `replicas x maximum-pool-size < PostgreSQL max_connections` first, then re-measure on a real database, see [database deployment](../deployment/database.md).

### Index Optimization

The system automatically creates the following indexes to improve query performance:

- `idx_config_key`: Configuration key index
- `idx_is_latest`: Latest version marker index
- `idx_config_key_latest`: Composite index

### Database Optimization

Regularly perform database optimization:

```sql
-- Analyze tables
ANALYZE TABLE config_data;
ANALYZE TABLE security_audit;
ANALYZE TABLE api_keys;
ANALYZE TABLE jwt_accounts;

-- View index usage
SELECT * FROM INFORMATION_SCHEMA.INDEXES;
```

## Backup and Recovery

### Backup

The H2 database file is located at `./data/jairouter.mv.db` and can be backed up by directly copying the file:

```bash
cp ./data/jairouter.mv.db ./backup/config-$(date +%Y%m%d).mv.db
```

### Recovery

Stop the application, replace the database file, and restart:

```bash
cp ./backup/config-20241120.mv.db ./data/jairouter.mv.db
```

### Production Environment Backup Strategy

```bash
#!/bin/bash
# /usr/local/bin/backup-jairouter-db.sh

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/var/backups/jairouter"
DB_FILE="/var/lib/jairouter/data/jairouter.mv.db"

mkdir -p $BACKUP_DIR
cp $DB_FILE $BACKUP_DIR/config_$DATE.mv.db

# Compress backup
gzip $BACKUP_DIR/config_$DATE.mv.db

# Keep backups for the last 30 days
find $BACKUP_DIR -name "config_*.mv.db.gz" -mtime +30 -delete

echo "Backup completed: config_$DATE.mv.db.gz"
```

Add to crontab:

```bash
# Backup daily at 3 AM
0 3 * * * /usr/local/bin/backup-jairouter-db.sh
```

## Notes

1. Ensure the storage path has appropriate read/write permissions
2. In production environments, it is recommended to use absolute paths to avoid path issues
3. Configuration directories should be backed up regularly to prevent data loss
4. Different environments should use different storage paths to avoid configuration conflicts
5. H2 database files cannot be shared between multiple processes
6. H2 database is now the default storage method, providing better performance and reliability

## Troubleshooting

### Storage Directory Does Not Exist

If the configured storage directory does not exist, the system will log warning messages but will not automatically create the directory. Please ensure the directory exists and has appropriate permissions.

### Permission Issues

If you encounter permission issues, please check:
- Read/write permissions of the application running user to the storage directory
- Execute permissions of the parent directory (permission to enter the directory)

### Insufficient Disk Space

Monitor the disk usage of the storage directory to ensure there is enough space to store configuration data and version history.

### Auto-Merge Function Not Working

If the auto-merge function is not working, please check:
- Whether the configuration directory exists and has read permissions
- Whether the configuration file naming conforms to the `model-router-config@<version>.json` format

### Database Connection Failure

If you encounter database connection issues, please check:
- Whether the database file path has write permissions
- Detailed error information in application logs

### Database File Corruption

```bash
# Try recovery
java -cp h2*.jar org.h2.tools.Recover -dir ./data -db jairouter

# If recovery fails, use backup
cp ./backup/config-latest.mv.db ./data/jairouter.mv.db
```

### Connection Pool Exhaustion

Check logs:

```
ERROR: Pool is exhausted
```

Solution:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20   # budget replicas x this value < max_connections, then re-measure
```

> Mind the wording: Hikari reports `Connection is not available, request timed out after 30000ms`, not `Pool is exhausted` (that was R2DBC phrasing, and R2DBC was removed in #875). Before raising the pool, rule out a slow query or a long transaction holding connections.