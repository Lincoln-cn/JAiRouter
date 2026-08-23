# Key Issue Fix Guide

<!-- 版本信息 -->
> **Doc Version**: 1.7.0
> **Last Updated**: 2026-05-21
> **Git Commit**: 61384b4a
> **Author**: Lincoln
<!-- /版本信息 -->

## Overview

This document records key issues encountered during JAiRouter development and operations, along with their fixes, to help you quickly locate and resolve similar problems.

---

## High Priority Issues

### 1. WebFlux Blocking Calls Causing Performance Degradation

**Problem**: Insufficient concurrency capacity, fluctuating response times

**Root cause**:
- Some code uses blocking JDBC calls
- File I/O operations not async
- HTTP calls use RestTemplate

**Fix**:
```java
// ❌ Wrong: blocking call
public Mono<Response> getData() {
    Data data = repository.findById(id); // blocking
    return Mono.just(convert(data));
}

// ✅ Correct: reactive call
public Mono<Response> getData() {
    return repository.findById(id)
        .map(this::convert);
}
```

**Verification**:
- Check thread dumps, confirm no BLOCKED state
- Load test to compare QPS improvement

**Fixed in**: V1.4.0

---

### 2. Circuit Breaker State Management Chaos

**Problem**: Incorrect circuit breaker state transitions, occasional failure to recover

**Root cause**:
- State judgment logic scattered across multiple places
- High cyclomatic complexity (15+)
- Inconsistent state under concurrency

**Fix**: Refactor using the state pattern

```java
public interface CircuitBreakerState {
    void recordSuccess(CircuitBreakerContext context);
    void recordFailure(CircuitBreakerContext context);
    boolean allowRequest();
}

public class ClosedState implements CircuitBreakerState { ... }
public class OpenState implements CircuitBreakerState { ... }
public class HalfOpenState implements CircuitBreakerState { ... }
```

**Verification**:
- Unit tests covering all state transitions
- Load test verifying failure recovery

**Fixed in**: V1.4.1

---

### 3. R2DBC Query Method Naming Not Working

**Problem**: Spring Data R2DBC method-naming queries do not take effect

**Root cause**:
- R2DBC does not support complex method-naming derived queries
- Certain keyword combinations cannot be parsed correctly

**Fix**: Use `@Query` annotation

```java
// ❌ Wrong: method naming may fail
Mono<ServiceInstance> findByServiceKeyAndStatus(String key, String status);

// ✅ Correct: use @Query
@Query("SELECT * FROM service_instance WHERE service_key = :key AND status = :status")
Mono<ServiceInstance> findByServiceKeyAndStatus(String key, String status);
```

**Verification**:
- Check whether the generated SQL is correct
- Log the actual executed SQL

---

### 4. Frontend Routing Configuration Causing 404

**Problem**: Vue frontend returns 404 on page refresh

**Root cause**:
- Frontend uses history mode
- Backend does not configure a fallback route

**Fix**:
```java
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {
    @Override
    public void configureRouting(RouteBuilder routes) {
        routes.GET("/admin/**", ctx -> {
            return ctx.render("index.html"); // fallback
        });
    }
}
```

**Verification**:
- Access frontend routes and refresh directly
- Check whether index.html is returned

---

### 5. Browser Cache Causing Changes Not to Take Effect

**Problem**: After modifying static resources, the browser still shows old content

**Root cause**:
- Static resources not versioned
- Aggressive browser caching

**Fix**:
1. Add file hash at build time
2. Add version numbers to HTML references
3. Disable caching in development

```yaml
# Development environment configuration
spring:
  web:
    resources:
      cache:
        period: 0
```

**Verification**:
- Clear browser cache
- Test in incognito mode

---

## Medium Priority Issues

### 6. Config Version Rollback Failure

**Problem**: Configuration rollback to a historical version does not take effect

**Root cause**:
- Version switch does not trigger config refresh
- Cache not cleared

**Fix**:
```java
public void rollback(String version) {
    Config config = versionRepository.findById(version);
    configRepository.save(config);
    cache.invalidate(config.getKey()); // clear cache
    eventPublisher.publish(new ConfigChangedEvent(config)); // publish event
}
```

---

### 7. Rate Limiter Concurrent Counting Inaccurate

**Problem**: Under high concurrency, the rate limiter allows more requests than configured

**Root cause**:
- Counter is not atomic
- Not synchronized in distributed environments

**Fix**:
```java
// Use atomic class
private final AtomicLong count = new AtomicLong(0);

// Or use Redis distributed lock
public Mono<Boolean> tryAcquire() {
    return redisTemplate.execute(script, keys, args);
}
```

---

### 8. JWT Token Validation Performance Bottleneck

**Problem**: JWT validation consumes significant CPU time

**Root cause**:
- Parses JWT on every request
- No caching

**Fix**:
```java
// Cache parsed Claims
private final Cache<String, Claims> cache = Caffeine.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build();

public Claims parse(String token) {
    return cache.get(token, t -> jwtParser.parse(t));
}
```

---

## Low Priority Issues

### 9. Broken Documentation Links

**Problem**: MkDocs reports broken links during build

**Root cause**:
- File moves without updating references
- Anchor changes

**Fix**:
1. Use a script to check links
2. Use relative paths
3. Add missing files

```bash
python scripts/docs/check-links.py
```

---

### 10. Test Coverage Below Target

**Problem**: JaCoCo reports coverage below 60%

**Root cause**:
- New code without tests
- Boundary conditions not covered

**Fix**:
1. Configure Maven to enforce checks
2. Use IDE test coverage tools
3. Prioritize core logic coverage

---

## Troubleshooting Process

### 1. Problem Location

```bash
# View application logs
tail -f logs/application.log

# View slow queries
curl http://localhost:8080/actuator/metrics/db.pool.wait

# View thread dumps
jstack <pid> > thread_dump.txt
```

### 2. Reproduce the Problem

- Record operation steps
- Prepare test data
- Write a reproduction script

### 3. Analyze the Cause

- Review relevant code
- Analyze log output
- Use debugging tools

### 4. Design the Solution

- Assess the impact scope
- Design the fix
- Prepare a rollback plan

### 5. Verify the Fix

- Unit test verification
- Integration test verification
- Regression test verification

---

## Preventive Measures

### 1. Code Review

- All PRs must be reviewed
- Use Checkstyle for standards
- Use SpotBugs for bug detection

### 2. Automated Testing

- Unit test coverage > 80%
- Integration tests covering core flows
- E2E tests covering key scenarios

### 3. Continuous Integration

- Commits trigger automatic builds
- Automatic test runs
- Automatic quality checks

### 4. Monitoring and Alerts

- Key metric monitoring
- Automatic anomaly alerts
- Log aggregation analysis

---

## Related Documentation

- [Troubleshooting Guide](../troubleshooting/index.md)
- [Debugging Guide](debugging.md)
- [Common Issues](common-issues.md)

---
