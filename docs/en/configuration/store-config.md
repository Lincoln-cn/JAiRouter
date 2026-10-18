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

JAiRouter supports multiple storage types, with H2 database storage as the default.

### H2 Database Storage (h2)

H2 database storage is the default storage method, saving configuration data in an embedded H2 database, providing better performance, transaction support, and query capabilities.

H2 database is now the **default storage method** for the project, suitable for all environments (dev, test, prod).

### File Storage (file)

File storage saves configuration data in the local file system, suitable for simple use cases.

### Memory Storage (memory)

Memory storage saves configuration data in memory, suitable for temporary or testing scenarios.

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
| `store.type` | `jpa` | Storage type, supports h2, file, memory |
| `store.path` | `config/` | Configuration file storage directory path (only used for file storage) |

### H2 Database Storage Advanced Configuration

```yaml
store:
  type: h2  # Default storage type

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
```

### H2 Database Storage Complete Configuration

```yaml
store:
  type: h2

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
```

## Environment-Specific Configuration

Different environments can have different storage configurations:

### Development Environment (application-dev.yml)

```yaml
store:
  type: h2

spring:
  r2dbc:
    pool:
      initial-size: 10
      max-size: 20
  h2:
    console:
      enabled: true
      path: /h2-console

```

### Production Environment (application-prod.yml)

```yaml
store:
  type: h2

spring:
  h2:
    console:
      enabled: false
  r2dbc:
    pool:
      initial-size: 20
      max-size: 50
      max-idle-time: 30m

```

## Auto-Merge Configuration

The auto-merge function is used to scan and process multi-version configuration files in the configuration directory.

### Function Description

When the auto-merge function is enabled, the system will periodically scan multi-version configuration files in the configuration directory and provide merge functionality.

### Usage Example

```yaml
# Enable auto-merge function and specify configuration directory
store:
  type: file
  path: "/var/lib/jairouter/config/"
```

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
- JDBC URL: `jdbc:h2:file:./data/config`
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
  r2dbc:
    pool:
      initial-size: 10
      max-size: 20
      max-idle-time: 30m
      max-acquire-time: 3s
      max-create-connection-time: 5s
```

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

The H2 database file is located at `./data/config.mv.db` and can be backed up by directly copying the file:

```bash
cp ./data/config.mv.db ./backup/config-$(date +%Y%m%d).mv.db
```

### Recovery

Stop the application, replace the database file, and restart:

```bash
cp ./backup/config-20241120.mv.db ./data/config.mv.db
```

### Production Environment Backup Strategy

```bash
#!/bin/bash
# /usr/local/bin/backup-jairouter-db.sh

DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/var/backups/jairouter"
DB_FILE="/var/lib/jairouter/data/config.mv.db"

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
java -cp h2*.jar org.h2.tools.Recover -dir ./data -db config

# If recovery fails, use backup
cp ./backup/config-latest.mv.db ./data/config.mv.db
```

### Connection Pool Exhaustion

Check logs:

```
ERROR: Pool is exhausted
```

Solution:

```yaml
spring:
  r2dbc:
    pool:
      max-size: 100  # Increase connection pool size
```