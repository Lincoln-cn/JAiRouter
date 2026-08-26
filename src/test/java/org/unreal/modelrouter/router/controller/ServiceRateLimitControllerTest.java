package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.config.sync.repository.StoreConfigRepository;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ServiceRateLimitController RESTful 接口测试
 *
 * v2.8.8: PUT 做实 — 持久化(StoreManager)+ 热生效(RateLimitManager),GET/PUT 统一 canonical 格式
 */
@DisplayName("ServiceRateLimitController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceRateLimitControllerTest {

    @Mock
    private StoreConfigRepository storeConfigRepository;

    @Mock
    private RateLimitManager rateLimitManager;

    @Mock
    private ServiceTypeResolver serviceTypeResolver;

    @InjectMocks
    private ServiceRateLimitController controller;

    // ==================== 获取限流配置测试 ====================

    @Nested
    @DisplayName("GET /api/services/{serviceType}/ratelimit - 获取限流配置测试")
    class GetRateLimitConfigTests {

        @Test
        @DisplayName("RL-001: 成功获取限流配置(canonical 格式)")
        void testGetRateLimitConfig_success() {
            when(storeConfigRepository.findRateLimitRaw("chat")).thenReturn(Optional.of(
                    Map.of("enabled", true, "capacity", 100L, "rate", 10L)));

            ResponseEntity<Map<String, Object>> result = controller.getRateLimitConfig("chat");

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertTrue((Boolean) result.getBody().get("enabled"));
            assertEquals(100L, result.getBody().get("capacity"));
            assertEquals(10L, result.getBody().get("rate"));
        }

        @Test
        @DisplayName("RL-002: 服务不在配置存储时返回空配置")
        void testGetRateLimitConfig_notFound() {
            when(storeConfigRepository.findRateLimitRaw("unknown")).thenReturn(Optional.empty());

            ResponseEntity<Map<String, Object>> result = controller.getRateLimitConfig("unknown");

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertTrue(result.getBody().isEmpty());
        }

        @Test
        @DisplayName("RL-003: 服务无限流配置时返回空配置")
        void testGetRateLimitConfig_nullConfig() {
            when(storeConfigRepository.findRateLimitRaw("chat")).thenReturn(Optional.empty());

            ResponseEntity<Map<String, Object>> result = controller.getRateLimitConfig("chat");

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertNotNull(result.getBody());
            assertTrue(result.getBody().isEmpty());
        }
    }

    // ==================== 更新限流配置测试 ====================

    @Nested
    @DisplayName("PUT /api/services/{serviceType}/ratelimit - 更新限流配置测试")
    class UpdateRateLimitConfigTests {

        @Test
        @DisplayName("RL-004: 成功更新限流配置(持久化 + 热生效)")
        void testUpdateRateLimitConfig_success() {
            when(serviceTypeResolver.parseServiceType("chat")).thenReturn(ModelServiceRegistry.ServiceType.chat);
            Map<String, Object> newConfig = Map.of(
                    "enabled", true,
                    "algorithm", "token-bucket",
                    "capacity", 100L,
                    "rate", 10L,
                    "scope", "service");

            ResponseEntity<Map<String, Object>> result = controller.updateRateLimitConfig("chat", newConfig);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(newConfig, result.getBody());
            verify(storeConfigRepository).saveRateLimitRaw(eq("chat"), eq(newConfig));
            verify(rateLimitManager).setRateLimiter(eq(ModelServiceRegistry.ServiceType.chat), any());
        }

        @Test
        @DisplayName("RL-005: 禁用的限流配置走移除语义")
        void testUpdateRateLimitConfig_disabled() {
            when(serviceTypeResolver.parseServiceType("chat")).thenReturn(ModelServiceRegistry.ServiceType.chat);
            Map<String, Object> disabledConfig = Map.of("enabled", false);

            ResponseEntity<Map<String, Object>> result = controller.updateRateLimitConfig("chat", disabledConfig);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(storeConfigRepository).saveRateLimitRaw(eq("chat"), eq(disabledConfig));
            verify(rateLimitManager).setRateLimiter(eq(ModelServiceRegistry.ServiceType.chat), argThat(cfg -> !cfg.isEnabled()));
        }

        @Test
        @DisplayName("RL-006: 未知服务类型返回 404")
        void testUpdateRateLimitConfig_unknownServiceType() {
            when(serviceTypeResolver.parseServiceType("unknown")).thenReturn(null);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.updateRateLimitConfig("unknown", Map.of("enabled", true)));

            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
            verify(storeConfigRepository, never()).saveRateLimitRaw(anyString(), any());
        }

        @Test
        @DisplayName("RL-007: 启用但 capacity<=0 返回 400")
        void testUpdateRateLimitConfig_invalidCapacity() {
            when(serviceTypeResolver.parseServiceType("chat")).thenReturn(ModelServiceRegistry.ServiceType.chat);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.updateRateLimitConfig("chat", Map.of("enabled", true, "capacity", 0L, "rate", 10L)));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            verify(storeConfigRepository, never()).saveRateLimitRaw(anyString(), any());
        }

        @Test
        @DisplayName("RL-008: 启用但 rate<=0 返回 400")
        void testUpdateRateLimitConfig_invalidRate() {
            when(serviceTypeResolver.parseServiceType("chat")).thenReturn(ModelServiceRegistry.ServiceType.chat);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.updateRateLimitConfig("chat", Map.of("enabled", true, "capacity", 100L, "rate", 0L)));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            verify(storeConfigRepository, never()).saveRateLimitRaw(anyString(), any());
        }
    }
}
