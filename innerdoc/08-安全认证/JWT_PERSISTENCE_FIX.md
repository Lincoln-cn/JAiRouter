# JWT Token Persistence Fix

## Problem
The `/api/auth/jwt/tokens?page=0&size=20` endpoint was returning no data because JWT tokens were not being persisted to the H2 database.

## Root Cause
The JWT token persistence feature was disabled in the configuration. When users logged in, tokens were generated but not saved to the database.

## Changes Made

### 1. Fixed Compilation Errors in ExtendedSecurityAuditServiceImpl.java
- **Line 33**: Fixed `@Value` annotation - moved it to its own line and removed `final` modifier from the field
- **Line 51**: Fixed `@Autowired` annotation - added missing `@` symbol in constructor parameter

### 2. Enabled JWT Token Persistence in application-h2.yml
Added the following configuration to enable JWT token persistence using H2 database:
```yaml
jairouter:
  security:
    jwt:
      persistence:
        enabled: true
        primary-storage: file  # file = StoreManager (H2 database)
        fallback-storage: memory
        composite:
          enabled: false  # Not needed for H2 mode
        sync:
          enabled: false  # Not needed for H2 mode
        startup-recovery:
          enabled: true
        cleanup:
          enabled: true
          schedule: "0 0 2 * * ?"
          retention-days: 30
          batch-size: 1000
```

### 3. Enabled JWT Blacklist Persistence in application-h2.yml
Added the following configuration to enable JWT blacklist persistence using H2 database:
```yaml
jairouter:
  security:
    jwt:
      blacklist:
        persistence:
          enabled: true
          primary-storage: file  # file = StoreManager (H2 database)
          fallback-storage: memory
          max-memory-size: 10000
          cleanup-interval: 3600
        composite:
          enabled: false  # Not needed for H2 mode
        redis:
          enabled: false  # Disabled in H2 mode
```

### 4. Enabled Configuration Migration
Changed migration settings to load existing config data into H2:
```yaml
store:
  migration:
    enabled: true
  security-migration:
    enabled: true
```

## How It Works

### Token Persistence Flow
1. User logs in via `/api/auth/login`
2. `JwtTokenController.login()` generates a JWT token
3. `JwtTokenRefreshService.saveTokenMetadata()` is called
4. Token metadata is saved to H2 database via `JwtTokenPersistenceServiceImpl`
5. Token is stored in the `config` table with key prefix `jwt_token_`

### Token Retrieval Flow
1. User requests `/api/auth/jwt/tokens`
2. `JwtTokenController.getTokens()` calls `JwtPersistenceService.findAllTokens()`
3. `JwtTokenPersistenceServiceImpl` queries the H2 database via `StoreManager`
4. Tokens are retrieved from the `config` table and returned

### Blacklist Persistence Flow
1. Token is revoked via `/api/auth/jwt/tokens/{tokenId}/revoke`
2. `JwtBlacklistServiceImpl.addToBlacklist()` is called
3. Blacklist entry is saved to H2 database via `StoreManager`
4. Entry is stored in the `config` table with key prefix `jwt_blacklist_`

### Blacklist Check Flow
1. Request comes in with JWT token
2. `JwtBlacklistServiceImpl.isBlacklisted()` checks the token hash
3. Query is made to H2 database via `StoreManager`
4. Returns true if token is blacklisted, false otherwise

## Testing

### 1. Rebuild the application
```bash
mvn clean package
```

### 2. Start with H2 profile
```bash
java -jar target/model-router.jar --spring.profiles.active=h2
```

### 3. Login to generate a token
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"UqfpTm2Zw7ff2BNnZb8AQo8t"}'
```

### 4. Verify tokens are persisted
```bash
curl -X GET "http://localhost:8080/api/auth/jwt/tokens?page=0&size=20" \
  -H "Authorization: Bearer <your-token>"
```

## Storage Details

### H2 Database Tables
All JWT-related data is stored in the `config` table with different key prefixes:

| Data Type | Key Prefix | Example Key |
|-----------|-----------|-------------|
| JWT Tokens | `jwt_token_` | `jwt_token_abc123...` |
| JWT Blacklist | `jwt_blacklist_` | `jwt_blacklist_xyz789...` |
| User Index | `jwt_user_index_` | `jwt_user_index_admin` |
| Status Index | `jwt_status_index_` | `jwt_status_index_ACTIVE` |
| Blacklist Index | `jwt_blacklist_index` | `jwt_blacklist_index` |
| Token Counter | `jwt_token_counter` | `jwt_token_counter` |
| Blacklist Stats | `jwt_blacklist_stats` | `jwt_blacklist_stats` |

### Data Structure
- **JWT Tokens**: Stored as JSON with fields: id, userId, tokenHash, status, issuedAt, expiresAt, deviceInfo, ipAddress, userAgent
- **Blacklist Entries**: Stored as JSON with fields: tokenHash, expiresAt, reason, addedBy, addedAt
- **Indexes**: Stored as JSON arrays for fast lookup

## Notes
- Tokens are stored as hashed values for security
- Full tokens are never stored in the database
- Migration will automatically load existing config files into H2 on startup
- The H2 console is available at http://localhost:8080/h2-console for debugging
- Both JWT tokens and blacklist use the same H2 database via `StoreManager`
- Automatic cleanup runs daily at 2 AM to remove expired tokens and blacklist entries
