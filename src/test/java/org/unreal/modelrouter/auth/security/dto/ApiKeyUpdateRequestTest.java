package org.unreal.modelrouter.auth.security.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiKeyUpdateRequest DTO 测试
 */
@DisplayName("ApiKeyUpdateRequest DTO 测试")
class ApiKeyUpdateRequestTest {

    @Test
    @DisplayName("使用Builder创建请求 - 所有字段")
    void builder_AllFields_CreatesRequest() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(30);
        
        ApiKeyUpdateRequest request = ApiKeyUpdateRequest.builder()
                .description("Updated Description")
                .permissions(List.of("READ", "ADMIN"))
                .enabled(false)
                .expiresAt(expiresAt)
                .allowedIpAddresses(List.of("10.0.0.1"))
                .dailyRequestLimit(2000L)
                .rotationPeriodDays(60)
                .build();

        assertEquals("Updated Description", request.getDescription());
        assertEquals(2, request.getPermissions().size());
        assertFalse(request.getEnabled());
        assertEquals(expiresAt, request.getExpiresAt());
        assertEquals(1, request.getAllowedIpAddresses().size());
        assertEquals(2000L, request.getDailyRequestLimit());
        assertEquals(60, request.getRotationPeriodDays());
    }

    @Test
    @DisplayName("使用Builder创建请求 - 仅部分字段")
    void builder_PartialFields_CreatesRequest() {
        ApiKeyUpdateRequest request = ApiKeyUpdateRequest.builder()
                .description("Only description")
                .enabled(true)
                .build();

        assertEquals("Only description", request.getDescription());
        assertNull(request.getPermissions());
        assertTrue(request.getEnabled());
        assertNull(request.getExpiresAt());
        assertNull(request.getAllowedIpAddresses());
        assertNull(request.getDailyRequestLimit());
        assertNull(request.getRotationPeriodDays());
    }

    @Test
    @DisplayName("使用无参构造函数创建请求")
    void noArgsConstructor_CreatesRequest() {
        ApiKeyUpdateRequest request = new ApiKeyUpdateRequest();
        
        assertNull(request.getDescription());
        assertNull(request.getPermissions());
        assertNull(request.getEnabled());
        assertNull(request.getExpiresAt());
        assertNull(request.getAllowedIpAddresses());
        assertNull(request.getDailyRequestLimit());
        assertNull(request.getRotationPeriodDays());
    }

    @Test
    @DisplayName("使用全参构造函数创建请求")
    void allArgsConstructor_CreatesRequest() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(15);
        
        ApiKeyUpdateRequest request = new ApiKeyUpdateRequest(
                "Full Update",
                List.of("DELETE"),
                true,
                expiresAt,
                List.of("172.16.0.0/12"),
                3000L,
                200000L,
                120,
                0.9,
                45
        );

        assertEquals("Full Update", request.getDescription());
        assertEquals(1, request.getPermissions().size());
        assertTrue(request.getEnabled());
        assertEquals(expiresAt, request.getExpiresAt());
        assertEquals(1, request.getAllowedIpAddresses().size());
        assertEquals(3000L, request.getDailyRequestLimit());
        assertEquals(200000L, request.getDailyTokenLimit());
        assertEquals(120, request.getRateLimitPerMinute());
        assertEquals(0.9, request.getQuotaAlertThreshold());
        assertEquals(45, request.getRotationPeriodDays());
    }

    @Test
    @DisplayName("Setter方法 - 正确设置字段")
    void setters_SetFieldsCorrectly() {
        ApiKeyUpdateRequest request = new ApiKeyUpdateRequest();
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(10);

        request.setDescription("Updated");
        request.setPermissions(List.of("WRITE"));
        request.setEnabled(true);
        request.setExpiresAt(expiresAt);
        request.setAllowedIpAddresses(List.of("192.168.1.0/24"));
        request.setDailyRequestLimit(5000L);
        request.setRotationPeriodDays(30);

        assertEquals("Updated", request.getDescription());
        assertEquals(1, request.getPermissions().size());
        assertTrue(request.getEnabled());
        assertEquals(expiresAt, request.getExpiresAt());
        assertEquals(1, request.getAllowedIpAddresses().size());
        assertEquals(5000L, request.getDailyRequestLimit());
        assertEquals(30, request.getRotationPeriodDays());
    }

    @Test
    @DisplayName("Equals和HashCode - 相同对象相等")
    void equalsHashCode_SameObjects_AreEqual() {
        ApiKeyUpdateRequest request1 = ApiKeyUpdateRequest.builder()
                .description("Test")
                .enabled(true)
                .build();

        ApiKeyUpdateRequest request2 = ApiKeyUpdateRequest.builder()
                .description("Test")
                .enabled(true)
                .build();

        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }

    @Test
    @DisplayName("Equals和HashCode - 不同对象不相等")
    void equalsHashCode_DifferentObjects_NotEqual() {
        ApiKeyUpdateRequest request1 = ApiKeyUpdateRequest.builder()
                .description("Test1")
                .build();

        ApiKeyUpdateRequest request2 = ApiKeyUpdateRequest.builder()
                .description("Test2")
                .build();

        assertNotEquals(request1, request2);
    }

    @Test
    @DisplayName("ToString - 包含字段")
    void toString_ContainsFields() {
        ApiKeyUpdateRequest request = ApiKeyUpdateRequest.builder()
                .description("Update Desc")
                .enabled(false)
                .build();

        String str = request.toString();
        assertTrue(str.contains("Update Desc"));
        assertTrue(str.contains("false"));
    }

    @Test
    @DisplayName("空权限列表 - 可以设置空列表")
    void builder_EmptyPermissions_IsValid() {
        ApiKeyUpdateRequest request = ApiKeyUpdateRequest.builder()
                .permissions(List.of())
                .build();

        assertNotNull(request.getPermissions());
        assertTrue(request.getPermissions().isEmpty());
    }

    @Test
    @DisplayName("禁用状态 - 可以设置为false")
    void builder_Disabled_CanBeFalse() {
        ApiKeyUpdateRequest request = ApiKeyUpdateRequest.builder()
                .enabled(false)
                .build();

        assertFalse(request.getEnabled());
    }

    @Test
    @DisplayName("零限制 - dailyRequestLimit为0表示无限制")
    void builder_ZeroLimit_MeansUnlimited() {
        ApiKeyUpdateRequest request = ApiKeyUpdateRequest.builder()
                .dailyRequestLimit(0L)
                .build();

        assertEquals(0L, request.getDailyRequestLimit());
    }

    @Test
    @DisplayName("Builder链式调用 - 正确工作")
    void builder_ChainedCalls_WorksCorrectly() {
        ApiKeyUpdateRequest request = ApiKeyUpdateRequest.builder()
                .description("a")
                .permissions(List.of("b"))
                .enabled(true)
                .dailyRequestLimit(100L)
                .build();

        assertNotNull(request);
        assertEquals("a", request.getDescription());
        assertEquals(100L, request.getDailyRequestLimit());
    }
}