# 代码质量标准

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



## 概述

JAiRouter 项目采用严格的代码质量标准，通过自动化工具和人工审查相结合的方式，确保代码的可读性、可维护性和安全性。

## 代码质量工具

### 1. Checkstyle

#### 配置文件
- **配置文件**: `checkstyle.xml`
- **规则集**: 基于 Google Java Style Guide，结合项目特点定制

#### 主要规则

**格式规范**
- 使用 4 个空格缩进，禁用 Tab
- 行长度不超过 120 字符
- 文件长度不超过 2000 行
- 方法长度不超过 150 行

**命名规范**
```java
// 类名：PascalCase
public class LoadBalancerFactory { }

// 方法名：camelCase
public ServiceInstance selectInstance() { }

// 常量：UPPER_SNAKE_CASE
public static final int DEFAULT_TIMEOUT = 30;

// 包名：小写，点分隔
package org.unreal.modelrouter.adapter;
```

**导入规范**
```java
// 正确的导入顺序
import java.util.List;           // Java 标准库
import java.util.concurrent.*;   // Java 标准库

import org.springframework.*;    // 第三方库

import org.unreal.modelrouter.*; // 项目内部包
```

#### 运行 Checkstyle
```bash
# 检查代码风格
./mvnw checkstyle:check

# 生成报告
./mvnw checkstyle:checkstyle
open target/site/checkstyle.html
```

### 2. SpotBugs

#### 配置文件
- **包含规则**: `spotbugs-security-include.xml`
- **排除规则**: `spotbugs-security-exclude.xml`

#### 检查类别

**安全问题**
- SQL 注入风险
- XSS 攻击风险
- 路径遍历漏洞
- 不安全的随机数生成

**性能问题**
- 低效的字符串操作
- 不必要的对象创建
- 资源泄漏风险

**并发问题**
- 线程安全问题
- 死锁风险
- 竞态条件

#### 运行 SpotBugs
```bash
# 运行 SpotBugs 检查
./mvnw spotbugs:check

# 生成 GUI 报告
./mvnw spotbugs:gui

# 生成 HTML 报告
./mvnw spotbugs:spotbugs
open target/site/spotbugs.html
```

### 3. JaCoCo

#### 覆盖率要求
- **最低覆盖率**: 60% (复杂度覆盖率)
- **推荐覆盖率**: 80%
- **核心模块**: 90% 以上

#### 覆盖率类型
- **行覆盖率**: 代码行的执行覆盖
- **分支覆盖率**: 条件分支的覆盖
- **复杂度覆盖率**: 圈复杂度的覆盖

#### 生成覆盖率报告
```bash
# 运行测试并生成覆盖率报告
./mvnw clean test jacoco:report

# 查看报告
open target/site/jacoco/index.html
```

## 编码规范

### 1. 类设计原则

#### 单一职责原则
```java
// 好的例子：职责单一
public class TokenBucketRateLimiter implements RateLimiter {
    // 只负责令牌桶限流逻辑
}

// 不好的例子：职责混乱
public class ServiceManager {
    // 既管理服务，又处理限流，还负责监控
}
```

#### 开闭原则
```java
// 好的例子：对扩展开放，对修改关闭
public abstract class BaseAdapter {
    public final Mono<String> processRequest(String serviceType, String requestBody, ServiceInstance instance) {
        // 模板方法，子类扩展具体实现
        return doProcessRequest(serviceType, requestBody, instance);
    }
    
    protected abstract Mono<String> doProcessRequest(String serviceType, String requestBody, ServiceInstance instance);
}
```

### 2. 方法设计

#### 方法长度
- 单个方法不超过 50 行
- 复杂方法拆分为多个小方法
- 使用有意义的方法名

```java
// 好的例子：方法简洁，职责明确
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
    // 具体选择逻辑
}
```

#### 参数设计
- 方法参数不超过 5 个
- 使用对象封装多个相关参数
- 避免使用 boolean 参数

```java
// 好的例子：使用配置对象
public LoadBalancer createLoadBalancer(LoadBalanceConfig config) {
    // 实现逻辑
}

// 不好的例子：参数过多
public LoadBalancer createLoadBalancer(String type, int weight, boolean enabled, 
                                     long timeout, String strategy, Map<String, Object> properties) {
    // 参数过多，难以维护
}
```

### 3. 异常处理

#### 异常分类
```java
// 业务异常：可恢复的错误
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

// 系统异常：不可恢复的错误
public class ConfigurationException extends RuntimeException {
    public ConfigurationException(String message) {
        super(message);
    }
}
```

#### 异常处理最佳实践
```java
// 好的例子：具体的异常处理
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

### 4. 日志规范

#### 日志级别
- **ERROR**: 系统错误，需要立即处理
- **WARN**: 警告信息，可能影响功能
- **INFO**: 重要的业务信息
- **DEBUG**: 调试信息，开发环境使用

#### 日志格式
```java
// 好的例子：结构化日志
log.info("Load balancer selected instance: type={}, instance={}, clientInfo={}", 
         balancerType, instance.getName(), clientInfo);

log.error("Failed to process request: serviceType={}, error={}", 
          serviceType, ex.getMessage(), ex);

// 不好的例子：信息不足
log.info("Selected instance");
log.error("Error occurred", ex);
```

#### 敏感信息处理
```java
// 好的例子：脱敏处理
log.info("User request: userId={}, requestId={}", 
         maskUserId(userId), requestId);

private String maskUserId(String userId) {
    if (userId == null || userId.length() <= 4) {
        return "****";
    }
    return userId.substring(0, 2) + "****" + userId.substring(userId.length() - 2);
}
```

### 5. 注释规范

#### 类注释
```java
/**
 * 负载均衡器工厂类
 * 
 * <p>负责创建和管理不同类型的负载均衡器实例。支持以下负载均衡策略：
 * <ul>
 *   <li>随机选择 (random)</li>
 *   <li>轮询调度 (round-robin)</li>
 *   <li>最少连接 (least-connections)</li>
 *   <li>IP哈希 (ip-hash)</li>
 * </ul>
 * 
 * <p>该类是线程安全的，可以在多线程环境中使用。
 * 
 * @author 开发者姓名
 * @since 1.0.0
 * @see LoadBalancer
 * @see LoadBalanceConfig
 */
public class LoadBalancerFactory {
    // 实现代码
}
```

#### 方法注释
```java
/**
 * 根据配置创建负载均衡器实例
 * 
 * <p>根据提供的配置信息创建对应类型的负载均衡器。如果配置中指定的类型
 * 不支持，将抛出 IllegalArgumentException 异常。
 * 
 * @param config 负载均衡配置，不能为 null
 * @return 负载均衡器实例，永远不会返回 null
 * @throws IllegalArgumentException 当配置类型不支持时
 * @throws ConfigurationException 当配置参数无效时
 * @since 1.0.0
 */
public LoadBalancer createLoadBalancer(LoadBalanceConfig config) {
    // 实现代码
}
```

#### 复杂逻辑注释
```java
public ServiceInstance selectByLeastConnections(List<ServiceInstance> instances) {
    // 使用最少连接算法选择实例
    // 1. 遍历所有可用实例
    // 2. 统计每个实例的当前连接数
    // 3. 选择连接数最少的实例
    // 4. 如果有多个实例连接数相同，则随机选择一个
    
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

## 代码审查

### 审查检查清单

#### 功能性
- [ ] 代码实现是否符合需求
- [ ] 边界条件是否正确处理
- [ ] 异常情况是否妥善处理
- [ ] 业务逻辑是否正确

#### 性能
- [ ] 是否存在性能瓶颈
- [ ] 资源使用是否合理
- [ ] 是否有内存泄漏风险
- [ ] 算法复杂度是否合理

#### 安全性
- [ ] 输入验证是否充分
- [ ] 是否存在安全漏洞
- [ ] 敏感信息是否正确处理
- [ ] 权限控制是否到位

#### 可维护性
- [ ] 代码结构是否清晰
- [ ] 命名是否有意义
- [ ] 注释是否充分
- [ ] 是否遵循设计模式

#### 测试
- [ ] 单元测试是否充分
- [ ] 测试用例是否覆盖边界条件
- [ ] 集成测试是否完整
- [ ] 测试代码质量是否良好

### 审查流程

1. **自审**: 开发者提交前自行审查
2. **工具检查**: 自动化工具检查通过
3. **同行审查**: 至少一名同事审查
4. **技术负责人审查**: 重要变更需要技术负责人审查

## 持续改进

### 质量指标监控

#### 代码质量指标
- 代码覆盖率趋势
- 代码复杂度分布
- 技术债务统计
- 缺陷密度分析

#### 工具集成
```yaml
# GitHub Actions 质量检查
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

### 质量改进计划

#### 短期目标 (1-3个月)
- 提高测试覆盖率到 85%
- 消除所有 SpotBugs 高优先级问题
- 统一代码风格，消除 Checkstyle 警告

#### 中期目标 (3-6个月)
- 引入 SonarQube 进行深度代码分析
- 建立代码质量门禁机制
- 完善代码审查流程

#### 长期目标 (6-12个月)
- 建立代码质量文化
- 持续重构和优化
- 技术债务管理

## 工具配置

### IDE 配置

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

### Maven 配置优化

#### 快速构建配置
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

#### 质量检查配置
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

通过遵循这些代码质量标准，JAiRouter 项目能够保持高质量的代码库，提高开发效率和系统稳定性。