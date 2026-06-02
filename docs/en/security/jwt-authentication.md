# JWT Authentication Configuration Guide

<!-- Version Information -->
> **Document Version**: 2.0.0
> **Last Updated**: 2026-05-21
> **Git Commit**: 61384b4a
> **Author**: Lincoln
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
- **Persistent Storage**: Supports storing JWT accounts and token information in H2 database
- **H2 Database Default Storage**: H2 database is the default persistent storage method for JWT data

## Quick Start

### 1. Enable JWT Authentication

Configuration file: `config/auth/jwt.yml`

```yaml
jairouter:
  security:
    jwt:
      enabled: true
      secret: "${JWT_SECRET}"
      algorithm: "HS256"
      expiration-minutes: 60
      jwt-header: "Jairouter_Token"  # Custom JWT header
```

### 2. Set JWT Key

#### Symmetric Key (HS256/HS384/HS512)

```bash
# Production Environment JWT Key Configuration
export JWT_SECRET="your-very-strong-production-jwt-secret-key-at-least-32-characters-long"

# Optional Expiration Time Configuration
export JWT_EXPIRATION_MINUTES=15
export JWT_REFRESH_EXPIRATION_DAYS=30
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
          password: "{bcrypt}$2a$10$xmZ5S3DY567m5z6vcPVkreKZ885VqWFb1DB5.RgCEvqHLKj0H/G7u"
          roles: [ "ADMIN", "USER" ]
          enabled: true
        - username: "user"
          password: "{noop}user123"
          roles: [ "USER" ]
          enabled: true
```

### 4. Client Usage

Add the JWT token in the HTTP request header:

```bash
curl -H "Jairouter_Token: your-jwt-token-here" \
     -X POST \
     -H "Content-Type: application/json" \
     -d '{"model": "gpt-3.5-turbo", "messages": [...]}' \
     http://localhost:8080/v1/chat/completions
```

---

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

---

## JWT Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `enabled` | boolean | false | Enable JWT authentication |
| `secret` | string | - | JWT signing key (symmetric algorithm) |
| `public-key` | string | - | JWT public key (asymmetric algorithm) |
| `private-key` | string | - | JWT private key (asymmetric algorithm) |
| `algorithm` | string | "HS256" | JWT signing algorithm |
| `expiration-minutes` | int | 60 | Access token expiration (minutes) |
| `refresh-expiration-days` | int | 7 | Refresh token expiration (days) |
| `issuer` | string | "jairouter" | JWT issuer identifier |
| `blacklist-enabled` | boolean | true | Enable blacklist functionality |
| `jwt-header` | string | "Jairouter_Token" | Custom JWT header name |
| `accounts` | array | [] | User account list |

---

## Supported Signing Algorithms

### Symmetric Algorithms (HMAC)

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

### Asymmetric Algorithms (RSA)

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

---

## Token Refresh Mechanism

### Configure Refresh Token

```yaml
jairouter:
  security:
    jwt:
      expiration-minutes: 15        # Access token expires in 15 minutes
      refresh-expiration-days: 30   # Refresh token expires in 30 days
```

### Refresh Token Flow

1. **Obtain Access Token and Refresh Token**
```bash
curl -X POST http://localhost:8080/api/auth/jwt/login \
     -H "Content-Type: application/json" \
     -d '{"username": "user", "password": "pass"}'
```

2. **Use Access Token to Access API**
```bash
curl -H "Jairouter_Token: access_token_here" \
     http://localhost:8080/v1/chat/completions
```

3. **Refresh Access Token**
```bash
curl -X POST http://localhost:8080/api/auth/jwt/refresh \
     -H "Content-Type: application/json" \
     -d '{"refresh_token": "refresh_token_here"}'
```

---

## JWT Token Persistence

### Enable Token Persistence

```yaml
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        primary-storage: h2    # h2, redis, memory
        fallback-storage: memory
        cleanup:
          enabled: true
          schedule: "0 0 2 * * ?"  # Daily at 2 AM
          retention-days: 30
          batch-size: 1000
```

### H2 Database Storage Advantages

1. **Default Storage Method**: H2 database is the default storage method for JWT tokens
2. **Persistence**: Data is not lost when the application restarts
3. **High Performance**: Embedded database with no network overhead
4. **Easy Maintenance**: Single database file, easy to backup
5. **Powerful Queries**: Support for complex SQL queries

---

## Blacklist Functionality

### Enable Blacklist

```yaml
jairouter:
  security:
    jwt:
      blacklist-enabled: true
      blacklist:
        persistence:
          enabled: true
          primary-storage: h2
          fallback-storage: memory
          max-memory-size: 10000
          cleanup-interval: 3600
```

### Token Revocation

```bash
curl -X POST http://localhost:8080/api/auth/jwt/logout \
     -H "Jairouter_Token: token_to_revoke"
```

---

## Token Management APIs

### Get Token List
```bash
curl -X GET "http://localhost:8080/api/auth/jwt/tokens?page=0&size=20&status=ACTIVE" \
     -H "Jairouter_Token: admin_token"
```

### Revoke Specific Token
```bash
curl -X POST "http://localhost:8080/api/auth/jwt/tokens/token-uuid-123/revoke" \
     -H "Jairouter_Token: admin_token" \
     -H "Content-Type: application/json" \
     -d '{"reason": "Security policy violation"}'
```

### Bulk Token Revocation
```bash
curl -X POST "http://localhost:8080/api/auth/jwt/tokens/revoke-batch" \
     -H "Jairouter_Token: admin_token" \
     -H "Content-Type: application/json" \
     -d '{
       "tokenIds": ["token-uuid-123", "token-uuid-456"],
       "reason": "Bulk security cleanup"
     }'
```

### Get Blacklist Statistics
```bash
curl -X GET "http://localhost:8080/api/auth/jwt/blacklist/stats" \
     -H "Jairouter_Token: admin_token"
```

---

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

---

## Security Best Practices

### 1. Key Management

- **Use Strong Keys**: Symmetric keys should be at least 256 bits (32 bytes)
- **Regular Rotation**: Regularly rotate JWT signing keys
- **Secure Storage**: Store keys using environment variables or secret management systems

### 2. Token Lifecycle

- **Short-lived Access Tokens**: Access tokens should have short expiration times (15-60 minutes)
- **Long-lived Refresh Tokens**: Refresh tokens can have longer expiration times (7-30 days)
- **Timely Revocation**: Revoke tokens promptly when users log out

### 3. Algorithm Selection

- **Production Recommendation**: Use asymmetric algorithms like RS256
- **Development**: Symmetric algorithms like HS256 can be used
- **Avoid Weak Algorithms**: Do not use the none algorithm

---

## Troubleshooting

### Common Issues

#### Token Validation Failed

**Error**: `Invalid JWT token`

**Solutions**:
1. Check token format
2. Verify signing key configuration
3. Check if token is expired
4. View detailed error logs

#### Token Expired

**Error**: `JWT token has expired`

**Solutions**:
1. Use refresh token to get new access token
2. Login again to get new token
3. Adjust token expiration configuration

### Debug Tips

#### Enable Verbose Logging

```yaml
logging:
  level:
    org.unreal.modelrouter.security.jwt: DEBUG
```

#### Token Decoding Tools

- https://jwt.io/
- https://jwt-decoder.com/

---

*Last updated: 2026-05-21*
