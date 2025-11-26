# API Key Management Guide

<!-- Version Information -->
> **Document Version**: 1.0.0  
> **Last Updated**: 2025-08-19  
> **Git Commit**:   
> **Author**: 
<!-- /Version Information -->



## Overview

The API Key authentication feature of JAiRouter provides a secure access control mechanism for the system. Through API Keys, you can control who can access your AI model services and assign different permission levels to different users.

## Features

- **Multi-level Permission Control**: Supports different permission levels such as admin, read, write, and delete
- **Expiration Time Management**: Supports setting expiration times for API Keys
- **Usage Statistics**: Records usage statistics for each API Key
- **Cache Optimization**: Supports Redis and local caching to improve authentication performance
- **Dynamic Management**: Supports adding, deleting, and updating API Keys at runtime
- **Persistent Storage**: Supports storing API Key information in H2 database
- **H2 Database Default Storage**: H2 database is now the default persistent storage method for API Key data, providing better performance and reliability

## Quick Start

### 1. Enable API Key Authentication

Enable security features in [application.yml](file://D:\IdeaProjects\model-router\target\classes\application.yml):

```yaml
jairouter:
  security:
    enabled: true
    api-key:
      enabled: true
      header-name: "X-API-Key"
```

### 2. Configure API Key

```yaml
jairouter:
  security:
    api-key:
      keys:
        - key-id: "admin-key-001"
          key-value: "${ADMIN_API_KEY}"
          description: "Administrator API Key"
          permissions: ["admin", "read", "write", "delete"]
          expires-at: "2025-12-31T23:59:59"
          enabled: true
```

### 3. Client Usage

Add the API Key to the HTTP request header:

```bash
curl -H "X-API-Key: your-api-key-here" \
     -X POST \
     -H "Content-Type: application/json" \
     -d '{"model": "gpt-3.5-turbo", "messages": [...]}' \
     http://localhost:8080/v1/chat/completions
```

## Detailed Configuration

### API Key Configuration Parameters

| Parameter | Type | Default Value | Description |
|-----------|------|---------------|-------------|
| [enabled](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\ratelimit\RateLimitConfig.java#L7-L7) | boolean | true | Whether to enable API Key authentication |
| `header-name` | string | "X-API-Key" | Name of the API Key request header |
| `default-expiration-days` | int | 365 | Default number of days until expiration |
| `cache-enabled` | boolean | true | Whether to enable caching |
| `cache-expiration-seconds` | int | 3600 | Cache expiration time (in seconds) |

### API Key Attributes

Each API Key contains the following attributes:

```yaml
- key-id: "unique-key-identifier"      # Unique identifier
  key-value: "actual-api-key-string"   # Actual API Key value
  description: "Key description"        # Description information
  permissions: ["read", "write"]        # Permission list
  expires-at: "2025-12-31T23:59:59"    # Expiration time
  enabled: true                         # Whether enabled
  metadata:                            # Metadata
    created-by: "admin"
    department: "IT"
```

### Permission Level Descriptions

| Permission | Description | Applicable Scenarios |
|------------|-------------|----------------------|
| `read` | Read-only permission, can query models and send inference requests | Regular users, client applications |
| `write` | Write permission, can modify configurations (excluding security configurations) | Service administrators |
| `delete` | Delete permission, can delete configurations and data | Senior administrators |
| `admin` | Administrator permission, can manage all functions including security configurations | System administrators |

## Environment Variable Configuration

For security reasons, it is recommended to set API Keys through environment variables:

### Linux/macOS

```bash
# Production Environment API Key Configuration
export PROD_ADMIN_API_KEY="your-production-admin-api-key-here"
export PROD_SERVICE_API_KEY="your-production-service-api-key-here"
export PROD_READONLY_API_KEY="your-production-readonly-api-key-here"

# API Key Expiration Time Configuration
export PROD_ADMIN_KEY_EXPIRES="2025-12-31T23:59:59"
export PROD_SERVICE_KEY_EXPIRES="2025-12-31T23:59:59"
export PROD_READONLY_KEY_EXPIRES="2025-12-31T23:59:59"
```

### Windows

```cmd
# Production Environment API Key Configuration
set PROD_ADMIN_API_KEY=your-production-admin-api-key-here
set PROD_SERVICE_API_KEY=your-production-service-api-key-here
set PROD_READONLY_API_KEY=your-production-readonly-api-key-here

# API Key Expiration Time Configuration
set PROD_ADMIN_KEY_EXPIRES=2025-12-31T23:59:59
set PROD_SERVICE_KEY_EXPIRES=2025-12-31T23:59:59
set PROD_READONLY_KEY_EXPIRES=2025-12-31T23:59:59
```

### Docker

```bash
# Production Environment Docker Deployment
docker run -d \
  --name jairouter-prod \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e PROD_ADMIN_API_KEY="your-production-admin-api-key-here" \
  -e PROD_SERVICE_API_KEY="your-production-service-api-key-here" \
  -e PROD_READONLY_API_KEY="your-production-readonly-api-key-here" \
  -e PROD_JWT_SECRET="your-production-jwt-secret-here" \
  -e REDIS_HOST="your-redis-host" \
  -e REDIS_PORT="your-redis-port" \
  -e REDIS_PASSWORD="your-redis-password" \
  -v $(pwd)/config:/app/config:ro \
  -v $(pwd)/logs:/app/logs \
  sodlinken/jairouter:latest
```

## Cache Configuration

### Redis Cache

```yaml
jairouter:
  security:
    performance:
      cache:
        redis:
          enabled: true
          host: "localhost"
          port: 6379
          password: "your-redis-password"
          database: 0
```

### Local Cache

```yaml
jairouter:
  security:
    performance:
      cache:
        local:
          enabled: true
          api-key:
            max-size: 1000
            expire-after-write: 3600
```

## API Key Persistence Storage

### Enable API Key Persistence

JAiRouter supports persistent storage of API Keys for enhanced management and monitoring:

```yaml
jairouter:
  security:
    api-key:
      # API Key persistence configuration
      persistence:
        enabled: true
        primary-storage: h2    # h2, redis, memory
        fallback-storage: memory  # memory
        
        # Cleanup configuration
        cleanup:
          enabled: true
          schedule: "0 0 3 * * ?"  # Daily at 3 AM
          retention-days: 365
          batch-size: 1000
        
        # Memory storage configuration
        memory:
          max-keys: 10000
          cleanup-threshold: 0.8  # 80% triggers cleanup
          lru-enabled: true
          
        # H2 database storage configuration
        h2:
          table-name: "api_keys"  # Table name
          max-batch-size: 1000    # Maximum batch size
```

### API Key Management Features

With persistence enabled, you can:

1. **Track API Keys**: Monitor all API Keys and their status
2. **Lifecycle Management**: Automatic status updates and cleanup
3. **Enhanced Security**: Persistent storage with H2 database support
4. **Audit Trail**: Complete audit logging of API Key operations
5. **Performance Monitoring**: Metrics and health checks for API Key operations

### H2 Database Storage Advantages

Using H2 database storage for API Keys provides the following advantages:

1. **Default Storage Method**: H2 database is now the default storage method for API Keys
2. **Persistence**: Data is not lost when the application restarts
3. **High Performance**: Embedded database with no network overhead
4. **Easy Maintenance**: Single database file, easy to backup
5. **Powerful Queries**: Support for complex SQL queries
6. **Transaction Support**: Ensures data consistency
7. **Visual Management**: H2 console for easy debugging
8. **Production Ready**: Meets production environment requirements

### H2 Database Table Structure

API Keys are stored in the H2 database in the following tables:

#### api_keys Table

| Field | Type | Description |
|-------|------|-------------|
| id | BIGINT | Primary key |
| key_id | VARCHAR(255) | Key ID |
| key_value_hash | VARCHAR(500) | Key value hash (no plaintext storage) |
| description | VARCHAR(1000) | Description |
| permissions | TEXT | Permission list (JSON) |
| expires_at | TIMESTAMP | Expiration time |
| enabled | BOOLEAN | Whether enabled |
| created_at | TIMESTAMP | Creation time |
| updated_at | TIMESTAMP | Update time |
| metadata | TEXT | Metadata (JSON) |
| usage_statistics | TEXT | Usage statistics (JSON) |

#### Index Optimization

The system automatically creates the following indexes to improve query performance:

- `idx_apikey_key_id`: Key ID index
- `idx_apikey_enabled`: Enabled status index
- `idx_apikey_expires_at`: Expiration time index
- `idx_apikey_created_at`: Creation time index

### API Key Storage Structure

API Keys are stored with the following metadata:

```json
{
  "id": "key-uuid-123",
  "keyId": "admin-key-001",
  "keyValueHash": "sha256-hash-of-key",
  "description": "Administrator API Key",
  "permissions": ["admin", "read", "write", "delete"],
  "expiresAt": "2025-12-31T23:59:59",
  "enabled": true,
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-01-15T10:30:00Z",
  "metadata": {
    "createdBy": "admin",
    "department": "IT"
  },
  "usageStatistics": {
    "totalRequests": 1000,
    "successfulRequests": 950,
    "failedRequests": 50,
    "lastUsedAt": "2025-01-15T10:30:00Z"
  }
}
```

## Monitoring and Auditing

### Usage Statistics

The system automatically records usage statistics for each API Key:

- Total number of requests
- Number of successful requests
- Number of failed requests
- Last usage time
- Daily usage volume

### Audit Logs

When audit functionality is enabled, the system records all authentication-related events:

```yaml
jairouter:
  security:
    audit:
      enabled: true
      event-types:
        api-key-created: true
        api-key-used: true
        api-key-revoked: true
        api-key-expired: true
        authentication-success: true
        authentication-failure: true
```

### Monitoring Metrics

The system provides the following Prometheus metrics:

- `jairouter_security_authentication_attempts_total`: Total number of authentication attempts
- `jairouter_security_authentication_successes_total`: Total number of successful authentications
- `jairouter_security_authentication_failures_total`: Total number of failed authentications
- `jairouter_security_authentication_duration_seconds`: Authentication duration
- `jairouter_security_api_keys_created_total`: Total number of API Keys created
- `jairouter_security_api_keys_used_total`: Total API Key usage
- `jairouter_security_api_keys_revoked_total`: Total number of API Keys revoked

## Security Auditing and Monitoring

### Enhanced Audit Configuration

```yaml
jairouter:
  security:
    # Enhanced audit configuration
    audit:
      enabled: true
      log-level: "INFO"
      include-request-body: false
      include-response-body: false
      retention-days: 90
      
      # API Key operations auditing
      api-key-operations:
        enabled: true
        log-key-details: false  # Don't log full keys
        log-usage-patterns: true
        log-ip-address: true
      
      # JWT operations auditing
      jwt-operations:
        enabled: true
        log-token-details: false  # Don't log full tokens
        log-user-agent: true
        log-ip-address: true
      
      # Security events auditing
      security-events:
        enabled: true
        suspicious-activity-detection: true
        alert-thresholds:
          failed-auth-per-minute: 10
          api-key-usage-per-minute: 100
          token-revoke-per-minute: 5
      
      # Audit storage configuration
      storage:
        type: "h2"              # Options: h2, file, database
        h2:
          table-name: "security_audit_events"  # H2 table name
        file-path: "logs/security-audit.log"
        rotation:
          max-file-size: "100MB"
          max-files: 30
        # Optional: database storage
        database:
          enabled: false
          table-name: "security_audit_events"
```

### Audit Event Types

The system logs the following API Key and JWT events:

#### API Key Events
- **Key Created**: When a new API key is generated
- **Key Used**: When an API key is used for authentication
- **Key Revoked**: When an API key is revoked
- **Key Expired**: When an API key expires

#### JWT Token Events
- **Token Issued**: When a new JWT token is created
- **Token Refreshed**: When an access token is refreshed
- **Token Revoked**: When a token is manually revoked
- **Token Validated**: When a token is validated (success/failure)
- **Token Expired**: When a token naturally expires

#### Security Events
- **Suspicious Activity**: Unusual authentication patterns
- **Failed Authentication**: Failed login attempts
- **Bulk Operations**: Mass token/key operations

### Audit Event Structure

```json
{
  "id": "audit-event-uuid",
  "type": "API_KEY_USED",
  "userId": "user123",
  "resourceId": "key-uuid-123",
  "action": "USE_KEY",
  "details": "Using API Key to access service",
  "ipAddress": "192.168.1.100",
  "userAgent": "Mozilla/5.0...",
  "success": true,
  "timestamp": "2025-01-15T10:30:00Z",
  "metadata": {
    "keyId": "admin-key-001",
    "endpoint": "/v1/chat/completions",
    "method": "POST"
  }
}
```

### Security Report Generation

Generate comprehensive security reports:

```bash
# Get security report for the last 30 days
curl -X GET "http://localhost:8080/api/security/audit/report?from=2025-01-01&to=2025-01-31" \
     -H "Authorization: Bearer admin_token"
```

Response includes:
- Total API Key and JWT operations
- Failed authentication statistics
- Suspicious activity alerts
- Top IP addresses and users
- Security event trends

## Best Practices

### 1. API Key Security

- **Use Strong Keys**: API Keys should be long and random, at least 32 characters recommended
- **Regular Rotation**: Regularly rotate API Keys, especially when personnel changes occur
- **Environment Variables**: Do not hardcode API Keys in configuration files, use environment variables
- **Principle of Least Privilege**: Only grant necessary permissions

### 2. Expiration Time Management

- **Reasonable Expiration Settings**: Set appropriate expiration times based on usage scenarios
- **Early Renewal**: Renew API Keys in a timely manner before they expire
- **Expiration Monitoring**: Set up alerts to monitor API Keys that are about to expire

### 3. Permission Management

- **Hierarchical Authorization**: Assign different permission levels based on user roles
- **Regular Review**: Regularly review API Key permission assignments
- **Timely Revocation**: Disable or delete unused API Keys promptly

### 4. Performance Optimization

- **Enable Caching**: Enable Redis caching in production environments
- **Reasonable Cache Time Settings**: Set cache expiration times based on security requirements and performance needs
- **Monitor Cache Hit Rate**: Monitor cache performance and adjust configurations in a timely manner

## Troubleshooting

### Common Issues

#### 1. Authentication Failure

**Issue**: Client receives 401 Unauthorized error

**Possible Causes**:
- Incorrect API Key
- Expired API Key
- Disabled API Key
- Incorrect request header name

**Solutions**:
1. Check if the API Key is correct
2. Check if the API Key has expired
3. Check the `header-name` setting in configuration
4. View audit logs for detailed error information

#### 2. Insufficient Permissions

**Issue**: Client receives 403 Forbidden error

**Possible Causes**:
- Insufficient API Key permissions
- Accessed an interface requiring higher permissions

**Solutions**:
1. Check API Key permission configuration
2. Add necessary permissions to the API Key
3. Use an API Key with sufficient permissions

#### 3. Performance Issues

**Issue**: Authentication response time is too long

**Possible Causes**:
- Cache not enabled or improperly configured
- Redis connection issues
- Too many API Keys

**Solutions**:
1. Enable and properly configure caching
2. Check Redis connection status
3. Optimize API Key configuration
4. Monitor authentication performance metrics

### Debugging Tips

#### 1. Enable Detailed Logging

```yaml
logging:
  level:
    org.unreal.modelrouter.security: DEBUG
```

#### 2. Check Audit Logs

```bash
tail -f logs/security-audit.log | grep authentication
```

#### 3. Monitor Metrics

Access the Prometheus metrics endpoint:
```
http://localhost:8080/actuator/prometheus
```

#### 4. Health Check

Check system health status:
```
http://localhost:8080/actuator/health
```

## Example Configurations

### Development Environment

```yaml
jairouter:
  security:
    enabled: true
    api-key:
      enabled: true
      default-expiration-days: 30
      keys:
        - key-id: "dev-admin"
          key-value: "dev-admin-key-12345"
          permissions: ["admin", "read", "write", "delete"]
          expires-at: "2025-12-31T23:59:59"
```

### Production Environment

```yaml
jairouter:
  security:
    enabled: true
    api-key:
      enabled: true
      cache-enabled: true
      keys:
        - key-id: "prod-admin"
          key-value: "${PROD_ADMIN_API_KEY}"
          permissions: ["admin", "read", "write", "delete"]
          expires-at: "${PROD_ADMIN_KEY_EXPIRES}"
        - key-id: "prod-service"
          key-value: "${PROD_SERVICE_API_KEY}"
          permissions: ["read", "write"]
          expires-at: "${PROD_SERVICE_KEY_EXPIRES}"
    performance:
      cache:
        redis:
          enabled: true
          host: "${REDIS_HOST}"
          port: "${REDIS_PORT}"
          password: "${REDIS_PASSWORD}"
```

## Related Documentation

- [JWT Authentication Configuration](jwt-authentication.md)
- [Data Sanitization Rules Configuration](data-sanitization.md)
- [Security Feature Troubleshooting Guide](troubleshooting.md)