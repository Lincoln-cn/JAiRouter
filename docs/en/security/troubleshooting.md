# Security Feature Troubleshooting Guide

<!-- 版本信息 -->
> **文档版本**: 1.0.2  
> **最后更新**: 2026-05-21  
> **Git 提交**: 61384b4a  
> **作者**: 
<!-- /版本信息 -->



## Overview

This document provides diagnosis and solutions for common JAiRouter security feature issues, including authentication failures, sanitization problems, performance issues, and more.

## Quick Diagnosis

### 1. Check Security Feature Status

```bash
# Check system health
curl http://localhost:8080/actuator/health

# Check security configuration
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/status
```

### 2. View Logs

```bash
# View application logs
tail -f logs/jairouter.log

# View security audit logs
tail -f logs/security-audit.log

# View error logs
grep ERROR logs/jairouter.log
```

### 3. Check Monitoring Metrics

```bash
# View Prometheus metrics
curl http://localhost:8080/actuator/prometheus | grep security

# View authentication metrics
curl http://localhost:8080/actuator/prometheus | grep authentication

# View sanitization metrics
curl http://localhost:8080/actuator/prometheus | grep sanitization
```

## Authentication Issues

### Issue 1: API Key Authentication Failure

#### Symptoms
- Client receives `401 Unauthorized` error
- Logs show `Invalid API Key` or `API Key not found`

#### Possible Causes
1. API Key is incorrect or does not exist
2. API Key has expired
3. API Key has been disabled
4. Request header name is incorrect
5. API Key format is wrong

#### Diagnosis Steps

```bash
# 1. Check API Key configuration
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/api-keys

# 2. Verify request header
curl -v -H "X-API-Key: your-api-key" \
     http://localhost:8080/v1/models

# 3. Check API Key status
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/api-keys/your-key-id/status
```

#### Solutions

1. **Check API Key value**
```yaml
# Ensure configuration is correct
jairouter:
  security:
    api-key:
      keys:
        - key-id: "test-key"
          key-value: "correct-api-key-value"
          enabled: true
```

2. **Check expiration time**
```bash
# Update expiration time
curl -X PUT -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"expires_at": "2025-12-31T23:59:59"}' \
     http://localhost:8080/admin/security/api-keys/your-key-id
```

3. **Enable API Key**
```bash
curl -X PUT -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"enabled": true}' \
     http://localhost:8080/admin/security/api-keys/your-key-id
```

### Issue 2: JWT Authentication Failure

#### Symptoms
- Client receives `401 Unauthorized` error
- Logs show `Invalid JWT token` or `JWT signature verification failed`

#### Possible Causes
1. JWT token format is incorrect
2. Signature verification failed
3. Token has expired
4. Token is in the blacklist
5. Secret key configuration is wrong

#### Diagnosis Steps

```bash
# 1. Parse JWT token
echo "your-jwt-token" | cut -d'.' -f2 | base64 -d | jq

# 2. Check token status
curl -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"token": "your-jwt-token"}' \
     http://localhost:8080/admin/security/jwt/validate

# 3. Check blacklist
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/jwt/blacklist
```

#### Solutions

1. **Check JWT configuration**
```yaml
jairouter:
  security:
    jwt:
      enabled: true
      secret: "${JWT_SECRET}"  # Ensure secret is correct
      algorithm: "HS256"
```

2. **Refresh token**
```bash
curl -X POST -H "Content-Type: application/json" \
     -d '{"refresh_token": "your-refresh-token"}' \
     http://localhost:8080/auth/refresh
```

3. **Clear blacklist**
```bash
curl -X DELETE -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/jwt/blacklist/clear
```

### Issue 3: Insufficient Permissions

#### Symptoms
- Client receives `403 Forbidden` error
- Logs show `Insufficient permissions`

#### Diagnosis Steps

```bash
# Check user permissions
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/permissions/your-user-id

# Check endpoint permission requirements
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/endpoints
```

#### Solutions

```bash
# Update user permissions
curl -X PUT -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"permissions": ["read", "write", "admin"]}' \
     http://localhost:8080/admin/security/api-keys/your-key-id/permissions
```

## Data Sanitization Issues

### Issue 4: Sanitization Not Working

#### Symptoms
- Sensitive data is not being sanitized
- Logs show sanitization rules not matched

#### Possible Causes
1. Sanitization feature is not enabled
2. Regular expression does not match
3. User is in the whitelist
4. Rule priority issue

#### Diagnosis Steps

```bash
# 1. Check sanitization configuration
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/sanitization/config

# 2. Test sanitization rules
curl -X POST -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"text": "My phone number is 13812345678", "rules": ["phone"]}' \
     http://localhost:8080/admin/security/sanitization/test

# 3. Check whitelist
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/sanitization/whitelist
```

#### Solutions

1. **Enable sanitization feature**
```yaml
jairouter:
  security:
    sanitization:
      request:
        enabled: true
      response:
        enabled: true
```

2. **Fix regular expression**
```yaml
jairouter:
  security:
    sanitization:
      request:
        pii-patterns:
          - "\\b(?:13[0-9]|14[5-9]|15[0-3,5-9]|16[2,5,6,7]|17[0-8]|18[0-9]|19[1,3,5,8,9])\\d{8}\\b"
```

3. **Check whitelist**
```bash
# Remove user from whitelist
curl -X DELETE -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/sanitization/whitelist/users/your-user-id
```

### Issue 5: False Positive Sanitization

#### Symptoms
- Normal data is incorrectly sanitized
- Business functionality is affected

#### Diagnosis Steps

```bash
# Test sanitization result for specific text
curl -X POST -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"text": "your-test-text"}' \
     http://localhost:8080/admin/security/sanitization/test
```

#### Solutions

1. **Make regular expression more precise**
```yaml
# Before: Too broad
pii-patterns:
  - "\\d+"  # Matches all numbers

# After: Precise matching
pii-patterns:
  - "\\b1[3-9]\\d{9}\\b"  # Only matches phone numbers
```

2. **Adjust rule priority**
```yaml
jairouter:
  security:
    sanitization:
      request:
        rule-priorities:
          specific-pattern: 1    # High priority
          general-pattern: 10    # Low priority
```

3. **Add exception rules**
```yaml
jairouter:
  security:
    sanitization:
      request:
        exception-patterns:
          - "Order number:\\d+"  # Order numbers not sanitized
```

## Performance Issues

### Issue 6: Slow Authentication Response

#### Symptoms
- Authentication takes too long
- System response is slow

#### Diagnosis Steps

```bash
# Check authentication performance metrics
curl http://localhost:8080/actuator/prometheus | grep authentication_duration

# Check cache hit rate
curl http://localhost:8080/actuator/prometheus | grep cache_hit_rate

# Check thread pool status
curl http://localhost:8080/actuator/prometheus | grep thread_pool
```

#### Solutions

1. **Enable caching**
```yaml
jairouter:
  security:
    performance:
      cache:
        redis:
          enabled: true
        local:
          enabled: true
```

2. **Optimize thread pool**
```yaml
jairouter:
  security:
    performance:
      authentication:
        async-enabled: true
        thread-pool-size: 20
        timeout-ms: 3000
```

3. **Reduce API Key count**
```bash
# Clean up unused API Keys
curl -X DELETE -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/api-keys/unused-key-id
```

### Issue 7: Sanitization Performance Issues

#### Symptoms
- Sanitization operation takes too long
- Memory usage is too high

#### Diagnosis Steps

```bash
# Check sanitization performance metrics
curl http://localhost:8080/actuator/prometheus | grep sanitization_duration

# Check memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Check regex cache
curl http://localhost:8080/actuator/prometheus | grep regex_cache
```

#### Solutions

1. **Enable parallel processing**
```yaml
jairouter:
  security:
    performance:
      sanitization:
        parallel-enabled: true
        thread-pool-size: 8
```

2. **Optimize regular expressions**
```yaml
# Avoid complex regular expressions
pii-patterns:
  - "\\d{11}"  # Simple pattern
  # Avoid: (?=.*\\d)(?=.*[a-z])(?=.*[A-Z]).{8,}  # Complex pattern
```

3. **Enable streaming processing**
```yaml
jairouter:
  security:
    performance:
      sanitization:
        streaming-threshold: 1048576  # 1MB
```

## Configuration Issues

### Issue 8: Configuration Not Taking Effect

#### Symptoms
- Features don't change after modifying configuration
- System uses default configuration

#### Diagnosis Steps

```bash
# Check current configuration
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/config/current

# Check configuration file
cat src/main/resources/application.yml | grep -A 20 security

# Check environment variables
env | grep -i security
```

#### Solutions

1. **Restart application**
```bash
# Some configurations require restart to take effect
./mvnw spring-boot:stop
./mvnw spring-boot:run
```

2. **Check configuration priority**
```yaml
# Ensure configuration is in the correct environment file
# application-prod.yml has higher priority than application.yml
```

3. **Validate YAML format**
```bash
# Use YAML validation tool
yamllint src/main/resources/application.yml
```

### Issue 9: Environment Variables Not Working

#### Symptoms
- Environment variable values are not read
- Default values are used instead of environment variable values

#### Solutions

1. **Check environment variable format**
```bash
# Correct format
export JWT_SECRET="your-secret-here"

# Wrong format
export jwt_secret="your-secret-here"  # Case error
```

2. **Check configuration reference**
```yaml
# Correct reference
secret: "${JWT_SECRET}"

# Wrong reference
secret: "$JWT_SECRET"  # Missing braces
```

3. **Reload environment variables**
```bash
source ~/.bashrc
# Or
source ~/.profile
```

## Monitoring and Alerting Issues

### Issue 10: Missing Monitoring Metrics

#### Symptoms
- Prometheus metrics not showing
- Monitoring dashboard has no data

#### Solutions

1. **Enable monitoring feature**
```yaml
jairouter:
  security:
    monitoring:
      enabled: true
      metrics:
        authentication:
          enabled: true
        sanitization:
          enabled: true
```

2. **Check Actuator configuration**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

3. **Verify metrics endpoint**
```bash
curl http://localhost:8080/actuator/prometheus
```

### Issue 11: Alerts Not Triggering

#### Symptoms
- Threshold reached but no alert received
- Alert configuration not working

#### Solutions

1. **Check alert configuration**
```yaml
jairouter:
  security:
    monitoring:
      alerts:
        enabled: true
        thresholds:
          authentication-failure-rate: 0.1
```

2. **Test alert notification**
```bash
curl -X POST -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/alerts/test
```

3. **Check notification configuration**
```yaml
jairouter:
  security:
    monitoring:
      alerts:
        notifications:
          email:
            enabled: true
            recipients: ["admin@example.com"]
```

## Debugging Tools and Tips

### 1. Enable Verbose Logging

```yaml
logging:
  level:
    org.unreal.modelrouter.security: DEBUG
    org.springframework.security: DEBUG
    org.springframework.web: DEBUG
```

### 2. Use Debug Endpoints

```bash
# Security status check
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/debug

# Configuration check
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/config/validate

# Performance analysis
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/performance/analyze
```

### 3. Log Analysis Script

```bash
#!/bin/bash
# security-log-analyzer.sh

LOG_FILE="logs/jairouter.log"
AUDIT_FILE="logs/security-audit.log"

echo "=== Authentication Failure Statistics ==="
grep "authentication failed" $LOG_FILE | wc -l

echo "=== Recent Authentication Errors ==="
grep "authentication failed" $LOG_FILE | tail -10

echo "=== Sanitization Operation Statistics ==="
grep "sanitization applied" $AUDIT_FILE | wc -l

echo "=== Performance Issue Check ==="
grep "timeout\|slow\|performance" $LOG_FILE | tail -10
```

### 4. Health Check Script

```bash
#!/bin/bash
# security-health-check.sh

BASE_URL="http://localhost:8080"
ADMIN_TOKEN="your-admin-token"

echo "=== System Health Check ==="
curl -s "$BASE_URL/actuator/health" | jq '.status'

echo "=== Security Feature Status ==="
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
     "$BASE_URL/admin/security/status" | jq

echo "=== Authentication Performance Metrics ==="
curl -s "$BASE_URL/actuator/prometheus" | \
     grep "jairouter_security_authentication_duration_seconds"

echo "=== Sanitization Performance Metrics ==="
curl -s "$BASE_URL/actuator/prometheus" | \
     grep "jairouter_security_sanitization_duration_seconds"
```

## Common Commands Reference

### Configuration Management

```bash
# Reload configuration
curl -X POST -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/config/reload

# Validate configuration
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/config/validate

# Backup configuration
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/config/backup > config-backup.json
```

### Cache Management

```bash
# Clear authentication cache
curl -X DELETE -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/cache/authentication

# Clear sanitization cache
curl -X DELETE -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/cache/sanitization

# View cache statistics
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/security/cache/stats
```

### Log Management

```bash
# Set log level
curl -X POST -H "Authorization: Bearer admin-token" \
     -H "Content-Type: application/json" \
     -d '{"level": "DEBUG"}' \
     http://localhost:8080/admin/logging/org.unreal.modelrouter.security

# Download logs
curl -H "Authorization: Bearer admin-token" \
     http://localhost:8080/admin/logs/security-audit.log > audit.log
```

## Contact Support

If the above solutions cannot resolve your issue, please contact technical support:

- **GitHub Issues**: https://github.com/Lincoln-cn/JAiRouter/issues
- **Email Support**: support@jairouter.com
- **Documentation Center**: https://jairouter.com

When submitting an issue, please include the following information:
1. Detailed description of the problem
2. Error logs
3. Configuration file (after sanitization)
4. System environment information
5. Steps to reproduce

## Related Documentation

- [API Key Management Guide](api-key-management.md)
- [JWT Authentication Configuration](jwt-authentication.md)
- [Data Sanitization Rule Configuration](data-sanitization.md)
- [Security Monitoring and Alerts](../monitoring/alerts.md)
