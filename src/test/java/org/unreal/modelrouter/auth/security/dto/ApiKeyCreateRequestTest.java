package org.unreal.modelrouter.auth.security.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiKeyCreateRequest DTO 测试
 */
@DisplayName("ApiKeyCreateRequest DTO 测试")
class ApiKeyCreateRequestTest {

    @Test
    @DisplayName("使用Builder创建请求 - 所有字段")
    void builder_AllFields_CreatesRequest() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);
        
        ApiKeyCreateRequest request = ApiKeyCreateRequest.builder()
                .keyId("test-key-id")
                .description("Test API Key")
                .permissions(List.of("READ", "WRITE"))
                .enabled(true)
                .expiresAt(expiresAt)
                .allowedIpAddresses(List.of("192.168.1.1", "10.0.0.1"))
                .dailyRequestLimit(1000L)
                .rotationPeriodDays(90)
                .build();

        assertEquals("test-key-id", request.getKeyId());
        assertEquals("Test API Key", request.getDescription());
        assertEquals(2, request.getPermissions().size());
        assertTrue(request.getEnabled());
        assertEquals(expiresAt, request.getExpiresAt());
        assertEquals(2, request.getAllowedIpAddresses().size());
        assertEquals(1000L, request.getDailyRequestLimit());
        assertEquals(90, request.getRotationPeriodDays());
    }

    @Test
    @DisplayName("使用Builder创建请求 - 仅必填字段")
    void builder_RequiredFieldsOnly_CreatesRequest() {
        ApiKeyCreateRequest request = ApiKeyCreateRequest.builder()
                .description("Minimal Key")
                .build();

        assertNull(request.getKeyId());
        assertEquals("Minimal Key", request.getDescription());
        assertNull(request.getPermissions());
        assertTrue(request.getEnabled()); // Default value
        assertNull(request.getExpiresAt());
        assertNull(request.getAllowedIpAddresses());
        assertEquals(0L, request.getDailyRequestLimit()); // Default value
        assertEquals(0, request.getRotationPeriodDays()); // Default value
    }

    @Test
    @DisplayName("使用无参构造函数创建请求")
    void noArgsConstructor_CreatesRequest() {
        ApiKeyCreateRequest request = new ApiKeyCreateRequest();
        
        assertNull(request.getKeyId());
        assertNull(request.getDescription());
        assertNull(request.getPermissions());
        // @Builder.Default fields are initialized
        assertTrue(request.getEnabled()); // Default is true
        assertNull(request.getExpiresAt());
        assertNull(request.getAllowedIpAddresses());
        assertEquals(0L, request.getDailyRequestLimit()); // Default is 0
        assertEquals(0, request.getRotationPeriodDays()); // Default is 0
    }

    @Test
    @DisplayName("使用全参构造函数创建请求")
    void allArgsConstructor_CreatesRequest() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);
        
        ApiKeyCreateRequest request = new ApiKeyCreateRequest(
                "key-123",
                "Description",
                List.of("ADMIN"),
                false,
                expiresAt,
                List.of("127.0.0.1"),
                500L,
                30
        );

        assertEquals("key-123", request.getKeyId());
        assertEquals("Description", request.getDescription());
        assertEquals(1, request.getPermissions().size());
        assertFalse(request.getEnabled());
        assertEquals(expiresAt, request.getExpiresAt());
        assertEquals(1, request.getAllowedIpAddresses().size());
        assertEquals(500L, request.getDailyRequestLimit());
        assertEquals(30, request.getRotationPeriodDays());
    }

    @Test
    @DisplayName("Setter方法 - 正确设置字段")
    void setters_SetFieldsCorrectly() {
        ApiKeyCreateRequest request = new ApiKeyCreateRequest();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        request.setKeyId("new-key");
        request.setDescription("New Description");
        request.setPermissions(List.of("READ"));
        request.setEnabled(false);
        request.setExpiresAt(expiresAt);
        request.setAllowedIpAddresses(List.of("192.168.0.0/16"));
        request.setDailyRequestLimit(100L);
        request.setRotationPeriodDays(7);

        assertEquals("new-key", request.getKeyId());
        assertEquals("New Description", request.getDescription());
        assertEquals(1, request.getPermissions().size());
        assertFalse(request.getEnabled());
        assertEquals(expiresAt, request.getExpiresAt());
        assertEquals(1, request.getAllowedIpAddresses().size());
        assertEquals(100L, request.getDailyRequestLimit());
        assertEquals(7, request.getRotationPeriodDays());
    }

    @Test
    @DisplayName("Equals和HashCode - 相同对象相等")
    void equalsHashCode_SameObjects_AreEqual() {
        ApiKeyCreateRequest request1 = ApiKeyCreateRequest.builder()
                .keyId("key-1")
                .description("Test")
                .build();

        ApiKeyCreateRequest request2 = ApiKeyCreateRequest.builder()
                .keyId("key-1")
                .description("Test")
                .build();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    @DisplayName("Equals和HashCode - 不同对象不相等")
    void equalsHashCode_DifferentObjects_NotEqual() {
        ApiKeyCreateRequest request1 = ApiKeyCreateRequest.builder()
                .keyId("key-1")
                .build();

        ApiKeyCreateRequest request2 = ApiKeyCreateRequest.builder()
                .keyId("key-2")
                .build();

        assertNotEquals(request1, request2);
    }

    @Test
    @DisplayName("ToString - 包含所有字段")
    void toString_ContainsAllFields() {
        ApiKeyCreateRequest request = ApiKeyCreateRequest.builder()
                .keyId("test-key")
                .description("Test Description")
                .build();

        String str = request.toString();
        assertTrue(str.contains("test-key"));
        assertTrue(str.contains("Test Description"));
    }

    @Test
    @DisplayName("Builder默认值 - enabled为true")
    void builder_DefaultValues_EnabledIsTrue() {
        ApiKeyCreateRequest request = ApiKeyCreateRequest.builder().build();
        assertTrue(request.getEnabled());
    }

    @Test
    @DisplayName("Builder默认值 - dailyRequestLimit为0")
    void builder_DefaultValues_DailyRequestLimitIsZero() {
        ApiKeyCreateRequest request = ApiKeyCreateRequest.builder().build();
        assertEquals(0L, request.getDailyRequestLimit());
    }

    @Test
    @DisplayName("Builder默认值 - rotationPeriodDays为0")
    void builder_DefaultValues_RotationPeriodDaysIsZero() {
        ApiKeyCreateRequest request = ApiKeyCreateRequest.builder().build();
        assertEquals(0, request.getRotationPeriodDays());
    }

    @Test
    @DisplayName("Builder链式调用 - 正确工作")
    void builder_ChainedCalls_WorksCorrectly() {
        ApiKeyCreateRequest.ApiKeyCreateRequestBuilder builder = ApiKeyCreateRequest.builder();
        
        ApiKeyCreateRequest request = builder
                .keyId("a")
                .description("b")
                .permissions(List.of("c"))
                .enabled(true)
                .build();

        assertNotNull(request);
        assertEquals("a", request.getKeyId());
        assertEquals("b", request.getDescription());
    }
}