package org.unreal.modelrouter.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * #703：baseUrl 规范化必须保留 query（过去只用 scheme/host/port/path 重建，查询串被静默丢弃）。
 *
 * <p>用公网 IP 字面量做被测主机，避免依赖 DNS。</p>
 */
@DisplayName("#703 URL 规范化保留 query")
class UrlSecurityValidatorQueryTest {

    private static final String HOST = "93.184.216.34";

    @Test
    @DisplayName("Q-001: 查询串必须原样保留")
    void queryIsPreserved() {
        assertEquals("https://" + HOST + "/v1?api-version=2024-01",
                UrlSecurityValidator.validateOutboundBaseUrl("https://" + HOST + "/v1?api-version=2024-01"));
    }

    @Test
    @DisplayName("Q-002: 根路径 + 查询串时不得留下多余斜杠")
    void rootPathWithQueryHasNoTrailingSlash() {
        assertEquals("https://" + HOST + "?token=abc",
                UrlSecurityValidator.validateOutboundBaseUrl("https://" + HOST + "/?token=abc"));
    }

    @Test
    @DisplayName("Q-003: 多参数与百分号编码原样保留，不重排")
    void rawQueryIsNotReorderedOrReencoded() {
        assertEquals("https://" + HOST + "/v1?a=1&b=%2F&c=x+y",
                UrlSecurityValidator.validateOutboundBaseUrl("https://" + HOST + "/v1?a=1&b=%2F&c=x+y"));
    }

    @Test
    @DisplayName("Q-004: fragment 仍被丢弃（不进 baseUrl）")
    void fragmentIsStillDropped() {
        final String normalized = UrlSecurityValidator.validateOutboundBaseUrl("https://" + HOST + "/v1#section");
        assertEquals("https://" + HOST + "/v1", normalized);
        assertFalse(normalized.contains("#"), "fragment 不得出现在规范化结果里");
    }

    @Test
    @DisplayName("Q-005: 无查询串时行为不变")
    void noQueryKeepsPreviousShape() {
        assertEquals("https://" + HOST + "/v1",
                UrlSecurityValidator.validateOutboundBaseUrl("https://" + HOST + "/v1"));
        assertEquals("https://" + HOST,
                UrlSecurityValidator.validateOutboundBaseUrl("https://" + HOST + "/"));
    }

    @Test
    @DisplayName("Q-006: 带 query 不改变危险主机的拦截")
    void blockedHostWithQueryIsStillRejected() {
        assertThrows(SecurityException.class,
                () -> UrlSecurityValidator.validateOutboundBaseUrl("http://169.254.169.254/latest?x=1"));
    }
}
