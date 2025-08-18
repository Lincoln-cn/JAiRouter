# Testing Guide

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: 3418d3f6  
> **作者**: Lincoln
<!-- /版本信息 -->


## Overview

JAiRouter employs a multi-layered testing strategy to ensure code quality and system stability. This guide covers various aspects including unit testing, integration testing, and performance testing.

## Testing Frameworks

### Core Testing Frameworks
- **JUnit 5**: Primary testing framework
- **Mockito**: Mocking framework
- **Spring Boot Test**: Spring integration testing support
- **Reactor Test**: Reactive streams testing tools
- **TestContainers**: Containerized integration testing

### Testing Tools
- **AssertJ**: Fluent assertion library
- **WireMock**: HTTP service mocking
- **JaCoCo**: Code coverage analysis

## Test Categories

### 1. Unit Testing

#### Test Scope
- Functionality verification of individual classes or methods
- Business logic correctness
- Boundary condition handling
- Exception handling

#### Naming Convention
```
{ClassName}Test.java
```

#### Basic Structure
```java
@ExtendWith(MockitoExtension.class)
@DisplayName("Load Balancer Factory Test")
class LoadBalancerFactoryTest {
    
    @Mock
    private LoadBalanceConfig config;
    
    @InjectMocks
    private LoadBalancerFactory factory;
    
    @Test
    @DisplayName("Should create corresponding load balancer based on configuration type")
    void shouldCreateLoadBalancerByType() {
        // Given - Prepare test data
        when(config.getType()).thenReturn("random");
        
        // When - Execute test operation
        LoadBalancer balancer = factory.createLoadBalancer(config);
        
        // Then - Verify results
        assertThat(balancer).isInstanceOf(RandomLoadBalancer.class);
    }
}
```

#### Testing Best Practices

**1. Use AAA Pattern**
```java
@Test
void shouldReturnTrueWhenTokensAvailable() {
    // Arrange - Setup
    TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(10, 1);
    
    // Act - Execution
    boolean result = rateLimiter.tryAcquire("test-key", 1);
    
    // Assert - Verification
    assertThat(result).isTrue();
}
```

**2. Test Boundary Conditions**
```java
@Test
void shouldRejectWhenExceedingCapacity() {
    // Test exceeding capacity limit scenario
    TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(5, 1);
    
    // Consume all tokens
    for (int i = 0; i < 5; i++) {
        rateLimiter.tryAcquire("test-key", 1);
    }
    
    // Next request should be rejected
    boolean result = rateLimiter.tryAcquire("test-key", 1);
    assertThat(result).isFalse();
}
```

**3. Exception Testing**
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

### 2. Integration Testing

#### Test Scope
- Multi-component collaboration
- Spring container integration
- Database interactions
- External service calls

#### Basic Structure
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

#### Using TestContainers
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
        // Test database persistence functionality
    }
}
```

### 3. Reactive Testing

#### Testing Mono
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

#### Testing Flux
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

#### Testing Error Handling
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

### 4. Performance Testing

#### Concurrency Testing
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
    assertThat(successCount.get()).isLessThanOrEqualTo(100); // Assuming rate limit threshold is 100
}
```

#### Stress Testing
```java
@Test
@Timeout(value = 5, unit = TimeUnit.SECONDS)
void shouldCompleteWithinTimeLimit() {
    // Test completing operation within specified time
    Mono<String> result = service.heavyOperation();
    
    StepVerifier.create(result)
        .expectNextCount(1)
        .verifyComplete();
}
```

## Test Data Management

### Test Configuration
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

### Mock Data
```java
@TestConfiguration
public class TestDataConfiguration {
    
    @Bean
    @Primary
    public ModelServiceRegistry testRegistry() {
        ModelServiceRegistry registry = new ModelServiceRegistry();
        
        // Add test service instance
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

### WireMock Usage
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
        // Test logic
    }
}
```

## Test Execution

### Running All Tests
```bash
# Standard test execution
./mvnw test

# Test execution skipping code quality checks
./mvnw compiler:compile compiler:testCompile surefire:test
```

### Running Specific Tests
```bash
# Run a single test class
./mvnw test -Dtest=LoadBalancerTest

# Run a specific test method
./mvnw test -Dtest=LoadBalancerTest#shouldSelectInstanceRandomly

# Run tests matching a pattern
./mvnw test -Dtest="*LoadBalancer*"
```

### Generating Coverage Reports
```bash
# Generate JaCoCo coverage report
./mvnw clean test jacoco:report

# View report
open target/site/jacoco/index.html
```

## Testing Best Practices

### 1. Test Naming
- Use descriptive test method names
- Use `@DisplayName` to provide Chinese descriptions
- Follow `should_ExpectedBehavior_When_StateUnderTest` pattern

### 2. Test Organization
```java
@Nested
@DisplayName("When configuration is valid")
class WhenConfigurationIsValid {
    
    @Test
    @DisplayName("Should create corresponding load balancer")
    void shouldCreateCorrectLoadBalancer() {
        // Test logic
    }
}

@Nested
@DisplayName("When configuration is invalid")
class WhenConfigurationIsInvalid {
    
    @Test
    @DisplayName("Should throw exception")
    void shouldThrowException() {
        // Test logic
    }
}
```

### 3. Test Data
- Use `@ParameterizedTest` for data-driven testing
- Use Builder pattern to create test objects
- Avoid hard-coded test data

```java
@ParameterizedTest
@ValueSource(strings = {"random", "round-robin", "least-connections", "ip-hash"})
@DisplayName("Should support all load balancer types")
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

### 4. Test Isolation
- Each test method should be independent
- Use `@BeforeEach` and `@AfterEach` for cleanup
- Avoid dependencies between tests

### 5. Asynchronous Testing
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

## Continuous Integration

### GitHub Actions Configuration
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

### Quality Gates
- Test coverage no less than 80%
- All tests must pass
- No Checkstyle and SpotBugs warnings

## Troubleshooting

### Common Issues

**1. Test Timeout**
```java
@Test
@Timeout(value = 10, unit = TimeUnit.SECONDS)
void testWithTimeout() {
    // Test logic
}
```

**2. Memory Leaks**
```java
@AfterEach
void cleanup() {
    // Clean up resources
    rateLimiterCache.clear();
    connectionPool.close();
}
```

**3. Concurrency Issues**
```java
@Test
void shouldBeConcurrentSafe() {
    // Use CountDownLatch to synchronize multi-threaded tests
}
```

### Debugging Tips
- Use `@EnabledIf` for conditional test execution
- Use `@DisabledOnOs` to skip specific platforms
- Use logging for debugging information

By following these testing guidelines, the code quality and system stability of JAiRouter can be ensured.
