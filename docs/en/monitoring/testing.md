# Monitoring Test Guide

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-19  
> **Git 提交**: c1aa5b0f  
> **作者**: Lincoln
<!-- /版本信息 -->



This document describes how to test the JAiRouter monitoring system functionalities, including unit testing, integration testing, performance testing, and end-to-end testing.

## Test Overview

JAiRouter monitoring system testing is divided into the following layers:

| Test Type      | Test Scope             | Test Objective     | Run Frequency     |
|----------------|------------------------|--------------------|-------------------|
| Unit Test      | Metrics collection components | Functional correctness | Every build |
| Integration Test | Prometheus endpoints   | Data integrity     | Every build |
| Performance Test | Monitoring overhead    | Performance impact | Daily/Weekly |
| End-to-End Test | Complete monitoring pipeline | System availability | Every release |

## Test Environment Setup

### Test Configuration

Create a test-specific configuration [application-test.yml](file://D:\IdeaProjects\model-router\target\classes\application-test.yml):

```yaml
# Test environment monitoring configuration
monitoring:
  metrics:
    enabled: true
    prefix: "test_jairouter"
    collection-interval: 1s  # Speed up testing
    
    enabled-categories:
      - system
      - business
      - infrastructure
    
    # Test-friendly configuration
    sampling:
      request-metrics: 1.0
      backend-metrics: 1.0
      infrastructure-metrics: 1.0
    
    performance:
      async-processing: false  # Simplify testing
      batch-size: 10
      buffer-size: 100

# Spring Actuator test configuration
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    prometheus:
      cache:
        time-to-live: 1s  # Reduce cache time for easier testing

# Test data source configuration
spring:
  profiles:
    active: test
```

### Test Infrastructure

#### Docker Compose Test Environment

Create `docker-compose-test.yml`:

```yaml
version: '3.8'

services:
  jairouter-test:
    image: jairouter/model-router:test
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=test
      - MONITORING_METRICS_ENABLED=true
    volumes:
      - ./config:/app/config
    networks:
      - test-monitoring

  prometheus-test:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/test/prometheus:/etc/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=1h'
      - '--web.enable-lifecycle'
    networks:
      - test-monitoring

networks:
  test-monitoring:
    driver: bridge
```

## Unit Testing

### Metrics Collector Testing

#### Basic Metrics Testing

```java
@ExtendWith(MockitoExtension.class)
class MetricsCollectorTest {
    
    @Mock
    private MeterRegistry meterRegistry;
    
    @Mock
    private Counter counter;
    
    @Mock
    private Timer timer;
    
    @InjectMocks
    private MetricsCollector metricsCollector;
    
    @BeforeEach
    void setUp() {
        when(meterRegistry.counter(anyString(), any(Tags.class))).thenReturn(counter);
        when(meterRegistry.timer(anyString(), any(Tags.class))).thenReturn(timer);
    }
    
    @Test
    void shouldRecordRequestMetrics() {
        // Given
        String service = "chat";
        String method = "POST";
        String status = "200";
        
        // When
        metricsCollector.recordRequest(service, method, status, 100L);
        
        // Then
        verify(counter).increment();
        verify(timer).record(100L, TimeUnit.MILLISECONDS);
    }
    
    @Test
    void shouldHandleNullValues() {
        // When & Then
        assertDoesNotThrow(() -> {
            metricsCollector.recordRequest(null, null, null, 0L);
        });
    }
    
    @Test
    void shouldApplyCorrectTags() {
        // Given
        String service = "embedding";
        String method = "POST";
        String status = "200";
        
        // When
        metricsCollector.recordRequest(service, method, status, 50L);
        
        // Then
        ArgumentCaptor<Tags> tagsCaptor = ArgumentCaptor.forClass(Tags.class);
        verify(meterRegistry).counter(eq("jairouter.requests.total"), tagsCaptor.capture());
        
        Tags capturedTags = tagsCaptor.getValue();
        assertEquals(service, capturedTags.stream()
            .filter(tag -> "service".equals(tag.getKey()))
            .findFirst()
            .map(Tag::getValue)
            .orElse(null));
    }
}
```

#### Asynchronous Processing Testing

```java
@SpringBootTest
@TestPropertySource(properties = {
    "monitoring.metrics.performance.async-processing=true",
    "monitoring.metrics.performance.batch-size=5"
})
class AsyncMetricsProcessorTest {
    
    @Autowired
    private AsyncMetricsProcessor processor;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Test
    void shouldProcessMetricsAsynchronously() throws InterruptedException {
        // Given
        int eventCount = 10;
        CountDownLatch latch = new CountDownLatch(eventCount);
        
        // When
        for (int i = 0; i < eventCount; i++) {
            processor.submitEvent(new MetricEvent("test.metric", 1.0, Tags.empty()));
        }
        
        // Wait for async processing
        Thread.sleep(2000);
        
        // Then
        Counter counter = meterRegistry.find("test.metric").counter();
        assertNotNull(counter);
        assertEquals(eventCount, counter.count(), 0.1);
    }
    
    @Test
    void shouldHandleQueueOverflow() {
        // Given
        int overflowCount = 1000;
        
        // When & Then
        assertDoesNotThrow(() -> {
            for (int i = 0; i < overflowCount; i++) {
                processor.submitEvent(new MetricEvent("overflow.test", 1.0, Tags.empty()));
            }
        });
    }
}
```

### Infrastructure Component Testing

#### Load Balancer Metrics Testing

```java
@ExtendWith(MockitoExtension.class)
class LoadBalancerMetricsTest {
    
    @Mock
    private MetricsCollector metricsCollector;
    
    @InjectMocks
    private RandomLoadBalancer loadBalancer;
    
    @Test
    void shouldRecordLoadBalancerSelection() {
        // Given
        List<ServiceInstance> instances = Arrays.asList(
            createInstance("instance1"),
            createInstance("instance2")
        );
        
        // When
        ServiceInstance selected = loadBalancer.selectInstance("chat", instances);
        
        // Then
        assertNotNull(selected);
        verify(metricsCollector).recordLoadBalancerSelection(
            eq("chat"), 
            eq("random"), 
            eq(selected.getInstanceId())
        );
    }
    
    private ServiceInstance createInstance(String instanceId) {
        ServiceInstance instance = mock(ServiceInstance.class);
        when(instance.getInstanceId()).thenReturn(instanceId);
        when(instance.getBaseUrl()).thenReturn("http://localhost:8080");
        return instance;
    }
}
```

#### Rate Limiter Metrics Testing

```java
@ExtendWith(MockitoExtension.class)
class RateLimiterMetricsTest {
    
    @Mock
    private MetricsCollector metricsCollector;
    
    @InjectMocks
    private TokenBucketRateLimiter rateLimiter;
    
    @Test
    void shouldRecordRateLimitEvents() {
        // Given
        rateLimiter.setCapacity(10);
        rateLimiter.setRefillRate(1);
        
        // When
        boolean allowed = rateLimiter.tryAcquire("test-service");
        
        // Then
        assertTrue(allowed);
        verify(metricsCollector).recordRateLimitEvent(
            eq("test-service"),
            eq("token_bucket"),
            eq(true)
        );
    }
    
    @Test
    void shouldRecordRateLimitDenial() {
        // Given
        rateLimiter.setCapacity(1);
        rateLimiter.setRefillRate(0);
        rateLimiter.tryAcquire("test-service"); // Consume the only token
        
        // When
        boolean denied = rateLimiter.tryAcquire("test-service");
        
        // Then
        assertFalse(denied);
        verify(metricsCollector).recordRateLimitEvent(
            eq("test-service"),
            eq("token_bucket"),
            eq(false)
        );
    }
}
```

## Integration Testing

### Prometheus Endpoint Testing

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "monitoring.metrics.enabled=true",
    "management.endpoints.web.exposure.include=prometheus"
})
class PrometheusEndpointIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @LocalServerPort
    private int port;
    
    @Test
    void shouldExposePrometheusMetrics() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/prometheus",
            String.class
        );
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("# HELP"));
        assertTrue(response.getBody().contains("# TYPE"));
    }
    
    @Test
    void shouldIncludeJAiRouterMetrics() {
        // Given - Trigger some requests to generate metrics
        restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);
        
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/prometheus",
            String.class
        );
        
        // Then
        String body = response.getBody();
        assertTrue(body.contains("jairouter_requests_total"));
        assertTrue(body.contains("jairouter_request_duration_seconds"));
    }
    
    @Test
    void shouldIncludeCustomTags() {
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/prometheus",
            String.class
        );
        
        // Then
        String body = response.getBody();
        assertTrue(body.contains("environment=\"test\""));
        assertTrue(body.contains("application=\"jairouter\""));
    }
    
    @Test
    void shouldRespondWithinTimeout() {
        // Given
        long startTime = System.currentTimeMillis();
        
        // When
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/prometheus",
            String.class
        );
        
        long responseTime = System.currentTimeMillis() - startTime;
        
        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(responseTime < 1000, "Response time should be less than 1 second, actual: " + responseTime + "ms");
    }
}
```

### End-to-End Data Flow Testing

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "monitoring.metrics.enabled=true",
    "monitoring.metrics.performance.async-processing=true"
})
class MonitoringDataFlowEndToEndTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @LocalServerPort
    private int port;
    
    @Test
    void shouldRecordCompleteRequestFlow() throws InterruptedException {
        // Given
        String testEndpoint = "/actuator/health";
        
        // When - Send request
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + testEndpoint,
            String.class
        );
        
        // Wait for async processing to complete
        Thread.sleep(2000);
        
        // Then - Verify response
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        // Verify metrics recording
        Counter requestCounter = meterRegistry.find("jairouter.requests.total")
            .tag("method", "GET")
            .tag("status", "200")
            .counter();
        
        assertNotNull(requestCounter, "Request counter should exist");
        assertTrue(requestCounter.count() > 0, "Request counter should have records");
        
        // Verify Prometheus endpoint includes metrics
        ResponseEntity<String> metricsResponse = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/prometheus",
            String.class
        );
        
        assertTrue(metricsResponse.getBody().contains("jairouter_requests_total"));
    }
    
    @Test
    void shouldHandleConcurrentRequests() throws InterruptedException {
        // Given
        int concurrentRequests = 50;
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        // When
        for (int i = 0; i < concurrentRequests; i++) {
            executor.submit(() -> {
                try {
                    restTemplate.getForEntity(
                        "http://localhost:" + port + "/actuator/health",
                        String.class
                    );
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        Thread.sleep(3000); // Wait for async processing
        
        // Then
        Counter requestCounter = meterRegistry.find("jairouter.requests.total").counter();
        assertNotNull(requestCounter);
        assertTrue(requestCounter.count() >= concurrentRequests);
    }
}
```

## Performance Testing

### Monitoring Overhead Testing

```java
@SpringBootTest
class MonitoringPerformanceBenchmarkTest {
    
    @Autowired
    private MetricsCollector metricsCollector;
    
    @Test
    void shouldMeasureMetricsCollectionOverhead() {
        // Given
        int iterations = 10000;
        
        // Warmup
        for (int i = 0; i < 1000; i++) {
            metricsCollector.recordRequest("test", "GET", "200", 10L);
        }
        
        // When - Measure performance with monitoring enabled
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            metricsCollector.recordRequest("test", "GET", "200", 10L);
        }
        long withMonitoringTime = System.nanoTime() - startTime;
        
        // When - Measure performance with monitoring disabled (empty operation)
        startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            // Empty operation
        }
        long withoutMonitoringTime = System.nanoTime() - startTime;
        
        // Then
        double overhead = (double) (withMonitoringTime - withoutMonitoringTime) / withoutMonitoringTime * 100;
        System.out.println("Monitoring overhead: " + overhead + "%");
        
        // Verify overhead is within acceptable range
        assertTrue(overhead < 50, "Monitoring overhead should be less than 50%, actual: " + overhead + "%");
    }
    
    @Test
    void shouldMeasureMemoryUsage() {
        // Given
        Runtime runtime = Runtime.getRuntime();
        int iterations = 100000;
        
        // Force GC and record initial memory
        System.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // When
        for (int i = 0; i < iterations; i++) {
            metricsCollector.recordRequest("test", "GET", "200", 10L);
        }
        
        // Force GC and record final memory
        System.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Then
        long memoryIncrease = finalMemory - initialMemory;
        System.out.println("Memory growth: " + memoryIncrease / 1024 / 1024 + " MB");
        
        // Verify memory growth is within acceptable range
        assertTrue(memoryIncrease < 100 * 1024 * 1024, "Memory growth should be less than 100MB");
    }
}
```

### Concurrency Performance Testing

```java
@SpringBootTest
class MonitoringConcurrencyStabilityTest {
    
    @Autowired
    private MetricsCollector metricsCollector;
    
    @Test
    void shouldHandleHighConcurrency() throws InterruptedException {
        // Given
        int threadCount = 20;
        int iterationsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // When
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        metricsCollector.recordRequest(
                            "test-" + threadId, 
                            "GET", 
                            "200", 
                            10L
                        );
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Then
        assertEquals(0, errorCount.get(), "Concurrent execution should not produce errors");
    }
    
    @Test
    void shouldNotCauseMemoryLeak() throws InterruptedException {
        // Given
        Runtime runtime = Runtime.getRuntime();
        int cycles = 10;
        int iterationsPerCycle = 10000;
        
        // When & Then
        for (int cycle = 0; cycle < cycles; cycle++) {
            long beforeMemory = runtime.totalMemory() - runtime.freeMemory();
            
            for (int i = 0; i < iterationsPerCycle; i++) {
                metricsCollector.recordRequest("test", "GET", "200", 10L);
            }
            
            System.gc();
            Thread.sleep(100);
            
            long afterMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryGrowth = afterMemory - beforeMemory;
            
            System.out.println("Cycle " + cycle + " memory growth: " + memoryGrowth / 1024 + " KB");
            
            // Memory growth per cycle should be small
            assertTrue(memoryGrowth < 10 * 1024 * 1024, 
                "Cycle " + cycle + " memory growth too large: " + memoryGrowth / 1024 / 1024 + " MB");
        }
    }
}
```

## Test Scripts

### Automated Test Scripts

#### Windows PowerShell Script

Create `scripts/run-monitoring-tests.ps1`:

```powershell
param(
    [string]$TestType = "all",
    [switch]$SkipBuild,
    [switch]$Verbose
)

$ErrorActionPreference = "Stop"

Write-Host "Starting monitoring tests..." -ForegroundColor Green

# Set environment variables
$env:SPRING_PROFILES_ACTIVE = "test"
$env:TEST_MONITORING_ENABLED = "true"

if (-not $SkipBuild) {
    Write-Host "Building project..." -ForegroundColor Yellow
    .\mvnw.cmd clean compile test-compile
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Build failed"
        exit 1
    }
}

# Run different tests based on test type
switch ($TestType.ToLower()) {
    "unit" {
        Write-Host "Running unit tests..." -ForegroundColor Yellow
        .\mvnw.cmd test -Dtest="*MetricsCollectorTest,*LoadBalancerMetricsTest,*RateLimiterMetricsTest"
    }
    "integration" {
        Write-Host "Running integration tests..." -ForegroundColor Yellow
        .\mvnw.cmd test -Dtest="*IntegrationTest"
    }
    "performance" {
        Write-Host "Running performance tests..." -ForegroundColor Yellow
        .\mvnw.cmd test -Dtest="*PerformanceBenchmarkTest,*ConcurrencyStabilityTest"
    }
    "prometheus" {
        Write-Host "Running Prometheus endpoint tests..." -ForegroundColor Yellow
        .\mvnw.cmd test -Dtest="PrometheusEndpointIntegrationTest"
    }
    "all" {
        Write-Host "Running all monitoring tests..." -ForegroundColor Yellow
        .\mvnw.cmd test -Dtest="*Metrics*Test,*Monitoring*Test,*Prometheus*Test"
    }
    default {
        Write-Error "Unknown test type: $TestType"
        exit 1
    }
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "All tests passed!" -ForegroundColor Green
} else {
    Write-Error "Tests failed"
    exit 1
}
```

#### Linux/macOS Shell Script

Create `scripts/run-monitoring-tests.sh`:

```bash
#!/bin/bash

set -e

# Parse arguments
TEST_TYPE="all"
SKIP_BUILD=false
VERBOSE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --test-type)
            TEST_TYPE="$2"
            shift 2
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

echo "Starting monitoring tests..."

# Set environment variables
export SPRING_PROFILES_ACTIVE=test
export TEST_MONITORING_ENABLED=true

if [ "$SKIP_BUILD" = false ]; then
    echo "Building project..."
    ./mvnw clean compile test-compile
fi

# Run different tests based on test type
case $TEST_TYPE in
    "unit")
        echo "Running unit tests..."
        ./mvnw test -Dtest="*MetricsCollectorTest,*LoadBalancerMetricsTest,*RateLimiterMetricsTest"
        ;;
    "integration")
        echo "Running integration tests..."
        ./mvnw test -Dtest="*IntegrationTest"
        ;;
    "performance")
        echo "Running performance tests..."
        ./mvnw test -Dtest="*PerformanceBenchmarkTest,*ConcurrencyStabilityTest"
        ;;
    "prometheus")
        echo "Running Prometheus endpoint tests..."
        ./mvnw test -Dtest="PrometheusEndpointIntegrationTest"
        ;;
    "all")
        echo "Running all monitoring tests..."
        ./mvnw test -Dtest="*Metrics*Test,*Monitoring*Test,*Prometheus*Test"
        ;;
    *)
        echo "Unknown test type: $TEST_TYPE"
        exit 1
        ;;
esac

echo "All tests passed!"
```

### Continuous Integration Configuration

#### GitHub Actions Configuration

Create `.github/workflows/monitoring-tests.yml`:

```yaml
name: Monitoring Tests

on:
  push:
    branches: [ main, develop ]
    paths:
      - 'src/**'
      - 'monitoring/**'
      - 'pom.xml'
  pull_request:
    branches: [ main ]
    paths:
      - 'src/**'
      - 'monitoring/**'
      - 'pom.xml'

jobs:
  monitoring-tests:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Setup Java
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: Cache Maven Dependencies
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
        restore-keys: ${{ runner.os }}-m2
    
    - name: Run Unit Tests
      run: ./scripts/run-monitoring-tests.sh --test-type unit
    
    - name: Run Integration Tests
      run: ./scripts/run-monitoring-tests.sh --test-type integration --skip-build
    
    - name: Run Performance Tests
      run: ./scripts/run-monitoring-tests.sh --test-type performance --skip-build
    
    - name: Upload Test Reports
      uses: actions/upload-artifact@v3
      if: always()
      with:
        name: test-reports
        path: target/surefire-reports/
    
    - name: Publish Test Results
      uses: dorny/test-reporter@v1
      if: success() || failure()
      with:
        name: Monitoring Test Results
        path: target/surefire-reports/*.xml
        reporter: java-junit
```

## Test Data Management

### Test Data Generation

```java
@Component
@Profile("test")
public class TestDataGenerator {
    
    @Autowired
    private MetricsCollector metricsCollector;
    
    public void generateTestMetrics() {
        // Generate request metrics
        generateRequestMetrics();
        
        // Generate business metrics
        generateBusinessMetrics();
        
        // Generate infrastructure metrics
        generateInfrastructureMetrics();
    }
    
    private void generateRequestMetrics() {
        String[] services = {"chat", "embedding", "rerank"};
        String[] methods = {"GET", "POST"};
        String[] statuses = {"200", "400", "500"};
        
        Random random = new Random();
        
        for (int i = 0; i < 100; i++) {
            String service = services[random.nextInt(services.length)];
            String method = methods[random.nextInt(methods.length)];
            String status = statuses[random.nextInt(statuses.length)];
            long duration = 50 + random.nextInt(200);
            
            metricsCollector.recordRequest(service, method, status, duration);
        }
    }
    
    private void generateBusinessMetrics() {
        String[] modelTypes = {"chat", "embedding"};
        String[] providers = {"openai", "ollama", "vllm"};
        
        Random random = new Random();
        
        for (int i = 0; i < 50; i++) {
            String modelType = modelTypes[random.nextInt(modelTypes.length)];
            String provider = providers[random.nextInt(providers.length)];
            boolean success = random.nextBoolean();
            
            metricsCollector.recordModelCall(modelType, provider, success);
        }
    }
    
    private void generateInfrastructureMetrics() {
        String[] strategies = {"random", "round_robin", "least_connections"};
        String[] instances = {"instance1", "instance2", "instance3"};
        
        Random random = new Random();
        
        for (int i = 0; i < 30; i++) {
            String strategy = strategies[random.nextInt(strategies.length)];
            String instance = instances[random.nextInt(instances.length)];
            
            metricsCollector.recordLoadBalancerSelection("test-service", strategy, instance);
        }
    }
}
```

### Test Assertion Utilities

```java
@Component
@Profile("test")
public class MetricsAssertions {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    public void assertCounterExists(String name, String... tags) {
        Counter counter = meterRegistry.find(name).tags(tags).counter();
        assertNotNull(counter, "Counter " + name + " should exist");
    }
    
    public void assertCounterValue(String name, double expectedValue, String... tags) {
        Counter counter = meterRegistry.find(name).tags(tags).counter();
        assertNotNull(counter, "Counter " + name + " should exist");
        assertEquals(expectedValue, counter.count(), 0.1, 
            "Counter " + name + " value mismatch");
    }
    
    public void assertTimerExists(String name, String... tags) {
        Timer timer = meterRegistry.find(name).tags(tags).timer();
        assertNotNull(timer, "Timer " + name + " should exist");
    }
    
    public void assertGaugeExists(String name, String... tags) {
        Gauge gauge = meterRegistry.find(name).tags(tags).gauge();
        assertNotNull(gauge, "Gauge " + name + " should exist");
    }
    
    public void assertMetricHasTags(String name, String... expectedTags) {
        Meter meter = meterRegistry.find(name).meter();
        assertNotNull(meter, "Metric " + name + " should exist");
        
        List<Tag> tags = meter.getId().getTags();
        for (int i = 0; i < expectedTags.length; i += 2) {
            String key = expectedTags[i];
            String value = expectedTags[i + 1];
            
            boolean tagExists = tags.stream()
                .anyMatch(tag -> key.equals(tag.getKey()) && value.equals(tag.getValue()));
            
            assertTrue(tagExists, "Metric " + name + " should contain tag " + key + "=" + value);
        }
    }
}
```

## Test Reports

### Test Coverage

Configure JaCoCo to generate test coverage reports:

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.8</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <includes>
            <include>**/monitoring/**</include>
            <include>**/metrics/**</include>
        </includes>
    </configuration>
</plugin>
```

### Performance Test Reports

```java
@Component
public class PerformanceTestReporter {
    
    public void generatePerformanceReport(Map<String, Double> results) {
        StringBuilder report = new StringBuilder();
        report.append("# Monitoring Performance Test Report\n\n");
        report.append("Test time: ").append(new Date()).append("\n\n");
        
        report.append("## Performance Metrics\n\n");
        results.forEach((metric, value) -> {
            report.append("- ").append(metric).append(": ").append(value).append("\n");
        });
        
        report.append("\n## Conclusion\n\n");
        double totalOverhead = results.getOrDefault("Total Overhead", 0.0);
        if (totalOverhead < 5) {
            report.append("✅ Performance impact is within acceptable range\n");
        } else if (totalOverhead < 10) {
            report.append("⚠️ Performance impact is high, optimization recommended\n");
        } else {
            report.append("❌ Performance impact is too high, immediate optimization needed\n");
        }
        
        // Write report to file
        try {
            Files.write(Paths.get("target/performance-report.md"), 
                       report.toString().getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Unable to write performance report", e);
        }
    }
}
```

## Best Practices

### Test Strategy

1. **Layered Testing**: Unit testing → Integration testing → End-to-end testing
2. **Fast Feedback**: Prioritize running fast unit tests first
3. **Isolated Tests**: Each test should be independent and not depend on other tests
4. **Data Cleanup**: Clean up generated metric data after testing
5. **Performance Baseline**: Establish performance baselines and continuously monitor

### Test Environment Management

1. **Configuration Isolation**: Use dedicated test configurations
2. **Data Isolation**: Use test-specific metric prefixes
3. **Resource Cleanup**: Clean up resources after testing
4. **Parallel Execution**: Support parallel test execution

### Test Maintenance

1. **Regular Updates**: Update tests synchronously with feature updates
2. **Performance Monitoring**: Monitor test execution time and resource usage
3. **Failure Analysis**: Analyze test failure causes and improve
4. **Documentation Updates**: Keep test documentation up to date

## Related Documents

- [Monitoring Setup Guide](setup.md)
- [Performance Optimization Guide](performance.md)
- [Troubleshooting Guide](troubleshooting.md)
- [Monitoring Metrics Reference](metrics.md)

---

**Tip**: It is recommended to integrate monitoring tests into the CI/CD pipeline to ensure that monitoring functionality is verified with every code change.
