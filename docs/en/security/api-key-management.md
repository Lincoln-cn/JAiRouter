# API Key Management Guide

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**:   
> **作者**: 
<!-- /版本信息 -->



## Overview

The API Key authentication feature of JAiRouter provides a secure access control mechanism for the system. Through API Keys, you can control who can access your AI model services and assign different permission levels to different users.

## Features

- **Multi-level Permission Control**: Supports different permission levels such as admin, read, write, and delete
- **Expiration Time Management**: Supports setting expiration times for API Keys
- **Usage Statistics**: Records usage statistics for each API Key
- **Cache Optimization**: Supports Redis and local caching to improve authentication performance
- **Dynamic Management**: Supports adding, deleting, and updating API Keys at runtime

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
export ADMIN_API_KEY="your-admin-api-key-here"
export USER_API_KEY="your-user-api-key-here"
```

### Windows

```cmd
set ADMIN_API_KEY=your-admin-api-key-here
set USER_API_KEY=your-user-api-key-here
```

### Docker

```bash
docker run -e ADMIN_API_KEY="your-admin-api-key-here" \
           -e USER_API_KEY="your-user-api-key-here" \
           jairouter:latest
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
        authentication-success: true
        authentication-failure: true
```

### Monitoring Metrics

The system provides the following Prometheus metrics:

- `jairouter_security_authentication_attempts_total`: Total number of authentication attempts
- `jairouter_security_authentication_successes_total`: Total number of successful authentications
- `jairouter_security_authentication_failures_total`: Total number of failed authentications
- `jairouter_security_authentication_duration_seconds`: Authentication duration

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
