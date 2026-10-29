package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.permission.PermissionAuthorizationManager;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 跨源配置解析的单元测试（issue #818）。
 *
 * <p>覆盖三件事：未配置时保持「不启用」、配置项按逗号切分并去空白、
 * 生成的 {@link CorsConfiguration} 允许配置的来源并暴露限流/配额响应头。</p>
 */
class SecurityConfigurationCorsTest {

    private SecurityConfiguration newConfiguration(final String allowedOrigins) throws Exception {
        final SecurityConfiguration configuration = new SecurityConfiguration(
                mock(SecurityProperties.class),
                mock(ApiKeyService.class),
                mock(PermissionAuthorizationManager.class),
                mock(ApplicationContext.class));
        final Field field = SecurityConfiguration.class.getDeclaredField("corsAllowedOrigins");
        field.setAccessible(true);
        field.set(configuration, allowedOrigins);
        return configuration;
    }

    @Test
    @DisplayName("CORS-001: 未配置来源时不启用跨源支持")
    void blankOriginsMeansDisabled() throws Exception {
        assertTrue(newConfiguration("").parseCorsAllowedOrigins().isEmpty());
        assertTrue(newConfiguration("   ").parseCorsAllowedOrigins().isEmpty());
        assertTrue(newConfiguration(null).parseCorsAllowedOrigins().isEmpty());
    }

    @Test
    @DisplayName("CORS-002: 逗号分隔的来源会被切分并去掉空白与空项")
    void originsAreTrimmedAndFiltered() throws Exception {
        final List<String> origins = newConfiguration(
                " http://localhost:5173 , https://console.example.com ,, ")
                .parseCorsAllowedOrigins();
        assertEquals(List.of("http://localhost:5173", "https://console.example.com"), origins);
    }

    @Test
    @DisplayName("CORS-003: 只允许配置的来源，并暴露限流与配额响应头")
    void corsConfigurationMatchesConfiguredOrigins() throws Exception {
        final List<String> origins = List.of("https://console.example.com");
        final CorsConfigurationSource source = newConfiguration("https://console.example.com")
                .corsConfigurationSource(origins);
        final CorsConfiguration config = source.getCorsConfiguration(
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/models")));

        assertNotNull(config, "配置了来源就必须能取出 CorsConfiguration");
        assertEquals(origins, config.getAllowedOrigins());
        assertTrue(config.getAllowedMethods().contains("POST"));
        assertTrue(config.getAllowedMethods().contains("OPTIONS"));
        assertTrue(config.getAllowedHeaders().contains("*"));
        assertNotNull(config.getExposedHeaders());
        assertTrue(config.getExposedHeaders().contains("Retry-After"),
                "浏览器端要能读到重试提示");
        assertTrue(config.getExposedHeaders().contains("X-Quota-Remaining"));
        assertFalse(config.getAllowCredentials(), "凭据走自定义头，不需要 allowCredentials");
    }
}
