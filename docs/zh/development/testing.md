# 测试指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



## 概述

JAiRouter 采用多层次的测试策略，确保代码质量和系统稳定性。本指南涵盖了单元测试、集成测试、性能测试等各个方面。

## 测试框架

### 核心测试框架
- **JUnit 5**: 主要测试框架
- **Mockito**: Mock 框架
- **Spring Boot Test**: Spring 集成测试支持
- **Reactor Test**: 响应式流测试工具
- **TestContainers**: 容器化集成测试

### 测试工具
- **AssertJ**: 流式断言库
- **WireMock**: HTTP 服务模拟
- **JaCoCo**: 代码覆盖率分析

## 测试分类

### 1. 单元测试

#### 测试范围
- 单个类或方法的功能验证
- 业务逻辑正确性
- 边界条件处理
- 异常情况处理

#### 命名规范
```
{ClassName}Test.java
```

#### 基本结构
```java
@ExtendWith(MockitoExtension.class)
@DisplayName("负载均衡器工厂测试")
class LoadBalancerFactoryTest {
    
    @Mock
    private LoadBalanceConfig config;
    
    @InjectMocks
    private LoadBalancerFactory factory;
    
    @Test
    @DisplayName("应该根据配置类型创建对应的负载均衡器")
    void shouldCreateLoadBalancerByType() {
        // Given - 准备测试数据
        when(config.getType()).thenReturn("random");
        
        // When - 执行测试操作
        LoadBalancer balancer = factory.createLoadBalancer(config);
        
        // Then - 验证结果
        assertThat(balancer).isInstanceOf(RandomLoadBalancer.class);
    }
}
```

#### 测试最佳实践

**1. 使用 AAA 模式**
```java
@Test
void shouldReturnTrueWhenTokensAvailable() {
    // Arrange - 准备
    TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(10, 1);
    
    // Act - 执行
    boolean result = rateLimiter.tryAcquire("test-key", 1);
    
    // Assert - 断言
    assertThat(result).isTrue();
}
```

**2. 测试边界条件**
```java
@Test
void shouldRejectWhenExceedingCapacity() {
    // 测试超出容量限制的情况
    TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(5, 1);
    
    // 消耗所有令牌
    for (int i = 0; i < 5; i++) {
        rateLimiter.tryAcquire("test-key", 1);
    }
    
    // 再次请求应该被拒绝
    boolean result = rateLimiter.tryAcquire("test-key", 1);
    assertThat(result).isFalse();
}
```

**3. 异常测试**
```java
@Test
void shouldThrowExceptionWhenConfigInvalid() {
    // Given
    LoadBalanceConfig invalidConfig = new LoadBalanceConfig();
    invalidConfig.setType("invalid-type");
    
    // When & Then
    assertThatThrownBy(() -> factory.createLoadBalancer(invalidConfig))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported load balancer type");
}
```

### 2. 集成测试

#### 测试范围
- 多个组件协作
- Spring 容器集成
- 数据库交互
- 外部服务调用

#### 基本结构
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "model.services.chat.instances[0].name=test-model",
    "model.services.chat.instances[0].baseUrl=http://localhost:8080"
})
class UniversalControllerIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private ModelServiceRegistry registry;
    
    @Test
    void shouldRouteChatRequest() {
        // Given
        String requestBody = """
            {
                "model": "test-model",
                "messages": [
                    {"role": "user", "content": "Hello"}
                ]
            }
            """;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        
        // When
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/v1/chat/completions", request, String.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }
}
```

#### 使用 TestContainers
```java
@SpringBootTest
@Testcontainers
class DatabaseIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Test
    void shouldPersistConfiguration() {
        // 测试数据库持久化功能
    }
}
```

### 3. 响应式测试

#### 测试 Mono
```java
@Test
void shouldProcessRequestSuccessfully() {
    // Given
    String requestBody = "{\"model\":\"test\"}";
    ServiceInstance instance = new ServiceInstance("test", "http://localhost:8080");
    
    // When
    Mono<String> result = adapter.processRequest("chat", requestBody, instance);
    
    // Then
    StepVerifier.create(result)
        .expectNextMatches(response -> response.contains("choices"))
        .verifyComplete();
}
```

#### 测试 Flux
```java
@Test
void shouldStreamResponses() {
    // Given
    Flux<String> responseStream = service.streamResponse();
    
    // When & Then
    StepVerifier.create(responseStream)
        .expectNext("chunk1")
        .expectNext("chunk2")
        .expectNext("chunk3")
        .verifyComplete();
}
```

#### 测试错误处理
```java
@Test
void shouldHandleServiceError() {
    // Given
    when(externalService.call()).thenReturn(Mono.error(new RuntimeException("Service error")));
    
    // When
    Mono<String> result = service.processWithFallback();
    
    // Then
    StepVerifier.create(result)
        .expectNext("fallback-response")
        .verifyComplete();
}
```

### 4. 性能测试

#### 并发测试
```java
@Test
void shouldHandleConcurrentRequests() throws InterruptedException {
    // Given
    int threadCount = 100;
    int requestsPerThread = 10;
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    
    // When
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                for (int j = 0; j < requestsPerThread; j++) {
                    if (rateLimiter.tryAcquire("test", 1)) {
                        successCount.incrementAndGet();
                    }
                }
            } finally {
                latch.countDown();
            }
        });
    }
    
    // Then
    latch.await(10, TimeUnit.SECONDS);
    assertThat(successCount.get()).isLessThanOrEqualTo(100); // 假设限流阈值为100
}
```

#### 压力测试
```java
@Test
@Timeout(value = 5, unit = TimeUnit.SECONDS)
void shouldCompleteWithinTimeLimit() {
    // 测试在指定时间内完成操作
    Mono<String> result = service.heavyOperation();
    
    StepVerifier.create(result)
        .expectNextCount(1)
        .verifyComplete();
}
```

## 测试数据管理

### 测试配置
```yaml
# application-test.yml
spring:
  profiles:
    active: test

model:
  services:
    chat:
      instances:
        - name: test-model
          baseUrl: http://localhost:${wiremock.server.port}
          path: /v1/chat/completions
          weight: 1

logging:
  level:
    org.unreal.modelrouter: DEBUG
```

### Mock 数据
```java
@TestConfiguration
public class TestDataConfiguration {
    
    @Bean
    @Primary
    public ModelServiceRegistry testRegistry() {
        ModelServiceRegistry registry = new ModelServiceRegistry();
        
        // 添加测试服务实例
        ServiceInstance testInstance = ServiceInstance.builder()
            .name("test-model")
            .baseUrl("http://localhost:8080")
            .path("/v1/chat/completions")
            .weight(1)
            .build();
            
        registry.addInstance("chat", testInstance);
        return registry;
    }
}
```

### WireMock 使用
```java
@ExtendWith(WireMockExtension.class)
class AdapterIntegrationTest {
    
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().port(8089))
        .build();
    
    @Test
    void shouldCallExternalService() {
        // Given
        wireMock.stubFor(post(urlEqualTo("/v1/chat/completions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                        "choices": [
                            {"message": {"content": "Hello!"}}
                        ]
                    }
                    """)));
        
        // When & Then
        // 测试逻辑
    }
}
```

## 测试执行

### 运行所有测试
```bash
# 标准测试执行
./mvnw test

# 跳过代码质量检查的测试执行
./mvnw compiler:compile compiler:testCompile surefire:test
```

### 运行特定测试
```bash
# 运行单个测试类
./mvnw test -Dtest=LoadBalancerTest

# 运行特定测试方法
./mvnw test -Dtest=LoadBalancerTest#shouldSelectInstanceRandomly

# 运行匹配模式的测试
./mvnw test -Dtest="*LoadBalancer*"
```

### 生成覆盖率报告
```bash
# 生成 JaCoCo 覆盖率报告
./mvnw clean test jacoco:report

# 查看报告
open target/site/jacoco/index.html
```

## 测试最佳实践

### 1. 测试命名
- 使用描述性的测试方法名
- 使用 `@DisplayName` 提供中文描述
- 遵循 `should_ExpectedBehavior_When_StateUnderTest` 模式

### 2. 测试组织
```java
@Nested
@DisplayName("当配置有效时")
class WhenConfigurationIsValid {
    
    @Test
    @DisplayName("应该创建对应的负载均衡器")
    void shouldCreateCorrectLoadBalancer() {
        // 测试逻辑
    }
}

@Nested
@DisplayName("当配置无效时")
class WhenConfigurationIsInvalid {
    
    @Test
    @DisplayName("应该抛出异常")
    void shouldThrowException() {
        // 测试逻辑
    }
}
```

### 3. 测试数据
- 使用 `@ParameterizedTest` 进行数据驱动测试
- 使用 Builder 模式创建测试对象
- 避免硬编码测试数据

```java
@ParameterizedTest
@ValueSource(strings = {"random", "round-robin", "least-connections", "ip-hash"})
@DisplayName("应该支持所有负载均衡类型")
void shouldSupportAllLoadBalancerTypes(String type) {
    // Given
    LoadBalanceConfig config = LoadBalanceConfig.builder()
        .type(type)
        .build();
    
    // When & Then
    assertThatCode(() -> factory.createLoadBalancer(config))
        .doesNotThrowAnyException();
}
```

### 4. 测试隔离
- 每个测试方法应该独立
- 使用 `@BeforeEach` 和 `@AfterEach` 进行清理
- 避免测试之间的依赖关系

### 5. 异步测试
```java
@Test
void shouldHandleAsyncOperation() {
    // Given
    CompletableFuture<String> future = service.asyncOperation();
    
    // When & Then
    assertThat(future)
        .succeedsWithin(Duration.ofSeconds(5))
        .isEqualTo("expected-result");
}
```

## 持续集成

### GitHub Actions 配置
```yaml
name: Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Cache Maven dependencies
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
    
    - name: Run tests
      run: ./mvnw clean test
    
    - name: Generate coverage report
      run: ./mvnw jacoco:report
    
    - name: Upload coverage to Codecov
      uses: codecov/codecov-action@v3
```

### 质量门禁
- 测试覆盖率不低于 80%
- 所有测试必须通过
- 无 Checkstyle 和 SpotBugs 警告

## 故障排查

### 常见问题

**1. 测试超时**
```java
@Test
@Timeout(value = 10, unit = TimeUnit.SECONDS)
void testWithTimeout() {
    // 测试逻辑
}
```

**2. 内存泄漏**
```java
@AfterEach
void cleanup() {
    // 清理资源
    rateLimiterCache.clear();
    connectionPool.close();
}
```

**3. 并发问题**
```java
@Test
void shouldBeConcurrentSafe() {
    // 使用 CountDownLatch 同步多线程测试
}
```

### 调试技巧
- 使用 `@EnabledIf` 条件执行测试
- 使用 `@DisabledOnOs` 跳过特定平台
- 使用日志输出调试信息

通过遵循这些测试指南，可以确保 JAiRouter 的代码质量和系统稳定性。