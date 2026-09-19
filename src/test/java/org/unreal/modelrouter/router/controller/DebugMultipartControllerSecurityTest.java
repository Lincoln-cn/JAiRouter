package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.http.HttpHeaders;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R2-P0-03：调试端点不得回显明文凭据。
 */
@DisplayName("DebugMultipartController 凭据脱敏")
class DebugMultipartControllerSecurityTest {

    private final DebugMultipartController controller = new DebugMultipartController();

    private static String redactExpect(final String raw) {
        return DebugMultipartController.redactSecret(raw);
    }

    @Test
    @DisplayName("redactSecret：长凭据只保留首尾 4 位")
    void redactSecret_masksLongValue() {
        String raw = "sk-abcdefghijklmnopqrstuvwxyz0123456789";
        String masked = DebugMultipartController.redactSecret(raw);
        assertNotNull(masked);
        assertFalse(masked.contains("abcdefghijklmnopqrstuvwxyz"));
        assertTrue(masked.startsWith("sk-a") || masked.startsWith("sk-a".substring(0, 4)));
        assertTrue(masked.contains("***"));
    }

    @Test
    @DisplayName("redactSecret：null/空白返回 null")
    void redactSecret_nullSafe() {
        assertEquals(null, DebugMultipartController.redactSecret(null));
        assertEquals(null, DebugMultipartController.redactSecret("   "));
    }

    @Test
    @DisplayName("multipart-info 响应不含明文 Authorization / X-API-Key / Token")
    void debugMultipartInfo_doesNotEchoSecrets() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/v1/debug/multipart-info")
                .header("Authorization", "Bearer super-secret-jwt-value-abcdef")
                .header("X-API-Key", "sk-live-should-not-appear-here-xxxx")
                .header("Jairouter_Token", "jwt-token-should-not-appear-here")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(controller.debugMultipartInfo(exchange))
                .assertNext(entity -> {
                    Map<String, Object> body = entity.getBody();
                    assertNotNull(body);
                    String auth = String.valueOf(body.get("authorization"));
                    String apiKey = String.valueOf(body.get("xApiKey"));
                    String jtoken = String.valueOf(body.get("jairouterToken"));
                    String allHeaders = String.valueOf(body.get("allHeaders"));
                    assertFalse(auth.contains("super-secret-jwt-value-abcdef"), "authorization 不得明文: " + auth);
                    assertFalse(apiKey.contains("sk-live-should-not-appear-here-xxxx"), "xApiKey 不得明文");
                    assertFalse(jtoken.contains("jwt-token-should-not-appear-here"), "jairouterToken 不得明文");
                    assertFalse(allHeaders.contains("super-secret-jwt-value-abcdef"), "allHeaders 不得含明文凭据");
                    assertEquals(redactExpect("Bearer super-secret-jwt-value-abcdef"), auth);
                })
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("调试端点默认关闭（ConditionalOnProperty 缺省 false）")
    void debugEndpoints_disabledByDefault() {
        // 契约：默认配置下 Spring 不装配该 Bean —— 见类上 @ConditionalOnProperty
        // 此处断言常量语义，装配测试由应用上下文测试覆盖
        assertFalse(DebugMultipartController.DEBUG_ENDPOINTS_ENABLED_BY_DEFAULT);
    }
}
