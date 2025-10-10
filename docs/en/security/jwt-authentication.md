# JWT Authentication Configuration Guide

<!-- Version Information -->
> **Document Version**: 1.0.0  
> **Last Updated**: 2025-08-19  
> **Git Commit**:   
> **Author**:
<!-- /Version Information -->

## Overview

JAiRouter supports JWT (JSON Web Token) authentication, which can be integrated with existing identity authentication systems. JWT authentication provides a stateless authentication mechanism and supports token refresh and blacklist functionalities.

## Features

- **Standard JWT Support**: Fully compatible with RFC 7519 standard
- **Multiple Signing Algorithms**: Supports HS256, HS384, HS512, RS256, RS384, RS512
- **Token Refresh**: Supports access token and refresh token mechanisms
- **Blacklist Functionality**: Supports token revocation and logout
- **Coexistence with API Key**: Can be used alongside API Key authentication
- **Username/Password Login**: Supports obtaining JWT tokens via username and password

## Quick Start

### 1. Enable JWT Authentication

```yaml
jairouter:
  security:
    enabled: true
    jwt:
      enabled: true
      secret: "${JWT_SECRET}"
      algorithm: "HS256"
      expiration-minutes: 60
```

**Configuration Explanation**  
To **change the HTTP header used to carry the JWT**, you can add the following when enabling JWT:
 ```yaml
 jwt:
   enabled: true
   jwt-header: "Jairouter_Token"
 ```
In this case, JAiRouter will no longer read the default `Authorization` header but will instead read the custom `Jairouter_Token` header to obtain the token. This is suitable for scenarios where the `Authorization` header is shared with existing systems.

### 2. Set JWT Key

#### Symmetric Key (HS256/HS384/HS512)
```bash
# Production Environment JWT Key Configuration
export PROD_JWT_SECRET="your-very-strong-production-jwt-secret-key-at-least-32-characters-long"
```

#### Symmetric Key (HS256/HS384/HS512)

```bash
# Production Environment JWT Key Configuration
export PROD_JWT_SECRET="your-very-strong-production-jwt-secret-key-at-least-32-characters-long"

# Optional Expiration Time Configuration
export PROD_JWT_EXPIRATION_MINUTES=15
export PROD_JWT_REFRESH_EXPIRATION_DAYS=30
```

#### Asymmetric Key (RS256/RS384/RS512)

```bash
# Production Environment Asymmetric Key Configuration
export JWT_PUBLIC_KEY="-----BEGIN PUBLIC KEY-----\nyour-public-key-here\n-----END PUBLIC KEY-----"
export JWT_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nyour-private-key-here\n-----END PRIVATE KEY-----"
```

### 3. Configure User Accounts

```yaml
jairouter:
  security:
    jwt:
      enabled: true
      secret: "dev-jwt-secret-key-for-development-only-not-for-production"
      algorithm: "HS256"
      expiration-minutes: 60
      refresh-expiration-days: 7
      issuer: "jairouter"
      blacklist-enabled: true
      # User Account Configuration
      accounts:
        - username: "admin"
          password: "{bcrypt}$2a$10$xmZ5S3DY567m5z6vcPVkreKZ885VqWFb1DB5.RgCEvqHLKj0H/G7u"  # BCrypt encrypted password
          roles: [ "ADMIN", "USER" ]
          enabled: true
        - username: "user"
          password: "{noop}user123"  # Plain text password for development, encrypted in production
          roles: [ "USER" ]
          enabled: true
```

### 4. Client Usage

Add the JWT token in the HTTP request header:

```bash
curl -H "Authorization: Bearer your-jwt-token-here" \
     -X POST \
     -H "Content-Type: application/json" \
     -d '{"model": "gpt-3.5-turbo", "messages": [...]}' \
     http://localhost:8080/v1/chat/completions
```

## Login to Obtain JWT Token

### Login Endpoint

```
POST /api/auth/jwt/login
```

### Request Example

```bash
curl -X POST http://localhost:8080/api/auth/jwt/login \
     -H "Content-Type: application/json" \
     -d '{
           "username": "admin",
           "password": "admin123"
         }'
```

### Response Example

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "message": "Login successful",
    "timestamp": "2023-01-01T12:00:00"
  },
  "errorCode": null
}
```

### Error Response

```json
{
  "success": false,
  "message": "Login failed: Incorrect username or password",
  "data": null,
  "errorCode": "LOGIN_FAILED"
}
```

## Detailed Configuration

### JWT Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| [enabled](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\config\TraceConfig.java#L12-L12) | boolean | false | Whether to enable JWT authentication |
| [secret](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\config\SecurityProperties.java#L126-L127) | string | - | JWT signing key (symmetric algorithm) |
| `public-key` | string | - | JWT public key (asymmetric algorithm) |
| `private-key` | string | - | JWT private key (asymmetric algorithm) |
| [algorithm](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\config\SecurityProperties.java#L132-L134) | string | "HS256" | JWT signing algorithm |
| `expiration-minutes` | int | 60 | Access token expiration time (minutes) |
| `refresh-expiration-days` | int | 7 | Refresh token expiration time (days) |
| [issuer](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\config\SecurityProperties.java#L153-L155) | string | "jairouter" | JWT issuer identifier |
| `blacklist-enabled` | boolean | true | Whether to enable blacklist functionality |
| [accounts](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\config\JwtUserProperties.java#L14-L14) | array | [] | User account list |

### Supported Signing Algorithms

#### Symmetric Algorithms (HMAC)

- **HS256**: HMAC using SHA-256
- **HS384**: HMAC using SHA-384
- **HS512**: HMAC using SHA-512

```yaml
jairouter:
  security:
    jwt:
      algorithm: "HS256"
      secret: "${JWT_SECRET}"
```

#### Asymmetric Algorithms (RSA)

- **RS256**: RSASSA-PKCS1-v1_5 using SHA-256
- **RS384**: RSASSA-PKCS1-v1_5 using SHA-384
- **RS512**: RSASSA-PKCS1-v1_5 using SHA-512

```yaml
jairouter:
  security:
    jwt:
      algorithm: "RS256"
      public-key: |
        -----BEGIN PUBLIC KEY-----
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...
        -----END PUBLIC KEY-----
      private-key: |
        -----BEGIN PRIVATE KEY-----
        MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...
        -----END PRIVATE KEY-----
```

## JWT Token Format

### Standard Claims

JAiRouter supports the following standard JWT claims:

```json
{
  "iss": "jairouter",           // Issuer
  "sub": "user123",             // Subject (User ID)
  "aud": "jairouter-api",       // Audience
  "exp": 1640995200,            // Expiration Time
  "iat": 1640991600,            // Issued At
  "nbf": 1640991600,            // Not Before
  "jti": "unique-token-id"      // JWT ID
}
```

### Custom Claims

You can include custom claims in the JWT:

```json
{
  "sub": "user123",
  "permissions": ["read", "write"],
  "department": "IT",
  "role": "admin",
  "custom_data": {
    "user_level": "premium",
    "features": ["feature1", "feature2"]
  }
}
```

## Password Encryption Configuration

JAiRouter supports multiple password encryption methods to enhance security:

### BCrypt Encryption Configuration

BCrypt is a secure password hashing function recommended for production environments:

```java
// Example code: Generate BCrypt encrypted password
String rawPassword = "admin123";
org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder = 
    new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
String encodedPassword = encoder.encode(rawPassword);
System.out.println("Encoded password: " + encodedPassword);
```

Using BCrypt encrypted password in configuration file:

```yaml
jairouter:
  security:
    jwt:
      accounts:
        - username: "admin"
          password: "{bcrypt}$2a$10$xmZ5S3DY567m5z6vcPVkreKZ885VqWFb1DB5.RgCEvqHLKj0H/G7u"
          roles: [ "ADMIN", "USER" ]
          enabled: true
```

### Plain Text Password Configuration (Development Only)

For convenience in development and testing, plain text password configuration is supported:

```yaml
jairouter:
  security:
    jwt:
      accounts:
        - username: "user"
          password: "{noop}user123"
          roles: [ "USER" ]
          enabled: true
```

### Password Encoder Configuration

Configure password encoder in [SecurityConfiguration.java](file://D:\IdeaProjects\model-router\src\main\java\org\unreal\modelrouter\security\config\SecurityConfiguration.java):

```java
/**
 * Configure password encoder - BCrypt as default encoder
 */
@Bean
public PasswordEncoder passwordEncoder() {
    java.util.Map<String, org.springframework.security.crypto.password.PasswordEncoder> encoders = 
        java.util.Map.of(
            "bcrypt", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10),
            "noop", org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance()
        );
    
    org.springframework.security.crypto.password.DelegatingPasswordEncoder delegatingEncoder = 
        new org.springframework.security.crypto.password.DelegatingPasswordEncoder("bcrypt", encoders);
    
    // Set default password encoder
    delegatingEncoder.setDefaultPasswordEncoderForMatches(encoders.get("bcrypt"));
    
    return delegatingEncoder;
}
```

## Token Refresh Mechanism

### Configure Refresh Token

```yaml
jairouter:
  security:
    jwt:
      expiration-minutes: 15        # Access token expires in 15 minutes
      refresh-expiration-days: 30   # Refresh token expires in 30 days
      refresh-endpoint: "/auth/refresh"
```

### Refresh Token Flow

1. **Obtain Access Token and Refresh Token**
```bash
curl -X POST http://localhost:8080/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username": "user", "password": "pass"}'
```

Response:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in": 900,
  "token_type": "Bearer"
}
```

2. **Use Access Token to Access API**
```bash
curl -H "Authorization: Bearer access_token_here" \
     http://localhost:8080/v1/chat/completions
```

3. **Refresh Access Token**
```bash
curl -X POST http://localhost:8080/auth/refresh \
     -H "Content-Type: application/json" \
     -d '{"refresh_token": "refresh_token_here"}'
```

## JWT Token Persistence

### Enable Token Persistence

JAiRouter supports persistent storage of JWT tokens for enhanced management and monitoring:

```yaml
jairouter:
  security:
    jwt:
      # Token persistence configuration
      persistence:
        enabled: true
        primary-storage: redis    # redis, memory
        fallback-storage: memory  # memory
        
        # Cleanup configuration
        cleanup:
          enabled: true
          schedule: "0 0 2 * * ?"  # Daily at 2 AM
          retention-days: 30
          batch-size: 1000
        
        # Memory storage configuration
        memory:
          max-tokens: 50000
          cleanup-threshold: 0.8  # 80% triggers cleanup
          lru-enabled: true
          
        # Redis storage configuration
        redis:
          key-prefix: "jwt:"
          default-ttl: 3600
          connection-timeout: 5000
          retry-attempts: 3
          serialization-format: "json"
```

### Token Management Features

With persistence enabled, you can:

1. **Track Active Tokens**: Monitor all issued tokens and their status
2. **Token Lifecycle Management**: Automatic status updates and cleanup
3. **Enhanced Security**: Persistent blacklist with Redis support
4. **Audit Trail**: Complete audit logging of token operations
5. **Performance Monitoring**: Metrics and health checks for token operations

### Token Storage Structure

Tokens are stored with the following metadata:

```json
{
  "id": "token-uuid-123",
  "userId": "user123",
  "tokenHash": "sha256-hash-of-token",
  "issuedAt": "2025-01-15T10:30:00Z",
  "expiresAt": "2025-01-15T11:30:00Z",
  "status": "ACTIVE",
  "deviceInfo": "Mozilla/5.0...",
  "ipAddress": "192.168.1.100",
  "revokeReason": null,
  "revokedAt": null,
  "revokedBy": null,
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-01-15T10:30:00Z"
}
```

## Blacklist Functionality

### Enable Blacklist

```yaml
jairouter:
  security:
    jwt:
      blacklist-enabled: true
      # Enhanced blacklist persistence
      blacklist:
        persistence:
          enabled: true
          primary-storage: redis  # redis, memory
          fallback-storage: memory
          max-memory-size: 10000
          cleanup-interval: 3600  # 1 hour
```

### Token Revocation

Revoke token (logout):

```bash
curl -X POST http://localhost:8080/auth/logout \
     -H "Authorization: Bearer token_to_revoke"
```

### Enhanced Token Management

With persistence enabled, you can use the management APIs:

#### Get Token List
```bash
curl -X GET "http://localhost:8080/api/auth/jwt/tokens?page=0&size=20&status=ACTIVE" \
     -H "Authorization: Bearer admin_token"
```

#### Revoke Specific Token
```bash
curl -X POST "http://localhost:8080/api/auth/jwt/tokens/token-uuid-123/revoke" \
     -H "Authorization: Bearer admin_token" \
     -H "Content-Type: application/json" \
     -d '{"reason": "Security policy violation"}'
```

#### Bulk Token Revocation
```bash
curl -X POST "http://localhost:8080/api/auth/jwt/tokens/revoke-batch" \
     -H "Authorization: Bearer admin_token" \
     -H "Content-Type: application/json" \
     -d '{
       "tokenIds": ["token-uuid-123", "token-uuid-456"],
       "reason": "Bulk security cleanup"
     }'
```

#### Manual Cleanup
```bash
curl -X POST "http://localhost:8080/api/auth/jwt/cleanup" \
     -H "Authorization: Bearer admin_token" \
     -H "Content-Type: application/json" \
     -d '{"cleanupType": "ALL"}'
```

#### Get Blacklist Statistics
```bash
curl -X GET "http://localhost:8080/api/auth/jwt/blacklist/stats" \
     -H "Authorization: Bearer admin_token"
```

## Integration with API Key

JWT authentication can be used alongside API Key authentication:

```yaml
jairouter:
  security:
    enabled: true
    api-key:
      enabled: true
    jwt:
      enabled: true
```

Authentication Priority:
1. If the request contains a JWT token, JWT authentication is used first
2. If JWT authentication fails or no JWT token exists, API Key authentication is attempted
3. If both authentication methods fail, a 401 error is returned

## Performance Optimization

### Cache Configuration

```yaml
jairouter:
  security:
    performance:
      cache:
        redis:
          enabled: true
        local:
          jwt-blacklist:
            max-size: 10000
            expire-after-write: 86400
```

### Asynchronous Validation

```yaml
jairouter:
  security:
    performance:
      authentication:
        async-enabled: true
        thread-pool-size: 10
        timeout-ms: 5000
```

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
      
      # JWT operations auditing
      jwt-operations:
        enabled: true
        log-token-details: false  # Don't log full tokens
        log-user-agent: true
        log-ip-address: true
      
      # API Key operations auditing
      api-key-operations:
        enabled: true
        log-key-details: false   # Don't log full keys
        log-usage-patterns: true
        log-ip-address: true
      
      # Security events auditing
      security-events:
        enabled: true
        suspicious-activity-detection: true
        alert-thresholds:
          failed-auth-per-minute: 10
          token-revoke-per-minute: 5
          api-key-usage-per-minute: 100
      
      # Audit storage configuration
      storage:
        type: "file"
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

The system logs the following JWT and API Key events:

#### JWT Token Events
- **Token Issued**: When a new JWT token is created
- **Token Refreshed**: When an access token is refreshed
- **Token Revoked**: When a token is manually revoked
- **Token Validated**: When a token is validated (success/failure)
- **Token Expired**: When a token naturally expires

#### API Key Events
- **Key Created**: When a new API key is generated
- **Key Used**: When an API key is used for authentication
- **Key Revoked**: When an API key is revoked
- **Key Expired**: When an API key expires

#### Security Events
- **Suspicious Activity**: Unusual authentication patterns
- **Failed Authentication**: Failed login attempts
- **Bulk Operations**: Mass token/key operations

### Audit Event Structure

```json
{
  "id": "audit-event-uuid",
  "type": "JWT_TOKEN_ISSUED",
  "userId": "user123",
  "resourceId": "token-uuid-123",
  "action": "ISSUE_TOKEN",
  "details": "JWT token issued for user login",
  "ipAddress": "192.168.1.100",
  "userAgent": "Mozilla/5.0...",
  "success": true,
  "timestamp": "2025-01-15T10:30:00Z",
  "metadata": {
    "tokenExpiresAt": "2025-01-15T11:30:00Z",
    "deviceInfo": "Desktop Browser",
    "authMethod": "username_password"
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
- Total JWT and API Key operations
- Failed authentication statistics
- Suspicious activity alerts
- Top IP addresses and users
- Security event trends

### Monitoring Metrics

#### JWT Token Metrics
- `jairouter_security_jwt_tokens_issued_total`: Total tokens issued
- `jairouter_security_jwt_tokens_refreshed_total`: Total tokens refreshed
- `jairouter_security_jwt_tokens_revoked_total`: Total tokens revoked
- `jairouter_security_jwt_validation_duration_seconds`: Token validation duration
- `jairouter_security_jwt_blacklist_size`: Current blacklist size
- `jairouter_security_jwt_active_tokens`: Number of active tokens

#### API Key Metrics
- `jairouter_security_api_keys_created_total`: Total API keys created
- `jairouter_security_api_keys_used_total`: Total API key usage
- `jairouter_security_api_keys_revoked_total`: Total API keys revoked
- `jairouter_security_api_key_validation_duration_seconds`: API key validation duration

#### Storage Metrics
- `jairouter_security_storage_operations_total`: Total storage operations
- `jairouter_security_storage_errors_total`: Storage operation errors
- `jairouter_security_redis_connection_status`: Redis connection health
- `jairouter_security_memory_usage_bytes`: Memory usage for token storage

## Security Best Practices

### 1. Key Management

- **Use Strong Keys**: Symmetric keys should be at least 256 bits (32 bytes)
- **Regular Rotation**: Regularly rotate JWT signing keys
- **Secure Storage**: Store keys using environment variables or secret management systems
- **Key Separation**: Use different keys for different environments

### 2. Token Lifecycle

- **Short-lived Access Tokens**: Access tokens should have short expiration times (15-60 minutes)
- **Long-lived Refresh Tokens**: Refresh tokens can have longer expiration times (7-30 days)
- **Timely Revocation**: Revoke tokens promptly when users log out or anomalies are detected

### 3. Algorithm Selection

- **Production Recommendation**: Use asymmetric algorithms like RS256
- **Development**: Symmetric algorithms like HS256 can be used
- **Avoid Weak Algorithms**: Do not use the none algorithm

### 4. Claim Validation

- **Validate Standard Claims**: Always validate standard claims like exp, iat, nbf
- **Validate Custom Claims**: Validate custom claims based on business requirements
- **Principle of Least Privilege**: Only include necessary information in tokens

## Troubleshooting

### Common Issues

#### 1. Token Validation Failure

**Error Message**: `Invalid JWT token`

**Possible Causes**:
- Incorrect token format
- Signature validation failure
- Expired token
- Incorrect key configuration

**Solutions**:
1. Check if token format is correct
2. Verify signature key configuration
3. Check if token has expired
4. Review detailed error logs

#### 2. Token Expired

**Error Message**: `JWT token has expired`

**Solutions**:
1. Use refresh token to obtain new access token
2. Re-login to obtain new token
3. Adjust token expiration time configuration

#### 3. Blacklist Issues

**Error Message**: `JWT token has been revoked`

**Solutions**:
1. Check if token has been revoked
2. Re-login to obtain new token
3. Check blacklist cache configuration

### Debugging Tips

#### 1. Enable Detailed Logging

```yaml
logging:
  level:
    org.unreal.modelrouter.security.jwt: DEBUG
```

#### 2. Token Decoding Tools

Use online tools to decode JWT tokens:
- https://jwt.io/
- https://jwt-decoder.com/

#### 3. Verify Token Content

```bash
# Decode JWT token (without signature verification)
echo "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." | base64 -d
```

## Example Configurations

### Development Environment

```yaml
jairouter:
  security:
    enabled: true
    jwt:
      enabled: true
      secret: "dev-jwt-secret-key-for-development-only"
      algorithm: "HS256"
      expiration-minutes: 60
      refresh-expiration-days: 1
      issuer: "jairouter-dev"
      blacklist-enabled: true
      accounts:
        - username: "admin"
          password: "{noop}admin123"
          roles: [ "ADMIN", "USER" ]
          enabled: true
        - username: "user"
          password: "{noop}user123"
          roles: [ "USER" ]
          enabled: true
```

### Production Environment

```yaml
jairouter:
  security:
    enabled: true
    jwt:
      enabled: true
      algorithm: "RS256"
      public-key: "${JWT_PUBLIC_KEY}"
      private-key: "${JWT_PRIVATE_KEY}"
      expiration-minutes: 15
      refresh-expiration-days: 30
      issuer: "jairouter-prod"
      blacklist-enabled: true
      blacklist-cache:
        expiration-seconds: 86400
        max-size: 50000
      accounts:
        - username: "admin"
          password: "{bcrypt}$2a$10$xmZ5S3DY567m5z6vcPVkreKZ885VqWFb1DB5.RgCEvqHLKj0H/G7u"
          roles: [ "ADMIN", "USER" ]
          enabled: true
```

### High Availability Environment

```yaml
jairouter:
  security:
    jwt:
      enabled: true
      algorithm: "RS256"
      public-key: "${JWT_PUBLIC_KEY}"
      expiration-minutes: 15
    performance:
      cache:
        redis:
          enabled: true
          host: "${REDIS_HOST}"
          port: "${REDIS_PORT}"
          password: "${REDIS_PASSWORD}"
          cluster:
            enabled: true
            nodes:
              - "${REDIS_NODE1}"
              - "${REDIS_NODE2}"
              - "${REDIS_NODE3}"
```

## Related Documentation

- [API Key Management Guide](api-key-management.md)
- [Data Sanitization Rules Configuration](data-sanitization.md)
- [Security Feature Troubleshooting Guide](troubleshooting.md)
- [Security Monitoring and Alerting](../monitoring/index.md)
