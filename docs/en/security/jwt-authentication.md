# JWT Authentication Configuration Guide

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**:   
> **作者**: 
<!-- /版本信息 -->

## Overview

JAiRouter supports JWT (JSON Web Token) authentication, which can be integrated with existing identity authentication systems. JWT authentication provides a stateless authentication mechanism and supports token refresh and blacklist features.

## Features

- **Standard JWT Support**: Fully compatible with RFC 7519 standard
- **Multiple Signing Algorithms**: Supports HS256, HS384, HS512, RS256, RS384, RS512
- **Token Refresh**: Supports access token and refresh token mechanism
- **Blacklist Functionality**: Supports token revocation and logout
- **Coexistence with API Key**: Can be used simultaneously with API Key authentication
- **Username/Password Login**: Supports obtaining JWT tokens through username and password

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

### 2. Set JWT Key

#### Symmetric Key (HS256/HS384/HS512)

```bash
# Generate a strong key
export JWT_SECRET="your-very-strong-jwt-secret-key-at-least-32-characters-long"
```

#### Asymmetric Key (RS256/RS384/RS512)

```yaml
jairouter:
  security:
    jwt:
      algorithm: "RS256"
      public-key: "${JWT_PUBLIC_KEY}"
      private-key: "${JWT_PRIVATE_KEY}"
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
      # User account configuration
      accounts:
        - username: "admin"
          password: "{bcrypt}$2a$10$xmZ5S3DY567m5z6vcPVkreKZ885VqWFb1DB5.RgCEvqHLKj0H/G7u"  # BCrypt encrypted password
          roles: [ "ADMIN", "USER" ]
          enabled: true
        - username: "user"
          password: "{noop}user123"  # Plain text password for development, should use encryption in production
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
| `enabled` | boolean | false | Whether to enable JWT authentication |
| `secret` | string | - | JWT signing key (symmetric algorithm) |
| `public-key` | string | - | JWT public key (asymmetric algorithm) |
| `private-key` | string | - | JWT private key (asymmetric algorithm) |
| `algorithm` | string | "HS256" | JWT signing algorithm |
| `expiration-minutes` | int | 60 | Access token expiration time (minutes) |
| `refresh-expiration-days` | int | 7 | Refresh token expiration time (days) |
| `issuer` | string | "jairouter" | JWT issuer identifier |
| `blacklist-enabled` | boolean | true | Whether to enable blacklist functionality |
| `accounts` | array | [] | User account list |

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
  "exp": 1640995200,            // Expiration time
  "iat": 1640991600,            // Issued at
  "nbf": 1640991600,            // Not before
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

JAiRouter supports multiple password encryption methods to improve security:

### BCrypt Encryption Configuration

BCrypt is a secure password hashing function, recommended for use in production environments:

```java
// Example code: Generate BCrypt encrypted password
String rawPassword = "admin123";
org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder = 
    new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
String encodedPassword = encoder.encode(rawPassword);
System.out.println("Encoded password: " + encodedPassword);
```

Using BCrypt encrypted passwords in configuration files:

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

For development and testing convenience, plain text password configuration is supported:

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

Configure the password encoder in `SecurityConfiguration.java`:

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
    
    // Set the default password encoder
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
      expiration-minutes: 15        // Access token expires in 15 minutes
      refresh-expiration-days: 30   // Refresh token expires in 30 days
      refresh-endpoint: "/auth/refresh"
```

### Token Refresh Process

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

## Blacklist Functionality

### Enable Blacklist

```yaml
jairouter:
  security:
    jwt:
      blacklist-enabled: true
      blacklist-cache:
        expiration-seconds: 86400   // 24 hours
        max-size: 10000            // Maximum cache entries
```

### Token Revocation

Revoke token (logout):

```bash
curl -X POST http://localhost:8080/auth/logout \
     -H "Authorization: Bearer token_to_revoke"
```

### Bulk Revocation

Revoke all tokens for a user:

```bash
curl -X POST http://localhost:8080/auth/revoke-all \
     -H "Authorization: Bearer admin_token" \
     -H "Content-Type: application/json" \
     -d '{"user_id": "user123"}'
```

## Integration with API Key

JWT authentication can be used simultaneously with API Key authentication:

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
2. If JWT authentication fails or no JWT token exists, try API Key authentication
3. If both authentication methods fail, return a 401 error

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

## Monitoring and Auditing

### Audit Events

```yaml
jairouter:
  security:
    audit:
      enabled: true
      event-types:
        jwt-token-issued: true
        jwt-token-refreshed: true
        jwt-token-revoked: true
        jwt-token-expired: true
        jwt-validation-failed: true
```

### Monitoring Metrics

- `jairouter_security_jwt_tokens_issued_total`: Total number of issued tokens
- `jairouter_security_jwt_tokens_refreshed_total`: Total number of refreshed tokens
- `jairouter_security_jwt_tokens_revoked_total`: Total number of revoked tokens
- `jairouter_security_jwt_validation_duration_seconds`: Token validation duration

## Security Best Practices

### 1. Key Management

- **Use Strong Keys**: Symmetric keys should be at least 256 bits (32 bytes)
- **Regular Rotation**: Regularly rotate JWT signing keys
- **Secure Storage**: Store keys using environment variables or key management systems
- **Key Separation**: Use different keys for different environments

### 2. Token Lifecycle

- **Short-lived Access Tokens**: Access tokens should have short expiration times (15-60 minutes)
- **Long-lived Refresh Tokens**: Refresh tokens can have longer expiration times (7-30 days)
- **Timely Revocation**: Revoke tokens promptly when users log out or anomalies are detected

### 3. Algorithm Selection

- **Production Environment Recommendation**: Use asymmetric algorithms like RS256
- **Development Environment**: Symmetric algorithms like HS256 can be used
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
- Signature verification failure
- Token has expired
- Key configuration error

**Solutions**:
1. Check if the token format is correct
2. Verify signature key configuration
3. Check if the token has expired
4. Check detailed error logs

#### 2. Token Expired

**Error Message**: `JWT token has expired`

**Solutions**:
1. Use refresh token to obtain a new access token
2. Re-login to obtain a new token
3. Adjust token expiration time configuration

#### 3. Blacklist Issues

**Error Message**: `JWT token has been revoked`

**Solutions**:
1. Check if the token has been revoked
2. Re-login to obtain a new token
3. Check blacklist cache configuration

### Debugging Tips

#### 1. Enable Detailed Logging

```yaml
logging:
  level:
    org.unreal.modelrouter.security.jwt: DEBUG
```

#### 2. Token Parsing Tools

Use online tools to parse JWT tokens:
- https://jwt.io/
- https://jwt-decoder.com/

#### 3. Validate Token Content

```bash
// Decode JWT token (without signature verification)
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
- [Security Monitoring and Alerting](../monitoring/alerts.md)