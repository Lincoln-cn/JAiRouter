# 监控测试指南

<!-- 版本信息 -->
> **文档版本**: 1.0.0  
> **最后更新**: 2025-08-18  
> **Git 提交**: 3418d3f6  
> **作者**: Lincoln
<!-- /版本信息 -->


本文档介绍如何测试 JAiRouter 监控系统的功能，包括单元测试、集成测试、性能测试和端到端测试。

## 测试概述

JAiRouter 监控系统测试分为以下几个层次：

| 测试类型 | 测试范围 | 测试目标 | 运行频率 |
|----------|----------|----------|----------|
| 单元测试 | 指标收集组件 | 功能正确性 | 每次构建 |
| 集成测试 | Prometheus 端点 | 数据完整性 | 每次构建 |
| 性能测试 | 监控开销 | 性能影响 | 每日/每周 |
| 端到端测试 | 完整监控链路 | 系统可用性 | 每次发布 |

## 测试环境准备

### 测试配置

创建测试专用配置 `application-test.yml`：

```yaml
# 测试环境监控配置
monitoring:
  metrics:
    enabled: true
    prefix: "test_jairouter"
    collection-interval: 1s  # 加快测试速度
    
    enabled-categories:
      - system
      - business
      - infrastructure
    
    # 测试友好的配置
    sampling:
      request-metrics: 1.0
      backend-metrics: 1.0
      infrastructure-metrics: 1.0
    
    performance:
      async-processing: false  # 简化测试
      batch-size: 10
      buffer-size: 100

# Spring Actuator 测试配置
management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    prometheus:
      cache:
        time-to-live: 1s  # 减少缓存时间便于测试

# 测试数据源配置
spring:
  profiles:
    active: test
```

### 测试基础设施

#### Docker Compose 测试环境

创建 `docker-compose-test.yml`：

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

## 单元测试

### 指标收集器测试

#### 基础指标测试

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

#### 异步处理测试

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

### 基础设施组件测试

#### 负载均衡器指标测试

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

#### 限流器指标测试

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
        rateLimiter.tryAcquire("test-service"); // 消耗唯一令牌
        
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

## 集成测试

### Prometheus 端点测试

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
        // Given - 触发一些请求以生成指标
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
        assertTrue(responseTime < 1000, "响应时间应小于 1 秒，实际: " + responseTime + "ms");
    }
}
```

### 端到端数据流测试

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
        
        // When - 发送请求
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + testEndpoint,
            String.class
        );
        
        // 等待异步处理完成
        Thread.sleep(2000);
        
        // Then - 验证响应
        assertEquals(HttpStatus.OK, response.getStatusCode());
        
        // 验证指标记录
        Counter requestCounter = meterRegistry.find("jairouter.requests.total")
            .tag("method", "GET")
            .tag("status", "200")
            .counter();
        
        assertNotNull(requestCounter, "请求计数器应该存在");
        assertTrue(requestCounter.count() > 0, "请求计数器应该有记录");
        
        // 验证 Prometheus 端点包含指标
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
        Thread.sleep(3000); // 等待异步处理
        
        // Then
        Counter requestCounter = meterRegistry.find("jairouter.requests.total").counter();
        assertNotNull(requestCounter);
        assertTrue(requestCounter.count() >= concurrentRequests);
    }
}
```

## 性能测试

### 监控开销测试

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
        
        // When - 测量启用监控的性能
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            metricsCollector.recordRequest("test", "GET", "200", 10L);
        }
        long withMonitoringTime = System.nanoTime() - startTime;
        
        // When - 测量禁用监控的性能（空操作）
        startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            // 空操作
        }
        long withoutMonitoringTime = System.nanoTime() - startTime;
        
        // Then
        double overhead = (double) (withMonitoringTime - withoutMonitoringTime) / withoutMonitoringTime * 100;
        System.out.println("监控开销: " + overhead + "%");
        
        // 验证开销在可接受范围内
        assertTrue(overhead < 50, "监控开销应小于 50%，实际: " + overhead + "%");
    }
    
    @Test
    void shouldMeasureMemoryUsage() {
        // Given
        Runtime runtime = Runtime.getRuntime();
        int iterations = 100000;
        
        // 强制 GC 并记录初始内存
        System.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // When
        for (int i = 0; i < iterations; i++) {
            metricsCollector.recordRequest("test", "GET", "200", 10L);
        }
        
        // 强制 GC 并记录最终内存
        System.gc();
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Then
        long memoryIncrease = finalMemory - initialMemory;
        System.out.println("内存增长: " + memoryIncrease / 1024 / 1024 + " MB");
        
        // 验证内存增长在可接受范围内
        assertTrue(memoryIncrease < 100 * 1024 * 1024, "内存增长应小于 100MB");
    }
}
```

### 并发性能测试

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
        assertEquals(0, errorCount.get(), "并发执行不应产生错误");
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
            
            System.out.println("Cycle " + cycle + " 内存增长: " + memoryGrowth / 1024 + " KB");
            
            // 每个周期的内存增长应该很小
            assertTrue(memoryGrowth < 10 * 1024 * 1024, 
                "Cycle " + cycle + " 内存增长过大: " + memoryGrowth / 1024 / 1024 + " MB");
        }
    }
}
```

## 测试脚本

### 自动化测试脚本

#### Windows PowerShell 脚本

创建 `scripts/run-monitoring-tests.ps1`：

```powershell
param(
    [string]$TestType = "all",
    [switch]$SkipBuild,
    [switch]$Verbose
)

$ErrorActionPreference = "Stop"

Write-Host "开始运行监控测试..." -ForegroundColor Green

# 设置环境变量
$env:SPRING_PROFILES_ACTIVE = "test"
$env:TEST_MONITORING_ENABLED = "true"

if (-not $SkipBuild) {
    Write-Host "构建项目..." -ForegroundColor Yellow
    .\mvnw.cmd clean compile test-compile
    if ($LASTEXITCODE -ne 0) {
        Write-Error "构建失败"
        exit 1
    }
}

# 根据测试类型运行不同的测试
switch ($TestType.ToLower()) {
    "unit" {
        Write-Host "运行单元测试..." -ForegroundColor Yellow
        .\mvnw.cmd test -Dtest="*MetricsCollectorTest,*LoadBalancerMetricsTest,*RateLimiterMetricsTest"
    }
    "integration" {
        Write-Host "运行集成测试..." -ForegroundColor Yellow
        .\mvnw.cmd test -Dtest="*IntegrationTest"
    }
    "performance" {
        Write-Host "运行性能测试..." -ForegroundColor Yellow
        .\mvnw.cmd test -Dtest="*PerformanceBenchmarkTest,*ConcurrencyStabilityTest"
    }
    "prometheus" {
        Write-Host "运行 Prometheus 端点测试..." -ForegroundColor Yellow
        .\mvnw.cmd test -Dtest="PrometheusEndpointIntegrationTest"
    }
    "all" {
        Write-Host "运行所有监控测试..." -ForegroundColor Yellow
        .\mvnw.cmd test -Dtest="*Metrics*Test,*Monitoring*Test,*Prometheus*Test"
    }
    default {
        Write-Error "未知的测试类型: $TestType"
        exit 1
    }
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "所有测试通过!" -ForegroundColor Green
} else {
    Write-Error "测试失败"
    exit 1
}
```

#### Linux/macOS Shell 脚本

创建 `scripts/run-monitoring-tests.sh`：

```bash
#!/bin/bash

set -e

# 参数解析
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
            echo "未知参数: $1"
            exit 1
            ;;
    esac
done

echo "开始运行监控测试..."

# 设置环境变量
export SPRING_PROFILES_ACTIVE=test
export TEST_MONITORING_ENABLED=true

if [ "$SKIP_BUILD" = false ]; then
    echo "构建项目..."
    ./mvnw clean compile test-compile
fi

# 根据测试类型运行不同的测试
case $TEST_TYPE in
    "unit")
        echo "运行单元测试..."
        ./mvnw test -Dtest="*MetricsCollectorTest,*LoadBalancerMetricsTest,*RateLimiterMetricsTest"
        ;;
    "integration")
        echo "运行集成测试..."
        ./mvnw test -Dtest="*IntegrationTest"
        ;;
    "performance")
        echo "运行性能测试..."
        ./mvnw test -Dtest="*PerformanceBenchmarkTest,*ConcurrencyStabilityTest"
        ;;
    "prometheus")
        echo "运行 Prometheus 端点测试..."
        ./mvnw test -Dtest="PrometheusEndpointIntegrationTest"
        ;;
    "all")
        echo "运行所有监控测试..."
        ./mvnw test -Dtest="*Metrics*Test,*Monitoring*Test,*Prometheus*Test"
        ;;
    *)
        echo "未知的测试类型: $TEST_TYPE"
        exit 1
        ;;
esac

echo "所有测试通过!"
```

### 持续集成配置

#### GitHub Actions 配置

创建 `.github/workflows/monitoring-tests.yml`：

```yaml
name: 监控测试

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
    
    - name: 设置 Java
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    
    - name: 缓存 Maven 依赖
      uses: actions/cache@v3
      with:
        path: ~/.m2
        key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
        restore-keys: ${{ runner.os }}-m2
    
    - name: 运行单元测试
      run: ./scripts/run-monitoring-tests.sh --test-type unit
    
    - name: 运行集成测试
      run: ./scripts/run-monitoring-tests.sh --test-type integration --skip-build
    
    - name: 运行性能测试
      run: ./scripts/run-monitoring-tests.sh --test-type performance --skip-build
    
    - name: 上传测试报告
      uses: actions/upload-artifact@v3
      if: always()
      with:
        name: test-reports
        path: target/surefire-reports/
    
    - name: 发布测试结果
      uses: dorny/test-reporter@v1
      if: success() || failure()
      with:
        name: 监控测试结果
        path: target/surefire-reports/*.xml
        reporter: java-junit
```

## 测试数据管理

### 测试数据生成

```java
@Component
@Profile("test")
public class TestDataGenerator {
    
    @Autowired
    private MetricsCollector metricsCollector;
    
    public void generateTestMetrics() {
        // 生成请求指标
        generateRequestMetrics();
        
        // 生成业务指标
        generateBusinessMetrics();
        
        // 生成基础设施指标
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

### 测试断言工具

```java
@Component
@Profile("test")
public class MetricsAssertions {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    public void assertCounterExists(String name, String... tags) {
        Counter counter = meterRegistry.find(name).tags(tags).counter();
        assertNotNull(counter, "计数器 " + name + " 应该存在");
    }
    
    public void assertCounterValue(String name, double expectedValue, String... tags) {
        Counter counter = meterRegistry.find(name).tags(tags).counter();
        assertNotNull(counter, "计数器 " + name + " 应该存在");
        assertEquals(expectedValue, counter.count(), 0.1, 
            "计数器 " + name + " 的值不匹配");
    }
    
    public void assertTimerExists(String name, String... tags) {
        Timer timer = meterRegistry.find(name).tags(tags).timer();
        assertNotNull(timer, "计时器 " + name + " 应该存在");
    }
    
    public void assertGaugeExists(String name, String... tags) {
        Gauge gauge = meterRegistry.find(name).tags(tags).gauge();
        assertNotNull(gauge, "仪表 " + name + " 应该存在");
    }
    
    public void assertMetricHasTags(String name, String... expectedTags) {
        Meter meter = meterRegistry.find(name).meter();
        assertNotNull(meter, "指标 " + name + " 应该存在");
        
        List<Tag> tags = meter.getId().getTags();
        for (int i = 0; i < expectedTags.length; i += 2) {
            String key = expectedTags[i];
            String value = expectedTags[i + 1];
            
            boolean tagExists = tags.stream()
                .anyMatch(tag -> key.equals(tag.getKey()) && value.equals(tag.getValue()));
            
            assertTrue(tagExists, "指标 " + name + " 应该包含标签 " + key + "=" + value);
        }
    }
}
```

## 测试报告

### 测试覆盖率

配置 JaCoCo 生成测试覆盖率报告：

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

### 性能测试报告

```java
@Component
public class PerformanceTestReporter {
    
    public void generatePerformanceReport(Map<String, Double> results) {
        StringBuilder report = new StringBuilder();
        report.append("# 监控性能测试报告\n\n");
        report.append("测试时间: ").append(new Date()).append("\n\n");
        
        report.append("## 性能指标\n\n");
        results.forEach((metric, value) -> {
            report.append("- ").append(metric).append(": ").append(value).append("\n");
        });
        
        report.append("\n## 结论\n\n");
        double totalOverhead = results.getOrDefault("总开销", 0.0);
        if (totalOverhead < 5) {
            report.append("✅ 性能影响在可接受范围内\n");
        } else if (totalOverhead < 10) {
            report.append("⚠️ 性能影响较高，建议优化\n");
        } else {
            report.append("❌ 性能影响过高，需要立即优化\n");
        }
        
        // 写入报告文件
        try {
            Files.write(Paths.get("target/performance-report.md"), 
                       report.toString().getBytes());
        } catch (IOException e) {
            throw new RuntimeException("无法写入性能报告", e);
        }
    }
}
```

## 最佳实践

### 测试策略

1. **分层测试**: 单元测试 → 集成测试 → 端到端测试
2. **快速反馈**: 优先运行快速的单元测试
3. **隔离测试**: 每个测试独立，不依赖其他测试
4. **数据清理**: 测试后清理生成的指标数据
5. **性能基准**: 建立性能基准并持续监控

### 测试环境管理

1. **配置隔离**: 使用专门的测试配置
2. **数据隔离**: 使用测试专用的指标前缀
3. **资源清理**: 测试完成后清理资源
4. **并行执行**: 支持并行运行测试

### 测试维护

1. **定期更新**: 随着功能更新同步更新测试
2. **性能监控**: 监控测试执行时间和资源使用
3. **失败分析**: 分析测试失败原因并改进
4. **文档更新**: 保持测试文档的更新

## 相关文档

- [监控设置指南](setup.md)
- [性能优化指南](performance.md)
- [故障排查指南](troubleshooting.md)
- [监控指标参考](metrics.md)

---

**提示**: 建议将监控测试集成到 CI/CD 流水线中，确保每次代码变更都能验证监控功能的正确性。