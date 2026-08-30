package org.unreal.modelrouter.monitor.callhistory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.config.RecordLevel;
import org.unreal.modelrouter.monitor.callhistory.crypto.RecordContentCipher;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO;
import org.unreal.modelrouter.persistence.jpa.entity.ApiCallHistoryEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ApiCallHistoryRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ApiCallHistoryService 记录级别测试
 * 验证不同 RecordLevel 下 buildEntity 和 decryptBodies 的行为
 *
 * @author JAiRouter Team
 * @since 2.9.2
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiCallHistoryService 记录级别测试")
class ApiCallHistoryServiceRecordLevelTest {

    @Mock
    private ApiCallHistoryRepository repository;

    @InjectMocks
    private ApiCallHistoryService service;

    private CallHistoryProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        properties = new CallHistoryProperties();
        properties.setEnabled(true);
        properties.setRetentionDays(30);
        properties.setRequestBodySummaryEnabled(true);
        properties.setRequestBodySummaryMaxLength(200);
        properties.setResponseBodySummaryEnabled(true);
        properties.setResponseBodySummaryMaxLength(200);
        properties.setMaxContentLength(65536);

        // 创建真实的 RecordContentCipher（使用自动生成密钥，传入临时路径避免文件系统副作用）
        // 通过反射注入，因为 @InjectMocks 不支持三字段 final 构造
        javax.crypto.KeyGenerator keyGen = javax.crypto.KeyGenerator.getInstance("AES");
        keyGen.init(256);
        javax.crypto.SecretKey testKey = keyGen.generateKey();
        RecordContentCipher realCipher = new RecordContentCipher(testKey);

        var propField = ApiCallHistoryService.class.getDeclaredField("properties");
        propField.setAccessible(true);
        propField.set(service, properties);

        var cipherField = ApiCallHistoryService.class.getDeclaredField("recordContentCipher");
        cipherField.setAccessible(true);
        cipherField.set(service, realCipher);
    }

    private CallHistoryRecordDTO createTestRecord() {
        return CallHistoryRecordDTO.builder()
                .traceId("trace-rg-001")
                .requestId("req-rg-001")
                .requestMethod("POST")
                .requestPath("/v1/chat/completions")
                .contentType("application/json")
                .serviceType("chat")
                .modelName("gpt-4")
                .provider("openai")
                .httpStatusCode(200)
                .promptTokens(100L)
                .completionTokens(50L)
                .totalTokens(150L)
                .responseTimeMs(500L)
                .isSuccess(true)
                .build();
    }

    @Nested
    @DisplayName("METADATA_ONLY 级别测试")
    class MetadataOnlyTests {

        @BeforeEach
        void setMetadataOnly() {
            properties.setRecordLevel(RecordLevel.METADATA_ONLY);
        }

        @Test
        @DisplayName("METADATA_ONLY - 不记录请求/响应体内容")
        void testMetadataOnlyNoBodyCapture() {
            CallHistoryRecordDTO dto = createTestRecord();
            dto.setRequestBody("{\"prompt\":\"Hello\"}");
            dto.setResponseBody("{\"response\":\"World\"}");

            when(repository.save(any(ApiCallHistoryEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ApiCallHistoryEntity result = service.record(dto);

            assertNotNull(result);
            assertEquals("METADATA_ONLY", result.getRecordLevel());
            assertNull(result.getRequestBodyEncrypted(), "METADATA_ONLY 不应有加密请求体");
            assertNull(result.getResponseBodyEncrypted(), "METADATA_ONLY 不应有加密响应体");
            // 摘要列根据配置可能有值
            if (properties.isRequestBodySummaryEnabled()) {
                assertNotNull(result.getRequestBodySummary(), "METADATA_ONLY 根据配置应生成摘要");
            }
        }

        @Test
        @DisplayName("METADATA_ONLY - 摘要功能关闭时不生成摘要")
        void testMetadataOnlySummaryDisabled() {
            properties.setRequestBodySummaryEnabled(false);
            properties.setResponseBodySummaryEnabled(false);

            CallHistoryRecordDTO dto = createTestRecord();
            dto.setRequestBody("{\"prompt\":\"Hello\"}");
            dto.setResponseBody("{\"response\":\"World\"}");

            when(repository.save(any(ApiCallHistoryEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ApiCallHistoryEntity result = service.record(dto);

            assertNull(result.getRequestBodySummary());
            assertNull(result.getResponseBodySummary());
        }
    }

    @Nested
    @DisplayName("SUMMARY 级别测试")
    class SummaryTests {

        @BeforeEach
        void setSummaryLevel() {
            properties.setRecordLevel(RecordLevel.SUMMARY);
        }

        @Test
        @DisplayName("SUMMARY - 脱敏内容截断后存入摘要列")
        void testSummaryLevelTruncation() {
            CallHistoryRecordDTO dto = createTestRecord();
            String desensitizedBody = "用户: ****, 消息: 你好";
            dto.setRequestBody(desensitizedBody);
            dto.setResponseBody("响应: ****");

            when(repository.save(any(ApiCallHistoryEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ApiCallHistoryEntity result = service.record(dto);

            assertNotNull(result);
            assertEquals("SUMMARY", result.getRecordLevel());
            assertNotNull(result.getRequestBodySummary());
            assertTrue(result.getRequestBodySummary().contains("****"));
            assertNull(result.getRequestBodyEncrypted(), "SUMMARY 不应有加密请求体");
            assertNull(result.getResponseBodyEncrypted(), "SUMMARY 不应有加密响应体");
        }

        @Test
        @DisplayName("SUMMARY - 内容超过 maxContentLength 时截断")
        void testSummaryLevelMaxContentTruncation() {
            properties.setMaxContentLength(100);
            CallHistoryRecordDTO dto = createTestRecord();
            dto.setRequestBody("A".repeat(200));
            dto.setResponseBody("B".repeat(200));

            when(repository.save(any(ApiCallHistoryEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ApiCallHistoryEntity result = service.record(dto);

            assertNotNull(result);
            assertEquals("SUMMARY", result.getRecordLevel());
            // 摘要列内容应被截断到 maxLength（200）+ "...[truncated]"
            assertNotNull(result.getRequestBodySummary());
            assertTrue(result.getRequestBodySummary().length() <= 220,
                    "摘要不应超过 maxLength + 截断标记长度");
        }
    }

    @Nested
    @DisplayName("FULL 级别测试")
    class FullTests {

        @BeforeEach
        void setFullLevel() {
            properties.setRecordLevel(RecordLevel.FULL);
        }

        @Test
        @DisplayName("FULL - 请求/响应体加密存储，摘要列有预览")
        void testFullLevelEncryption() {
            CallHistoryRecordDTO dto = createTestRecord();
            String rawRequestBody = "{\"model\":\"gpt-4\",\"messages\":[{\"role\":\"user\",\"content\":\"Hello World\"}]}";
            String rawResponseBody = "{\"choices\":[{\"message\":{\"content\":\"Hi there!\"}}]}";
            dto.setRequestBody(rawRequestBody);
            dto.setResponseBody(rawResponseBody);

            when(repository.save(any(ApiCallHistoryEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ApiCallHistoryEntity result = service.record(dto);

            assertNotNull(result);
            assertEquals("FULL", result.getRecordLevel());

            // 加密列应有值
            assertNotNull(result.getRequestBodyEncrypted(), "FULL 应有加密请求体");
            assertNotNull(result.getResponseBodyEncrypted(), "FULL 应有加密响应体");

            // 加密内容不应等于原始内容
            assertNotEquals(rawRequestBody, result.getRequestBodyEncrypted());
            assertNotEquals(rawResponseBody, result.getResponseBodyEncrypted());

            // 摘要列应有预览内容
            assertNotNull(result.getRequestBodySummary(), "FULL 应有请求体摘要预览");
            assertNotNull(result.getResponseBodySummary(), "FULL 应有响应体摘要预览");
        }

        @Test
        @DisplayName("FULL - null 内容不加密")
        void testFullLevelNullContent() {
            CallHistoryRecordDTO dto = createTestRecord();
            dto.setRequestBody(null);
            dto.setResponseBody(null);

            when(repository.save(any(ApiCallHistoryEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ApiCallHistoryEntity result = service.record(dto);

            assertNull(result.getRequestBodyEncrypted());
            assertNull(result.getResponseBodyEncrypted());
        }

        @Test
        @DisplayName("FULL - 内容超过 maxContentLength 时截断后加密")
        void testFullLevelMaxContentTruncation() {
            properties.setMaxContentLength(50);
            CallHistoryRecordDTO dto = createTestRecord();
            String longBody = "X".repeat(200);
            dto.setRequestBody(longBody);
            dto.setResponseBody(longBody);

            when(repository.save(any(ApiCallHistoryEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ApiCallHistoryEntity result = service.record(dto);

            assertNotNull(result.getRequestBodyEncrypted());
            assertNotNull(result.getResponseBodyEncrypted());

            // 验证解密后内容被截断到 maxContentLength
            ApiCallHistoryEntity decrypted = service.decryptBodies(result);
            assertEquals("X".repeat(50), decrypted.getRequestBodyDecrypted());
            assertEquals("X".repeat(50), decrypted.getResponseBodyDecrypted());
        }
    }

    @Nested
    @DisplayName("decryptBodies 测试")
    class DecryptBodiesTests {

        @BeforeEach
        void setFullLevel() {
            properties.setRecordLevel(RecordLevel.FULL);
        }

        @Test
        @DisplayName("decryptBodies 往返解密正确")
        void testDecryptBodiesRoundTrip() {
            String originalRequest = "{\"prompt\":\"测试解密功能\"}";
            String originalResponse = "{\"result\":\"解密成功\"}";

            CallHistoryRecordDTO dto = createTestRecord();
            dto.setRequestBody(originalRequest);
            dto.setResponseBody(originalResponse);

            when(repository.save(any(ApiCallHistoryEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ApiCallHistoryEntity saved = service.record(dto);

            // 解密
            ApiCallHistoryEntity decrypted = service.decryptBodies(saved);

            assertNotNull(decrypted);
            assertEquals(originalRequest, decrypted.getRequestBodyDecrypted());
            assertEquals(originalResponse, decrypted.getResponseBodyDecrypted());
        }

        @Test
        @DisplayName("decryptBodies null 实体安全处理")
        void testDecryptBodiesNullEntity() {
            ApiCallHistoryEntity result = service.decryptBodies(null);
            assertNull(result);
        }

        @Test
        @DisplayName("decryptBodies 无加密内容时安全处理")
        void testDecryptBodiesNoEncryptedContent() {
            ApiCallHistoryEntity entity = ApiCallHistoryEntity.builder()
                    .traceId("test")
                    .requestId("test")
                    .recordLevel("METADATA_ONLY")
                    .build();

            ApiCallHistoryEntity result = service.decryptBodies(entity);

            assertNotNull(result);
            assertNull(result.getRequestBodyDecrypted());
            assertNull(result.getResponseBodyDecrypted());
        }
    }
}
