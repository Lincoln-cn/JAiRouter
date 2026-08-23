# Audit Log Management

<!-- 版本信息 -->
> **Doc Version**: 1.7.0
> **Last Updated**: 2026-05-21
> **Git Commit**: 61384b4a
> **Author**: Lincoln
<!-- /版本信息 -->

## Overview

JAiRouter audit logging records all important security-related operations and configuration changes in the system, providing complete data support for security analysis, troubleshooting, and compliance auditing.

## Features

### Core Features

- **Comprehensive recording**: Records key events such as login, configuration changes, and API Key operations
- **Tamper-proof**: Audit logs cannot be modified once generated
- **Long-term retention**: Supports log archiving and long-term storage
- **Query and analysis**: Multi-dimensional query and statistical analysis
- **Compliance support**: Meets security audit compliance requirements

### Audit Log Types

| Type | Description | Recorded Content |
|------|-------------|------------------|
| **Security audit** | Login, logout, permission changes | User, IP, time, result |
| **Config audit** | Config item create, update, delete | Config key, before/after values, operator |
| **API Key audit** | API Key create, delete, rotate | Key name, operation type, operator |
| **Blacklist audit** | Blacklist add, delete, cleanup | Type, value, reason, operator |

## Audit Log Structure

### Log Fields

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique log identifier |
| `operation` | string | Operation type (LOGIN, CREATE, UPDATE, DELETE) |
| `resource_type` | string | Resource type (USER, CONFIG, API_KEY, BLACKLIST) |
| `resource_id` | string | Resource identifier |
| `operator` | string | Operator |
| `operator_ip` | string | Operator IP |
| `request_uri` | string | Request URI |
| `request_method` | string | Request method |
| `old_value` | string | Value before change (JSON) |
| `new_value` | string | Value after change (JSON) |
| `status` | string | Operation status (SUCCESS, FAILURE) |
| `message` | string | Operation description |
| `created_at` | timestamp | Creation time |

### Log Examples

#### Login Audit

```json
{
  "id": "audit-log-001",
  "operation": "LOGIN",
  "resource_type": "USER",
  "resource_id": "admin",
  "operator": "admin",
  "operator_ip": "192.168.1.100",
  "request_uri": "/api/auth/login",
  "request_method": "POST",
  "status": "SUCCESS",
  "message": "User admin logged in successfully",
  "created_at": "2026-04-10T10:30:00Z"
}
```

#### Config Change Audit

```json
{
  "id": "audit-log-002",
  "operation": "UPDATE",
  "resource_type": "CONFIG",
  "resource_id": "rate-limiter.default",
  "operator": "admin",
  "operator_ip": "192.168.1.100",
  "request_uri": "/api/config/rate-limiter/default",
  "request_method": "PUT",
  "old_value": "{\"rate\":100,\"window\":60}",
  "new_value": "{\"rate\":200,\"window\":60}",
  "status": "SUCCESS",
  "message": "Updated rate limiter configuration",
  "created_at": "2026-04-10T11:00:00Z"
}
```

## Querying Audit Logs

### Via the Admin Console

Visit the admin console `/admin/security/audit-logs` to:

- View the audit log list
- Filter logs by criteria
- Export log data
- View log details

### Via API

#### Get Audit Log List

```http
GET /api/security/audit-logs?operation={operation}&resourceType={type}&operator={user}&page={page}&size={size}
Authorization: Bearer {token}
```

Parameters:
- `operation`: optional, filter by operation type
- `resourceType`: optional, filter by resource type
- `operator`: optional, filter by operator
- `page`: optional, page number, default 0
- `size`: optional, items per page, default 20

Response:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "audit-log-001",
        "operation": "LOGIN",
        "resource_type": "USER",
        "operator": "admin",
        "operator_ip": "192.168.1.100",
        "status": "SUCCESS",
        "created_at": "2026-04-10T10:30:00Z"
      }
    ],
    "totalElements": 150,
    "totalPages": 8,
    "size": 20,
    "number": 0
  }
}
```

#### Get Single Log Detail

```http
GET /api/security/audit-logs/{logId}
Authorization: Bearer {token}
```

#### Export Audit Logs

```http
POST /api/security/audit-logs/export
Authorization: Bearer {token}
Content-Type: application/json
```

Request body:
```json
{
  "filters": {
    "operation": "UPDATE",
    "resourceType": "CONFIG",
    "startTime": "2026-04-01T00:00:00Z",
    "endTime": "2026-04-10T23:59:59Z"
  },
  "format": "CSV"
}
```

## Configuration Change Auditing

### Automatic Recording

The system automatically records the following configuration changes:

- Rate limiter configuration changes
- Circuit breaker configuration changes
- Load balancing configuration changes
- Service instance configuration changes

### Audit Content

Each configuration change records:

1. **Change operation**: create, update, delete
2. **Config key**: the modified configuration item
3. **Before/after values**: full configuration comparison
4. **Operator info**: username and IP address
5. **Operation time**: accurate to the second

### Config Audit Table Structure

```sql
CREATE TABLE config_change_audit_log (
    id VARCHAR(64) PRIMARY KEY,
    config_key VARCHAR(256) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    operator VARCHAR(64),
    operated_at TIMESTAMP NOT NULL,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Log Management

### Log Cleanup

Periodically clean up expired audit logs:

```yaml
jairouter:
  security:
    audit-log:
      cleanup:
        enabled: true
        schedule: "0 0 2 * * ?"  # Daily at 2 AM
        retention-days: 180      # Retain 180 days
```

### Log Archiving

Supports archiving historical logs to external storage:

```yaml
jairouter:
  security:
    audit-log:
      archive:
        enabled: true
        schedule: "0 0 3 * * 0"  # Weekly on Sunday at 3 AM
        storage: "s3"            # s3, oss, local
        path: "/archive/audit-logs"
```

### Log Backup

Regularly backing up audit logs is recommended for compliance auditing:

```bash
# Export audit logs from the last 30 days
curl -X POST "http://localhost:8080/api/security/audit-logs/export" \
     -H "Authorization: Bearer admin_token" \
     -H "Content-Type: application/json" \
     -d '{
       "filters": {
         "startTime": "2026-03-10T00:00:00Z",
         "endTime": "2026-04-10T23:59:59Z"
       },
       "format": "JSON"
     }' > audit-logs-backup.json
```

## Monitoring and Alerts

### Monitoring Metrics

- `jairouter_audit_logs_total`: total audit logs
- `jairouter_audit_logs_by_operation`: statistics by operation type
- `jairouter_audit_logs_by_status`: statistics by status
- `jairouter_audit_logs_failed`: number of failed operations

### Alert Configuration

```yaml
jairouter:
  security:
    audit-log:
      alerts:
        enabled: true
        # Too many login failures
        login-failure-threshold: 5
        # Configuration change frequency too high
        config-change-rate-threshold: 10
```

## Best Practices

### 1. Regular Audit Analysis

- Review key configuration changes weekly
- Generate audit reports monthly
- Conduct quarterly compliance audits

### 2. Log Protection

- Read-only permissions for audit logs
- Prevent log tampering
- Offsite backup storage

### 3. Correlation Analysis

- Combine login logs and operation logs
- Analyze abnormal operation patterns
- Discover potential security risks

### 4. Compliance Requirements

- Meet MLPS 2.0 requirements
- Comply with GDPR data protection
- Support SOX audit requirements

## Troubleshooting

### Common Issues

#### 1. Audit logs not recorded

**Possible causes**:
- Audit feature not enabled
- Insufficient log table space

**Solutions**:
1. Check the `jairouter.security.audit-log.enabled` configuration
2. Check database space and connections

#### 2. Slow log queries

**Possible causes**:
- Too much log data
- Missing indexes

**Solutions**:
1. Clean up historical logs regularly
2. Add indexes to query fields
3. Use log archiving

## Related Documentation

- [API Key Management](api-key-management.md)
- [JWT Authentication](jwt-authentication.md)
- [Security Blacklist](blacklist-management.md)
- [Data Sanitization](data-sanitization.md)
