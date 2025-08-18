# Code Quality Standards

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: 3418d3f6  
> **作者**: Lincoln
<!-- /版本信息 -->


## Overview

The JAiRouter project adopts strict code quality standards, combining automated tools and manual reviews to ensure code readability, maintainability, and security.

## Code Quality Tools

### 1. Checkstyle

#### Configuration Files
- **Configuration File**: [checkstyle.xml](file://D:\IdeaProjects\model-router\checkstyle.xml)
- **Rule Set**: Based on Google Java Style Guide, customized for project characteristics

#### Main Rules

**Formatting Standards**
- Use 4-space indentation, disable Tab
- Line length does not exceed 120 characters
- File length does not exceed 2000 lines
- Method length does not exceed 150 lines

**Naming Conventions**
```java
// Class name: PascalCase
public class LoadBalancerFactory { }

// Method name: camelCase
public ServiceInstance selectInstance() { }

// Constants: UPPER_SNAKE_CASE
public static final int DEFAULT_TIMEOUT = 30;

// Package name: lowercase, dot-separated
package org.unreal.modelrouter.adapter;
```

**Import Standards**
```java
// Correct import order
import java.util.List;           // Java standard library
import java.util.concurrent.*;   // Java standard library

import org.springframework.*;    // Third-party libraries

import org.unreal.modelrouter.*; // Project internal packages
```

#### Running Checkstyle
```bash
# Check code style
./mvnw checkstyle:check

# Generate report
./mvnw checkstyle:checkstyle
open target/site/checkstyle.html
```

### 2. SpotBugs

#### Configuration Files
- **Include Rules**: `spotbugs-security-include.xml`
- **Exclude Rules**: `spotbugs-security-exclude.xml`

#### Check Categories

**Security Issues**
- SQL injection risk
- XSS attack risk
- Path traversal vulnerability
- Insecure random number generation

**Performance Issues**
- Inefficient string operations
- Unnecessary object creation
- Resource leak risk

**Concurrency Issues**
- Thread safety issues
- Deadlock risk
- Race conditions

#### Running SpotBugs
```bash
# Run SpotBugs check
./mvnw spotbugs:check

# Generate GUI report
./mvnw spotbugs:gui

# Generate HTML report
./mvnw spotbugs:spotbugs
open target/site/spotbugs.html
```

### 3. JaCoCo

#### Coverage Requirements
- **Minimum Coverage**: 60% (complexity coverage)
- **Recommended Coverage**: 80%
- **Core Modules**: Above 90%

#### Coverage Types
- **Line Coverage**: Execution coverage of code lines
- **Branch Coverage**: Coverage of conditional branches
- **Complexity Coverage**: Coverage of cyclomatic complexity

#### Generating Coverage Reports
```bash
# Run tests and generate coverage report
./mvnw clean test jacoco:report

# View report
open target/site/jacoco/index.html
```

## Coding Standards

### 1. Class Design Principles

#### Single Responsibility Principle
```java
// Good example: Single responsibility
public class TokenBucketRateLimiter implements RateLimiter {
    // Only responsible for token bucket rate limiting logic
}

// Bad example: Confused responsibilities
public class ServiceManager {
    // Manages services, handles rate limiting, and monitoring
}
```

#### Open/Closed Principle
```java
// Good example: Open for extension, closed for modification
public abstract class BaseAdapter {
    public final Mono<String> processRequest(String serviceType, String requestBody, ServiceInstance instance) {
        // Template method, subclasses extend specific implementation
        return doProcessRequest(serviceType, requestBody, instance);
    }
    
    protected abstract Mono<String> doProcessRequest(String serviceType, String requestBody, ServiceInstance instance);
}
```

### 2. Method Design

#### Method Length
- Single method should not exceed 50 lines
- Complex methods should be split into multiple small methods
- Use meaningful method names

```java
// Good example: Concise method with clear responsibility
public ServiceInstance selectInstance(List<ServiceInstance> instances, String clientInfo) {
    validateInstances(instances);
    return doSelect(instances, clientInfo);
}

private void validateInstances(List<ServiceInstance> instances) {
    if (instances == null || instances.isEmpty()) {
        throw new IllegalArgumentException("Instances cannot be null or empty");
    }
}

private ServiceInstance doSelect(List<ServiceInstance> instances, String clientInfo) {
    // Specific selection logic
}
```

#### Parameter Design
- Method parameters should not exceed 5
- Use objects to encapsulate multiple related parameters
- Avoid using boolean parameters

```java
// Good example: Using configuration object
public LoadBalancer createLoadBalancer(LoadBalanceConfig config) {
    // Implementation logic
}

// Bad example: Too many parameters
public LoadBalancer createLoadBalancer(String type, int weight, boolean enabled, 
                                     long timeout, String strategy, Map<String, Object> properties) {
    // Too many parameters, difficult to maintain
}
```

### 3. Exception Handling

#### Exception Classification
```java
// Business exception: Recoverable errors
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

// System exception: Irrecoverable errors
public class ConfigurationException extends RuntimeException {
    public ConfigurationException(String message) {
        super(message);
    }
}
```

#### Exception Handling Best Practices
```java
// Good example: Specific exception handling
public Mono<String> processRequest(String requestBody, ServiceInstance instance) {
    return webClient.post()
        .uri(instance.getBaseUrl() + instance.getPath())
        .bodyValue(requestBody)
        .retrieve()
        .bodyToMono(String.class)
        .onErrorMap(WebClientResponseException.class, ex -> {
            if (ex.getStatusCode().is5xxServerError()) {
                return new ServiceUnavailableException("Service temporarily unavailable", ex);
            }
            return new InvalidRequestException("Invalid request: " + ex.getMessage(), ex);
        })
        .timeout(Duration.ofSeconds(30))
        .onErrorMap(TimeoutException.class, ex -> 
            new ServiceTimeoutException("Request timeout", ex));
}
```

### 4. Logging Standards

#### Log Levels
- **ERROR**: System errors requiring immediate attention
- **WARN**: Warning information that may affect functionality
- **INFO**: Important business information
- **DEBUG**: Debugging information for development environment

#### Log Format
```java
// Good example: Structured logging
log.info("Load balancer selected instance: type={}, instance={}, clientInfo={}", 
         balancerType, instance.getName(), clientInfo);

log.error("Failed to process request: serviceType={}, error={}", 
          serviceType, ex.getMessage(), ex);

// Bad example: Insufficient information
log.info("Selected instance");
log.error("Error occurred", ex);
```

#### Sensitive Information Handling
```java
// Good example: Data masking
log.info("User request: userId={}, requestId={}", 
         maskUserId(userId), requestId);

private String maskUserId(String userId) {
    if (userId == null || userId.length() <= 4) {
        return "****";
    }
    return userId.substring(0, 2) + "****" + userId.substring(userId.length() - 2);
}
```

### 5. Comment Standards

#### Class Comments
```java
/**
 * Load balancer factory class
 * 
 * <p>Responsible for creating and managing different types of load balancer instances. Supports the following load balancing strategies:
 * <ul>
 *   <li>Random selection (random)</li>
 *   <li>Round-robin scheduling (round-robin)</li>
 *   <li>Least connections (least-connections)</li>
 *   <li>IP hash (ip-hash)</li>
 * </ul>
 * 
 * <p>This class is thread-safe and can be used in multi-threaded environments.
 * 
 * @author Developer Name
 * @since 1.0.0
 * @see LoadBalancer
 * @see LoadBalanceConfig
 */
public class LoadBalancerFactory {
    // Implementation code
}
```

#### Method Comments
```java
/**
 * Create load balancer instance based on configuration
 * 
 * <p>Creates a load balancer of the corresponding type based on the provided configuration. 
 * Throws IllegalArgumentException if the type specified in the configuration is not supported.
 * 
 * @param config Load balancing configuration, cannot be null
 * @return Load balancer instance, never returns null
 * @throws IllegalArgumentException When configuration type is not supported
 * @throws ConfigurationException When configuration parameters are invalid
 * @since 1.0.0
 */
public LoadBalancer createLoadBalancer(LoadBalanceConfig config) {
    // Implementation code
}
```

#### Complex Logic Comments
```java
public ServiceInstance selectByLeastConnections(List<ServiceInstance> instances) {
    // Select instance using least connections algorithm
    // 1. Iterate through all available instances
    // 2. Count current connections for each instance
    // 3. Select the instance with the fewest connections
    // 4. If multiple instances have the same connection count, randomly select one
    
    ServiceInstance selected = null;
    int minConnections = Integer.MAX_VALUE;
    
    for (ServiceInstance instance : instances) {
        int connections = connectionCounter.getConnections(instance);
        if (connections < minConnections) {
            minConnections = connections;
            selected = instance;
        }
    }
    
    return selected;
}
```

## Code Review

### Review Checklist

#### Functionality
- [ ] Does the code implementation meet requirements
- [ ] Are boundary conditions handled correctly
- [ ] Are exception situations properly handled
- [ ] Is business logic correct

#### Performance
- [ ] Are there performance bottlenecks
- [ ] Is resource usage reasonable
- [ ] Is there a risk of memory leaks
- [ ] Is algorithm complexity reasonable

#### Security
- [ ] Is input validation sufficient
- [ ] Are there security vulnerabilities
- [ ] Is sensitive information handled correctly
- [ ] Is access control in place

#### Maintainability
- [ ] Is code structure clear
- [ ] Are names meaningful
- [ ] Are comments sufficient
- [ ] Are design patterns followed

#### Testing
- [ ] Are unit tests sufficient
- [ ] Do test cases cover boundary conditions
- [ ] Is integration testing complete
- [ ] Is test code quality good

### Review Process

1. **Self-review**: Developer self-reviews before submission
2. **Tool check**: Automated tool checks pass
3. **Peer review**: At least one colleague reviews
4. **Technical lead review**: Important changes require technical lead review

## Continuous Improvement

### Quality Metrics Monitoring

#### Code Quality Metrics
- Code coverage trends
- Code complexity distribution
- Technical debt statistics
- Defect density analysis

#### Tool Integration
```yaml
# GitHub Actions quality check
name: Code Quality

on: [push, pull_request]

jobs:
  quality:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Run Checkstyle
      run: ./mvnw checkstyle:check
    
    - name: Run SpotBugs
      run: ./mvnw spotbugs:check
    
    - name: Run tests with coverage
      run: ./mvnw clean test jacoco:report
    
    - name: Upload coverage to SonarCloud
      uses: SonarSource/sonarcloud-github-action@master
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
```

### Quality Improvement Plan

#### Short-term Goals (1-3 months)
- Increase test coverage to 85%
- Eliminate all high-priority SpotBugs issues
- Unify code style, eliminate Checkstyle warnings

#### Medium-term Goals (3-6 months)
- Introduce SonarQube for deep code analysis
- Establish code quality gate mechanisms
- Improve code review process

#### Long-term Goals (6-12 months)
- Establish code quality culture
- Continuous refactoring and optimization
- Technical debt management

## Tool Configuration

### IDE Configuration

#### IntelliJ IDEA
```xml
<!-- .idea/codeStyles/Project.xml -->
<component name="ProjectCodeStyleConfiguration">
  <code_scheme name="Project" version="173">
    <option name="LINE_SEPARATOR" value="&#10;" />
    <option name="RIGHT_MARGIN" value="120" />
    <JavaCodeStyleSettings>
      <option name="IMPORT_LAYOUT_TABLE">
        <value>
          <package name="java" withSubpackages="true" static="false" />
          <emptyLine />
          <package name="javax" withSubpackages="true" static="false" />
          <emptyLine />
          <package name="" withSubpackages="true" static="false" />
          <emptyLine />
          <package name="org.unreal.modelrouter" withSubpackages="true" static="false" />
        </value>
      </option>
    </JavaCodeStyleSettings>
  </code_scheme>
</component>
```

#### VS Code
```json
{
  "java.format.settings.url": "checkstyle.xml",
  "java.checkstyle.configuration": "checkstyle.xml",
  "java.spotbugs.enable": true,
  "editor.rulers": [120],
  "editor.insertSpaces": true,
  "editor.tabSize": 4
}
```

### Maven Configuration Optimization

#### Fast Build Configuration
```xml
<!-- pom.xml -->
<profiles>
  <profile>
    <id>fast</id>
    <properties>
      <maven.test.skip>true</maven.test.skip>
      <checkstyle.skip>true</checkstyle.skip>
      <spotbugs.skip>true</spotbugs.skip>
      <jacoco.skip>true</jacoco.skip>
    </properties>
  </profile>
</profiles>
```

#### Quality Check Configuration
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-checkstyle-plugin</artifactId>
  <configuration>
    <configLocation>checkstyle.xml</configLocation>
    <encoding>UTF-8</encoding>
    <consoleOutput>true</consoleOutput>
    <failsOnError>true</failsOnError>
    <violationSeverity>warning</violationSeverity>
  </configuration>
</plugin>
```

By following these code quality standards, the JAiRouter project can maintain a high-quality codebase, improving development efficiency and system stability.
