package org.unreal.modelrouter.tracing.encryption;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.DefaultTracingContext;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TracingEncryptionService 测试类
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TracingEncryptionServiceTest {

    @Mock
    private StructuredLogger structuredLogger;

    @Mock
    private TracingConfiguration tracingConfiguration;

    @Mock
    private TracingConfiguration.SecurityConfig securityConfig;

    @Mock
    private TracingConfiguration.SecurityConfig.EncryptionConfig encryptionConfig;

    @Mock
    private TracingConfiguration.SecurityConfig.EncryptionConfig.DataRetention dataRetention;

    private TracingEncryptionService tracingEncryptionService;
    private Tracer tracer;

    @BeforeEach
    void setUp() {
        // 设置配置mock
        when(tracingConfiguration.getSecurity()).thenReturn(securityConfig);
        when(securityConfig.getEncryption()).thenReturn(encryptionConfig);
        when(encryptionConfig.isEnabled()).thenReturn(true);
        when(encryptionConfig.getAlgorithm()).thenReturn("AES");
        when(encryptionConfig.getKeySize()).thenReturn(256);
        when(encryptionConfig.getDataRetention()).thenReturn(dataRetention);
        when(dataRetention.getDefaultRetention()).thenReturn(Duration.ofDays(30));

        // 创建测试用的Tracer
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
        tracer = tracerProvider.get("test");

        // 创建服务实例
        tracingEncryptionService = new TracingEncryptionService(tracingConfiguration, structuredLogger);
    }

    @Test
    void testEncryptTraceData() {
        // 准备测试数据
        String testData = "sensitive trace data";
        String traceId = "test-trace-id";
        String dataType = "span-data";

        // 执行测试
        Mono<String> result = tracingEncryptionService.encryptTraceData(testData, traceId, dataType);

        // 验证结果
        StepVerifier.create(result)
                .assertNext(encryptedData -> {
                    assertNotNull(encryptedData);
                    assertFalse(encryptedData.isEmpty());
                    // 加密后的数据应该与原始数据不同
                    assertNotEquals(testData, encryptedData);
                })
                .verifyComplete();
    }

    @Test
    void testDecryptTraceData() {
        // 准备测试数据
        String testData = "sensitive trace data";
        String traceId = "test-trace-id";
        String dataType = "span-data";

        // 先加密数据
        String encryptedData = tracingEncryptionService.encryptTraceData(testData, traceId, dataType).block();
        assertNotNull(encryptedData);

        // 执行解密测试
        Mono<String> result = tracingEncryptionService.decryptTraceData(encryptedData, traceId, dataType);

        // 验证结果
        StepVerifier.create(result)
                .assertNext(decryptedData -> {
                    assertNotNull(decryptedData);
                    assertEquals(testData, decryptedData);
                })
                .verifyComplete();
    }

    @Test
    void testRotateEncryptionKey() {
        // 执行测试
        Mono<Boolean> result = tracingEncryptionService.rotateEncryptionKey("test-trace-id");

        // 验证结果
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testCleanupExpiredData() {
        // 执行测试
        Mono<Integer> result = tracingEncryptionService.cleanupExpiredData();

        // 验证结果
        StepVerifier.create(result)
                .assertNext(cleanupCount -> {
                    assertNotNull(cleanupCount);
                    assertTrue(cleanupCount >= 0);
                })
                .verifyComplete();
    }

    @Test
    void testSecureCleanupTraceData() {
        // 执行测试
        Mono<Void> result = tracingEncryptionService.secureCleanupTraceData("test-trace-id");

        // 验证结果
        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void testEncryptionDisabled() {
        // 设置加密禁用
        when(encryptionConfig.isEnabled()).thenReturn(false);
        
        // 创建新的服务实例
        TracingEncryptionService disabledService = new TracingEncryptionService(tracingConfiguration, structuredLogger);

        String testData = "test data";

        // 执行测试
        Mono<String> result = disabledService.encryptTraceData(testData, "test-trace-id", "test-type");

        // 验证结果 - 应该返回原始数据或者空字符串
        StepVerifier.create(result)
                .assertNext(data -> {
                    assertNotNull(data);
                    // 当加密禁用时，可能返回原始数据或者空字符串
                })
                .verifyComplete();
    }

    @Test
    void testEncryptEmptyData() {
        // 准备测试数据
        String emptyData = "";

        // 执行测试
        Mono<String> result = tracingEncryptionService.encryptTraceData(emptyData, "test-trace-id", "test-type");

        // 验证结果
        StepVerifier.create(result)
                .verifyComplete(); // 空数据应该返回空的Mono
    }

    @Test
    void testDecryptInvalidData() {
        // 执行测试 - 使用无效的加密数据
        Mono<String> result = tracingEncryptionService.decryptTraceData("invalid-encrypted-data", "test-trace-id", "v1");

        // 验证结果 - 应该处理异常
        StepVerifier.create(result)
                .expectError()
                .verify();
    }

    @Test
    void testEncryptLargeData() {
        // 准备大量测试数据
        StringBuilder largeData = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeData.append("test data line ").append(i).append(" - this is a test string with some length\n");
        }

        // 执行测试
        Mono<String> result = tracingEncryptionService.encryptTraceData(largeData.toString(), "test-trace-id", "large-data");

        // 验证结果
        StepVerifier.create(result)
                .assertNext(encryptedData -> {
                    assertNotNull(encryptedData);
                    assertFalse(encryptedData.isEmpty());
                    // 大数据的加密应该能够正常处理
                })
                .verifyComplete();
    }

    @Test
    void testDataRetentionPolicy() {
        // 测试数据保留策略的配置
        when(dataRetention.getDefaultRetention()).thenReturn(Duration.ofDays(7));
        
        // 创建新的服务实例
        TracingEncryptionService serviceWithRetention = new TracingEncryptionService(tracingConfiguration, structuredLogger);

        // 执行清理测试
        Mono<Integer> result = serviceWithRetention.cleanupExpiredData();

        // 验证结果
        StepVerifier.create(result)
                .assertNext(cleanupCount -> {
                    assertNotNull(cleanupCount);
                    assertTrue(cleanupCount >= 0);
                })
                .verifyComplete();
    }

    @Test
    void testConcurrentEncryption() {
        // 准备测试数据
        String data1 = "test data 1";
        String data2 = "test data 2";

        // 执行并发加密测试
        Mono<String> result1 = tracingEncryptionService.encryptTraceData(data1, "trace-1", "type-1");
        Mono<String> result2 = tracingEncryptionService.encryptTraceData(data2, "trace-2", "type-2");

        // 验证结果
        StepVerifier.create(Mono.zip(result1, result2))
                .assertNext(tuple -> {
                    String encrypted1 = tuple.getT1();
                    String encrypted2 = tuple.getT2();
                    
                    assertNotNull(encrypted1);
                    assertNotNull(encrypted2);
                    assertNotEquals(encrypted1, encrypted2);
                })
                .verifyComplete();
    }

    @Test
    void testKeyRotationWithExistingData() {
        // 准备测试数据
        String testData = "test data for rotation";
        
        // 先加密数据
        String encryptedData = tracingEncryptionService.encryptTraceData(testData, "test-trace-id", "test-type").block();
        assertNotNull(encryptedData);

        // 执行密钥轮换
        Boolean rotationResult = tracingEncryptionService.rotateEncryptionKey("test-trace-id").block();
        assertTrue(rotationResult);

        // 验证轮换后仍能访问数据（如果实现了密钥管理）
        // 这里的具体验证取决于密钥轮换的实现策略
    }

    @Test
    void testEncryptionConfiguration() {
        // 测试不同的加密配置
        when(encryptionConfig.getAlgorithm()).thenReturn("AES");
        when(encryptionConfig.getKeySize()).thenReturn(128);
        
        // 创建新的服务实例
        TracingEncryptionService customService = new TracingEncryptionService(tracingConfiguration, structuredLogger);

        String data = "test config data";

        // 执行测试
        Mono<String> result = customService.encryptTraceData(data, "test-trace-id", "config-test");

        // 验证结果
        StepVerifier.create(result)
                .assertNext(encrypted -> {
                    assertNotNull(encrypted);
                    assertFalse(encrypted.isEmpty());
                })
                .verifyComplete();
    }

    @Test
    void testSecureDataHandling() {
        // 测试敏感数据的安全处理
        String sensitiveData = "password=secret123;token=jwt-token;creditCard=1234-5678-9012-3456";

        // 执行测试
        Mono<String> result = tracingEncryptionService.encryptTraceData(sensitiveData, "test-trace-id", "sensitive");

        // 验证结果
        StepVerifier.create(result)
                .assertNext(encrypted -> {
                    assertNotNull(encrypted);
                    // 确保敏感数据不以明文形式出现在加密结果中
                    assertFalse(encrypted.contains("secret123"));
                    assertFalse(encrypted.contains("jwt-token"));
                    assertFalse(encrypted.contains("1234-5678-9012-3456"));
                })
                .verifyComplete();
    }
}