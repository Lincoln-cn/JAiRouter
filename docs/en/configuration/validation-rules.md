# Configuration Validation Rules

<!-- 版本信息 -->
> **Doc Version**: 1.0.0
> **Last Updated**: 2026-07-17
> **Git Commit**: ca98a2ee
> **Author**: Lincoln
<!-- /版本信息 -->

> Version: v2.8.1
> Last updated: 2026-06-23

---

## Overview

JAiRouter v1.8.0+ introduces a startup configuration validation mechanism that automatically detects the reasonableness of key configuration items, helping administrators discover configuration issues before the application starts.

---

## Validation Rule List

### 1. Rate Limit Capacity (rate-limit-capacity)

**Config item**: `RATE_LIMIT_CAPACITY` (environment variable)

**Validation rules**:
| Rule type | Min | Max | Description |
|-----------|-----|-----|-------------|
| Error | 1 | - | Rate limit capacity must be greater than 0 |
| Warning | - | 100000 | Excessive capacity may cause memory pressure |

**Recommended value**: 100 - 10000

**Example**:
```bash
# Correct configuration
export RATE_LIMIT_CAPACITY=1000

# Error configuration (will report an error)
export RATE_LIMIT_CAPACITY=0

# Warning configuration (will warn)
export RATE_LIMIT_CAPACITY=200000
```

---

### 2. Rate Limit Rate (rate-limit-rate)

**Config item**: `RATE_LIMIT_RATE` (environment variable)

**Validation rules**:
| Rule type | Min | Max | Description |
|-----------|-----|-----|-------------|
| Error | 1 | - | Rate limit rate must be greater than 0 |
| Warning | - | 10000 | Excessive rate may overload the system |

**Recommended value**: 10 - 1000 (requests/second)

**Example**:
```bash
# Correct configuration
export RATE_LIMIT_RATE=100

# Error configuration
export RATE_LIMIT_RATE=0
```

---

### 3. Circuit Breaker Failure Threshold (circuit-breaker-threshold)

**Config item**: `CIRCUIT_BREAKER_FAILURE_THRESHOLD` (environment variable)

**Validation rules**:
| Rule type | Min | Max | Description |
|-----------|-----|-----|-------------|
| Error | 1 | - | Failure threshold must be greater than 0 |
| Warning | - | 100 | Excessive threshold may delay circuit breaking |

**Recommended value**: 5 - 20

**Example**:
```bash
# Correct configuration
export CIRCUIT_BREAKER_FAILURE_THRESHOLD=5

# Warning configuration
export CIRCUIT_BREAKER_FAILURE_THRESHOLD=150
```

---

### 4. Circuit Breaker Timeout (circuit-breaker-timeout)

**Config item**: `CIRCUIT_BREAKER_TIMEOUT` (environment variable, unit: milliseconds)

**Validation rules**:
| Rule type | Min | Max | Description |
|-----------|-----|-----|-------------|
| Error | 1000 | - | Timeout cannot be less than 1 second |
| Warning | - | 300000 | Timeout too long (>5 minutes) |

**Recommended value**: 30000 - 120000 (30 seconds - 2 minutes)

**Example**:
```bash
# Correct configuration
export CIRCUIT_BREAKER_TIMEOUT=60000

# Error configuration
export CIRCUIT_BREAKER_TIMEOUT=500

# Warning configuration
export CIRCUIT_BREAKER_TIMEOUT=400000
```

---

### 5. JWT Expiration (jwt-expiration)

**Config item**: `JWT_EXPIRATION_MINUTES` (environment variable, unit: minutes)

**Validation rules**:
| Rule type | Min | Max | Description |
|-----------|-----|-----|-------------|
| Error | 1 | - | JWT expiration must be greater than 0 minutes |
| Warning | - | 1440 | Expiration too long (>24 hours) |

**Recommended value**: 60 - 480 (1 hour - 8 hours)

**Example**:
```bash
# Correct configuration
export JWT_EXPIRATION_MINUTES=120

# Warning configuration
export JWT_EXPIRATION_MINUTES=2000
```

---

### 6. Server Port (server-port)

**Config item**: `SERVER_PORT` (environment variable)

**Validation rules**:
| Rule type | Min | Max | Description |
|-----------|-----|-----|-------------|
| Error | 1 | 65535 | Port must be in the valid range |
| Warning | - | 1023 | Ports below 1024 may require root privileges |

**Recommended value**: high ports such as 8080, 8443, 3000

**Example**:
```bash
# Correct configuration
export SERVER_PORT=8080

# Error configuration
export SERVER_PORT=70000

# Warning configuration (requires root)
export SERVER_PORT=80
```

---

### 7. Thread Pool Size (thread-pool-size)

**Config item**: `SERVER_TOMCAT_THREADS_MAX` (environment variable)

**Validation rules**:
| Rule type | Min | Max | Description |
|-----------|-----|-----|-------------|
| Error | 1 | - | Thread pool size must be greater than 0 |
| Warning | - | 500 | Oversized thread pool may waste resources |

**Recommended value**: 50 - 200

**Example**:
```bash
# Correct configuration
export SERVER_TOMCAT_THREADS_MAX=100

# Warning configuration
export SERVER_TOMCAT_THREADS_MAX=600
```

---

## Validation Levels

### ✅ OK (Pass)
- Configuration is within a reasonable range
- No logs output (except DEBUG level)
- Application starts normally

### ⚠️ WARN (Warning)
- Configuration may not be optimal but is acceptable
- Warning log output
- **Does not block application startup**
- Recommended to optimize configuration per the hint

### ❌ ERROR (Error)
- Configuration has an obvious problem
- Error log output
- **Does not block application startup**, but correction is strongly recommended
- The application may have runtime issues

---

## Validation Output Examples

### All Passed
```
╔══════════════════════════════════════════════════════════════════════════════╗
║                            JAiRouter Config Validation                       ║
╚══════════════════════════════════════════════════════════════════════════════╝

✓ [rate-limit-capacity] Rate limit capacity - Passed
✓ [rate-limit-rate] Rate limit rate - Passed
✓ [circuit-breaker-threshold] Circuit breaker failure threshold - Passed
✓ [circuit-breaker-timeout] Circuit breaker timeout - Passed
✓ [jwt-expiration] JWT expiration - Passed
✓ [server-port] Server port - Passed
✓ [thread-pool-size] Thread pool size - Passed

╔══════════════════════════════════════════════════════════════════════════════╗
║  Config Validation Summary                                                   ║
║                                                                              ║
║  Total rules: 7                                                              ║
║  Passed: 7                                                                   ║
║  Warnings: 0                                                                 ║
║  Errors: 0                                                                   ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### With Warnings
```
⚠️  [rate-limit-capacity] Rate limit capacity - Capacity too large, may cause memory pressure
⚠️  [circuit-breaker-threshold] Circuit breaker failure threshold - Threshold too large, may delay circuit breaking

╔══════════════════════════════════════════════════════════════════════════════╗
║  Config Validation Summary                                                   ║
║                                                                              ║
║  Total rules: 7                                                              ║
║  Passed: 7                                                                   ║
║  Warnings: 2                                                                 ║
║  Errors: 0                                                                   ║
╚══════════════════════════════════════════════════════════════════════════════╝
```

### With Errors
```
❌ [rate-limit-rate] Rate limit rate - Rate must be greater than 0
❌ [jwt-expiration] JWT expiration - JWT expiration must be greater than 0 minutes

╔══════════════════════════════════════════════════════════════════════════════╗
║  Config Validation Summary                                                   ║
║                                                                              ║
║  Total rules: 7                                                              ║
║  Passed: 5                                                                   ║
║  Warnings: 0                                                                 ║
║  Errors: 2                                                                   ║
╚══════════════════════════════════════════════════════════════════════════════╝

⚠️  2 configuration errors detected. Please check and fix the configuration, then restart the application.
```

---

## Enabling/Disabling Validation

### Enable (default)
Validation is enabled by default; no extra configuration is needed.

### Disable
To disable validation, set the following configuration:

```yaml
# application.yml
jairouter:
  config:
    validation:
      enabled: false
```

Or use the environment variable:
```bash
export JAiROUTER_CONFIG_VALIDATION_ENABLED=false
```

---

## Custom Validation Rules

Developers can add custom validation rules by implementing the `ConfigurationValidationRule` interface.

### Example Code

```java
@Component
public class CustomConfigurationValidator {

    @EventListener(ApplicationReadyEvent.class)
    public void validateCustomConfig() {
        // Add custom validation logic
        String customConfig = System.getenv("CUSTOM_CONFIG");
        if (customConfig != null) {
            // Validate custom config
            if (!isValid(customConfig)) {
                log.error("Custom configuration is invalid");
            }
        }
    }
    
    private boolean isValid(String config) {
        // Implement validation logic
        return true;
    }
}
```

---

## Best Practices

### 1. Development Environment
```bash
export RATE_LIMIT_CAPACITY=100
export RATE_LIMIT_RATE=10
export CIRCUIT_BREAKER_FAILURE_THRESHOLD=3
export CIRCUIT_BREAKER_TIMEOUT=30000
export JWT_EXPIRATION_MINUTES=60
export SERVER_PORT=8080
export SERVER_TOMCAT_THREADS_MAX=50
```

### 2. Production Environment
```bash
export RATE_LIMIT_CAPACITY=10000
export RATE_LIMIT_RATE=500
export CIRCUIT_BREAKER_FAILURE_THRESHOLD=10
export CIRCUIT_BREAKER_TIMEOUT=60000
export JWT_EXPIRATION_MINUTES=120
export SERVER_PORT=8080
export SERVER_TOMCAT_THREADS_MAX=200
```

### 3. High-Performance Environment
```bash
export RATE_LIMIT_CAPACITY=50000
export RATE_LIMIT_RATE=2000
export CIRCUIT_BREAKER_FAILURE_THRESHOLD=15
export CIRCUIT_BREAKER_TIMEOUT=90000
export JWT_EXPIRATION_MINUTES=240
export SERVER_PORT=8080
export SERVER_TOMCAT_THREADS_MAX=400
```

---

## Troubleshooting

### Issue: Validation rules not taking effect

**Checklist**:
1. Confirm `jairouter.config.validation.enabled` is `true` (default)
2. Check logs for "JAiRouter Config Validation" output
3. Confirm environment variables are set correctly

### Issue: False warnings

**Solutions**:
1. Check whether the configuration value is actually reasonable
2. If the configuration is confirmed correct, adjust the warning thresholds
3. Or ignore the warning (it does not affect startup)

### Issue: Configuration errors but startup not blocked

**Note**:
The purpose of configuration validation is to **detect problems early**, not to block startup. Even with errors, the application will still start, but may have runtime issues.

**Recommendations**:
- Fix the configuration immediately after seeing error logs
- Add configuration checks to the CI/CD pipeline
- Use Docker environment variables to force-inject correct configuration

---

## Related Files

- [Application Config](application-config.md) - Application configuration file documentation

---
