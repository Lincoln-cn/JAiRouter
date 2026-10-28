package org.unreal.modelrouter.auth.security.config.properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API Key 的 IP 白名单判定（#693）。
 *
 * <p>动因：认证链此前只调单参 {@code validateApiKey(key)}，把 IP 硬编码成 {@code null}，
 * 而 {@code isIpAllowed} 的实现在白名单非空时执行 {@code allowedIpAddresses.contains(null)}，
 * 恒为 false —— 白名单被实现成「一律拒绝」。本测试同时钉住三件事：
 * 真实 IP 能匹配、网段/书写形式能匹配、无法判定来源时 fail-closed。</p>
 *
 * @since 3.5.5
 */
@DisplayName("#693 IP 白名单判定")
class ApiKeyIpAllowlistTest {

    private static ApiKey keyWithAllowlist(final List<String> allowlist) {
        return ApiKey.builder()
                .keyId("key-ip")
                .allowedIpAddresses(allowlist)
                .build();
    }

    @Test
    @DisplayName("未配置白名单时放行任意来源")
    void noAllowlistAllowsEverything() {
        assertTrue(keyWithAllowlist(null).isIpAllowed(null));
        assertTrue(keyWithAllowlist(List.of()).isIpAllowed("203.0.113.9"));
    }

    @Test
    @DisplayName("精确 IPv4 命中白名单")
    void exactIpv4IsAllowed() {
        final ApiKey key = keyWithAllowlist(List.of("10.0.0.7"));
        assertTrue(key.isIpAllowed("10.0.0.7"));
        assertFalse(key.isIpAllowed("10.0.0.8"));
    }

    @Test
    @DisplayName("网段条目按 CIDR 匹配")
    void cidrRangeIsMatched() {
        final ApiKey key = keyWithAllowlist(List.of("192.168.1.0/24"));
        assertTrue(key.isIpAllowed("192.168.1.37"));
        assertTrue(key.isIpAllowed("192.168.1.0"));
        assertFalse(key.isIpAllowed("192.168.2.5"));
    }

    @Test
    @DisplayName("带端口/IPv6 的书写形式先规范化再比较")
    void writingFormsAreCanonicalized() {
        final ApiKey loopback = keyWithAllowlist(List.of("127.0.0.1"));
        assertTrue(loopback.isIpAllowed("::1"), "IPv6 localhost 应归一为 127.0.0.1");
        assertTrue(loopback.isIpAllowed("127.0.0.1:53124"), "带端口的 IPv4 应剥端口后匹配");

        final ApiKey v6 = keyWithAllowlist(List.of("2001:0db8::0001"));
        assertTrue(v6.isIpAllowed("2001:db8::1"), "IPv6 压缩/非压缩写法应视为同一地址");
    }

    @Test
    @DisplayName("白名单非空但无法判定来源时拒绝（fail-closed）")
    void unknownSourceIsRejected() {
        final ApiKey key = keyWithAllowlist(List.of("10.0.0.7"));
        assertFalse(key.isIpAllowed(null));
        assertFalse(key.isIpAllowed(""));
        assertFalse(key.isIpAllowed("   "));
        assertFalse(key.isIpAllowed("unknown"));
    }

    @Test
    @DisplayName("空白条目被忽略而不是放行一切")
    void blankEntriesAreIgnored() {
        final ApiKey key = keyWithAllowlist(List.of(" ", "", "10.0.0.7"));
        assertTrue(key.isIpAllowed("10.0.0.7"));
        assertFalse(key.isIpAllowed("10.0.0.8"));
    }
}
