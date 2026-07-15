# Security Blacklist Management

<!-- 版本信息 -->
> **Document Version**: 1.8.0
> **最后更新**: 2026-06-10
> **Git 提交**: 2cba097
> **作者**: Lincoln
<!-- /版本信息 -->

## Overview

JAiRouter's security blacklist feature provides defensive capabilities, allowing administrators to block specific IP addresses, user accounts, or tokens from accessing the system. Blacklist management effectively prevents malicious behavior and unauthorized access.

## Features

### Core Features

- **Multiple Blacklist Types**: Support for IP, user, and token blacklists
- **Expiration Setting**: Configurable expiration times for blacklist entries
- **Auto Cleanup**: Regular cleanup of expired blacklist entries
- **Statistics Analysis**: Blacklist statistics and trend analysis
- **Audit Logging**: All blacklist operations logged for traceability

### Blacklist Types

| Type | Description | Use Case | Verification Point |
|------|-------------|----------|---------------------|
| **IP Blacklist** | Block specific IP addresses | Prevent DDoS attacks, malicious crawlers | Request entry |
| **User Blacklist** | Block specific users | Disable suspicious accounts | JWT validation |
| **Token Blacklist** | Block specific JWT tokens | Revoke stolen tokens, forced logout | JWT validation |

## Quick Start

### 1. Access via Admin Console

Access the blacklist management console at `/admin/security/blacklist` to:

- View current blacklist list
- Add new blacklist entries
- Edit or remove existing entries
- View blacklist statistics

### 2. API Operations

#### Add IP Blacklist

```bash
curl -X POST "http://localhost:8080/api/security/blacklist" \
     -H "Authorization: Bearer admin_token" \
     -H "Content-Type: application/json" \
     -d '{
       "type": "IP",
       "value": "192.168.1.100",
       "reason": "DDoS attack source",
       "expiresAt": "2026-05-10T00:00:00Z"
     }'
```

## Blacklist Entry Structure

### Required Fields

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `type` | string | Yes | Blacklist type: IP, USER, TOKEN |
| `value` | string | Yes | Blacklist value (IP address, username, token) |
| `reason` | string | Yes | Reason for blacklisting |
| `expiresAt` | timestamp | No | Expiration time; permanent if not specified |
| `createdAt` | timestamp | No | Creation time (auto-filled by system) |

### Blacklist Examples

#### Single IP

```json
{
  "type": "IP",
  "value": "192.168.1.100"
}
```

#### CIDR Network Segment

```json
{
  "type": "IP",
  "value": "10.0.0.0/8",
  "reason": "Internal network block"
}
```

#### IP Range

```json
{
  "type": "IP",
  "value": "172.16.0.0-172.16.255.255",
  "reason": "Suspicious IP range"
}
```

#### User Blacklist

```json
{
  "type": "USER",
  "value": "malicious_user",
  "reason": "Malicious activity detected",
  "expiresAt": "2026-06-01T00:00:00Z"
}
```

#### Token Blacklist

```json
{
  "type": "TOKEN",
  "value": "token-uuid-123",
  "reason": "Token compromised",
  "expiresAt": "2026-04-01T00:00:00Z"
}
```

## API Reference

### Add Blacklist Entry

```http
POST /api/security/blacklist
Authorization: Bearer {token}
Content-Type: application/json
```

Request Body:
```json
{
  "type": "IP",
  "value": "192.168.1.100",
  "reason": "Attack source",
  "expiresAt": "2026-05-10T00:00:00Z"
}
```

Response:
```json
{
  "success": true,
  "message": "Blacklist entry added successfully",
  "data": {
    "id": "entry-uuid",
    "type": "IP",
    "value": "192.168.1.100",
    "reason": "Attack source",
    "createdAt": "2026-04-10T10:30:00Z",
    "expiresAt": "2026-05-10T00:00:00Z"
  }
}
```

### Get Blacklist List

```http
GET /api/security/blacklist?type={type}&page={page}&size={size}&status={status}
Authorization: Bearer {token}
```

Query Parameters:
- `type`: Blacklist type filter (IP/USER/TOKEN)
- `page`: Page number, default 0
- `size`: Page size, default 20
- `status`: Status filter (ACTIVE/EXPIRED)

Response:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "entry-id",
        "type": "IP",
        "value": "192.168.1.100",
        "reason": "Attack source",
        "createdAt": "2026-04-10T10:30:00Z",
        "expiresAt": "2026-05-10T00:00:00Z",
        "status": "ACTIVE"
      }
    ],
    "totalElements": 100,
    "totalPages": 5,
    "size": 20,
    "number": 0
  }
}
```

### Get Single Entry

```http
GET /api/security/blacklist/{entryId}
Authorization: Bearer {token}
```

### Update Blacklist Entry

```http
PUT /api/security/blacklist/{entryId}
Authorization: Bearer {token}
Content-Type: application/json
```

Request Body:
```json
{
  "reason": "Updated reason",
  "expiresAt": "2026-06-10T00:00:00Z"
}
```

### Delete Blacklist Entry

```http
DELETE /api/security/blacklist/{entryId}
Authorization: Bearer {token}
```

### Batch Add Blacklist

```http
POST /api/security/blacklist/batch
Authorization: Bearer {token}
Content-Type: application/json
```

Request Body:
```json
{
  "entries": [
    {"type": "IP", "value": "192.168.1.100", "reason": "Attack source"},
    {"type": "IP", "value": "192.168.1.101", "reason": "Attack source"},
    {"type": "USER", "value": "malicious_user", "reason": "Malicious activity"}
  ]
}
```

Response:
```json
{
  "success": true,
  "data": {
    "totalEntries": 3,
    "addedCount": 2,
    "skippedCount": 1,
    "errors": []
  }
}
```

### Get Blacklist Statistics

```http
GET /api/security/blacklist/stats
Authorization: Bearer {token}
```

Response:
```json
{
  "success": true,
  "data": {
    "totalEntries": 150,
    "activeEntries": 120,
    "expiredEntries": 30,
    "byType": {
      "IP": 80,
      "USER": 30,
      "TOKEN": 40
    },
    "recentlyBlocked": 15,
    "recentlyExpired": 5,
    "topReasons": [
      {"reason": "Attack source", "count": 50},
      {"reason": "Malicious activity", "count": 30},
      {"reason": "Token compromise", "count": 20}
    ]
  }
}
```

### Cleanup Expired Entries

```http
POST /api/security/blacklist/cleanup
Authorization: Bearer {token}
```

Response:
```json
{
  "success": true,
  "data": {
    "cleanedCount": 30,
    "cleanedAt": "2026-04-10T10:30:00Z"
  }
}
```

## Validation Mechanism

### IP Blacklist Validation

For each request entering the system:

1. Extract client IP (handling proxy scenarios)
2. Check if IP is in blacklist
3. Support matching for single IP, CIDR network, and IP range
4. If matched, return 403 Forbidden

### User Blacklist Validation

When validating JWT tokens:

1. Parse username from token
2. Check if user is in blacklist
3. If matched, return 401 Unauthorized

### Token Blacklist Validation

When validating JWT tokens:

1. Extract token ID (jti claim)
2. Check if token ID is in blacklist
3. If matched, return 401 Unauthorized

## Auto Cleanup

Expired blacklist entries are automatically cleaned up.

### Cleanup Configuration

```yaml
jairouter:
  security:
    blacklist:
      cleanup:
        enabled: true
        schedule: "0 0 3 * * ?"  # Daily at 3 AM
        retention-days: 30        # Retain expired entries for audit
```

### Cleanup Behavior

- Expired entries where `expiresAt` time has passed are removed
- Optionally retain expired entries for historical analysis
- Batch processing to avoid large single operations

## Monitoring and Alerts

### Monitoring Metrics

- `jairouter_security_blacklist_total`: Total blacklist entries
- `jairouter_security_blacklist_active`: Active blacklist entries
- `jairouter_security_blacklist_blocked_requests`: Blocked requests by blacklist
- `jairouter_security_blacklist_by_type`: Entry count by type

### Alert Configuration

```yaml
jairouter:
  security:
    alerts:
      blacklist:
        enabled: true
        # Alert when too many blacklist entries
        max-entries-threshold: 100
        # Alert for sudden increase in requests from same IP
        ip-request-rate-threshold: 1000
```

## Best Practices

### 1. Set Appropriate Expiration Times

- **Temporary blocks**: Set short expiration (hours to days)
- **Permanent blocks**: Set longer expiration or no expiration
- **Regular review**: Periodically review and remove unnecessary entries

### 2. Use Batch Operations

- Use batch API for large numbers of entries
- Periodically export blacklist for backup
- Use CIDR networks for IP ranges

### 3. Integrate with Other Security Features

- Combine with rate limiting to prevent brute force attacks
- Use with JWT blacklist for token management
- Enable audit logging for traceability

### 4. Document Blacklist Reasons

- Always add a reason when adding blacklist entries
- Facilitates future review and appeals
- Generate audit reports with blacklist history

## Troubleshooting

### Common Issues

#### 1. IP Blacklist Not Working

**Possible Causes**:
- IP extraction incorrect in proxy environment
- CIDR format incorrect

**Solution**:
1. Check `X-Forwarded-For` or `X-Real-IP` header configuration
2. Verify IP format is correct

#### 2. Blacklist Entry Cannot Be Added

**Possible Causes**:
- Duplicate entry already exists
- Blacklist not synced to validation service

**Solution**:
1. Check if entry already exists
2. Clear cache if needed

#### 3. Too Many Blacklist Entries

**Possible Causes**:
- Automated attacks generating many entries
- No expiration time set

**Solution**:
1. Run auto cleanup
2. Set appropriate expiration
3. Use CIDR networks instead of individual IPs

## Related Documentation

- [API Key Management Guide](api-key-management.md)
- [JWT Authentication Configuration](jwt-authentication.md)
- [Security Troubleshooting Guide](troubleshooting.md)
- [Audit Log Management](audit-log-management.md)