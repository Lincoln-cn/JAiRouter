package org.unreal.modelrouter.router.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link RateLimitKeyDimension} 单元测试（#161）。
 *
 * <p>重点：配置取值写错时**回退默认维度**而不是报错或让限流失效——维度配置错误不应
 * 让应用起不来，也不应静默换成另一种维度。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.4
 */
class RateLimitKeyDimensionTest {

    @Test
    @DisplayName("未配置维度时默认按客户端 IP")
    void defaultsToClientIp() {
        assertEquals(RateLimitKeyDimension.CLIENT_IP, RateLimitKeyDimension.parse(null));
        assertEquals(RateLimitKeyDimension.CLIENT_IP, RateLimitKeyDimension.parse(""));
        assertEquals(RateLimitKeyDimension.CLIENT_IP, RateLimitKeyDimension.parse("   "));
    }

    @Test
    @DisplayName("解析大小写与连字符/下划线不敏感")
    void normalizationIsLenient() {
        assertEquals(RateLimitKeyDimension.CLIENT_IP, RateLimitKeyDimension.parse("client-ip"));
        assertEquals(RateLimitKeyDimension.CLIENT_IP, RateLimitKeyDimension.parse("CLIENT_IP"));
        assertEquals(RateLimitKeyDimension.CLIENT_IP, RateLimitKeyDimension.parse(" Client-Ip "));
        assertEquals(RateLimitKeyDimension.API_KEY, RateLimitKeyDimension.parse("api-key"));
        assertEquals(RateLimitKeyDimension.API_KEY, RateLimitKeyDimension.parse("API_KEY"));
        assertEquals(RateLimitKeyDimension.API_KEY, RateLimitKeyDimension.parse("Api-Key"));
        assertEquals(RateLimitKeyDimension.TENANT, RateLimitKeyDimension.parse("tenant"));
        assertEquals(RateLimitKeyDimension.TENANT, RateLimitKeyDimension.parse("TENANT"));
    }

    @Test
    @DisplayName("无法识别的取值回退客户端 IP，不抛异常")
    void unknownValueFallsBackToClientIp() {
        assertEquals(RateLimitKeyDimension.CLIENT_IP, RateLimitKeyDimension.parse("apikey"));
        assertEquals(RateLimitKeyDimension.CLIENT_IP, RateLimitKeyDimension.parse("user"));
        assertEquals(RateLimitKeyDimension.CLIENT_IP, RateLimitKeyDimension.parse("clientip"));
    }

    @Test
    @DisplayName("配置取值与枚举一一对应")
    void configValuesAreStable() {
        assertEquals("client-ip", RateLimitKeyDimension.CLIENT_IP.configValue());
        assertEquals("api-key", RateLimitKeyDimension.API_KEY.configValue());
        assertEquals("tenant", RateLimitKeyDimension.TENANT.configValue());
    }
}
