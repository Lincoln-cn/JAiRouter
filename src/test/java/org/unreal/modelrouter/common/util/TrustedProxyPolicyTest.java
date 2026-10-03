package org.unreal.modelrouter.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TrustedProxyPolicy} 与 {@link ClientIpResolver} 的单元测试（#151 Stage 3）。
 *
 * <p>覆盖设计文档 §3.5 的书写形式矩阵与 §2.3 的「右向左走跳」决策。这些是安全性质：
 * 断言写错会让伪造的 XFF 被采信。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.6
 */
@DisplayName("可信代理策略与客户端 IP 推导")
class TrustedProxyPolicyTest {

    // ---------- §3.5 书写形式 ----------

    @Test
    @DisplayName("规范化：剥端口、去方括号、IPv6 压缩、::1 归一")
    void canonicalization() {
        assertEquals("10.0.0.1", IpUtils.canonicalizeIp(" 10.0.0.1 "));
        assertEquals("10.0.0.1", IpUtils.canonicalizeIp("10.0.0.1:8080"));
        assertEquals("127.0.0.1", IpUtils.canonicalizeIp("::1"));
        assertEquals("127.0.0.1", IpUtils.canonicalizeIp("[::1]:8080"));
        assertEquals("127.0.0.1", IpUtils.canonicalizeIp("0:0:0:0:0:0:0:1"));
        assertEquals("unknown", IpUtils.canonicalizeIp(null));
        assertEquals("unknown", IpUtils.canonicalizeIp("   "));
    }

    @Test
    @DisplayName("裸 IPv6 不被误剥端口（2001:db8::1 的 :1 不是端口）")
    void bareIpv6IsNotStripped() {
        final String canonical = IpUtils.canonicalizeIp("2001:db8::1");
        assertTrue(canonical.startsWith("2001:db8") || canonical.startsWith("2001:0db8"),
                "裸 IPv6 不应被截断，实际=" + canonical);
        assertFalse(canonical.equals("2001"), "不得剥成 2001");
    }

    @Test
    @DisplayName("IPv6 大小写与压缩等价形式归一为同一表示")
    void ipv6Equivalence() {
        assertEquals(IpUtils.canonicalizeIp("FE80::1"), IpUtils.canonicalizeIp("fe80:0:0:0:0:0:0:1"));
    }

    // ---------- 匹配语义 ----------

    @Test
    @DisplayName("空配置即空策略，谁也不信")
    void emptyPolicyTrustsNobody() {
        assertTrue(TrustedProxyPolicy.parse(null).isEmpty());
        assertTrue(TrustedProxyPolicy.parse("").isEmpty());
        assertTrue(TrustedProxyPolicy.parse("  ,  ").isEmpty());
        assertFalse(TrustedProxyPolicy.empty().trusts("10.0.0.1"));
    }

    @Test
    @DisplayName("IPv4 CIDR 支持网段内匹配、网段外不匹配")
    void ipv4CidrMatching() {
        final TrustedProxyPolicy policy = TrustedProxyPolicy.parse("10.0.0.0/8, 192.168.1.0/24");

        assertTrue(policy.trusts("10.1.2.3"), "10.0.0.0/8 内应可信");
        assertTrue(policy.trusts("192.168.1.200"), "192.168.1.0/24 内应可信");
        assertFalse(policy.trusts("192.168.2.1"), "网段外不应可信");
        assertFalse(policy.trusts("203.0.113.9"), "公网地址不应可信");
    }

    @Test
    @DisplayName("单个 IP 等价于 /32；配置带端口也能匹配上直连对端")
    void exactIpAndPortForm() {
        assertTrue(TrustedProxyPolicy.parse("10.0.0.1").trusts("10.0.0.1"));
        assertTrue(TrustedProxyPolicy.parse("10.0.0.1:8080").trusts("10.0.0.1"),
                "配置项允许写成 upstream 地址");
        assertFalse(TrustedProxyPolicy.parse("10.0.0.1").trusts("10.0.0.2"));
    }

    @Test
    @DisplayName("IPv6 精确匹配可命中；IPv6 CIDR 条目不参与匹配（不误匹配）")
    void ipv6ExactOnly() {
        assertTrue(TrustedProxyPolicy.parse("fe80::1").trusts("FE80::1"));
        // IPv6 CIDR 被跳过：既不应命中 2001:db8 网段，也不应因解析失败而命中一切
        assertFalse(TrustedProxyPolicy.parse("2001:db8::/32").trusts("2001:db8::1"),
                "IPv6 CIDR 不在 Stage 3 范围，必须显式不支持而不是误匹配");
    }

    // ---------- §2.3 右向左走跳 ----------

    @Test
    @DisplayName("对端不可信时完全忽略转发头（防伪造核心）")
    void untrustedPeerIgnoresForwardedHeaders() {
        ClientIpResolver.setPolicy(TrustedProxyPolicy.empty());
        try {
            assertEquals("203.0.113.7",
                    ClientIpResolver.resolve("203.0.113.7", "198.51.100.1", "198.51.100.2"));
        } finally {
            ClientIpResolver.setPolicy(TrustedProxyPolicy.empty());
        }
    }

    @Test
    @DisplayName("对端可信时取 XFF 中最后一个非可信跳（跳过我们家自己的多级代理）")
    void trustedPeerWalksRightToLeftSkippingTrustedHops() {
        ClientIpResolver.setPolicy(TrustedProxyPolicy.parse("10.0.0.0/8"));
        try {
            // client(203.0.113.5) -> proxy1(10.0.0.1) -> proxy2(10.0.0.2) -> 我们
            assertEquals("203.0.113.5",
                    ClientIpResolver.resolve("10.0.0.2", "203.0.113.5, 10.0.0.1, 10.0.0.2", null),
                    "应跳过 10.0.0.0/8 内的代理跳，得到真实客户端");
        } finally {
            ClientIpResolver.setPolicy(TrustedProxyPolicy.empty());
        }
    }

    @Test
    @DisplayName("伪造前缀不影响取值——只信右侧由我方代理追加的部分")
    void spoofedPrefixIsIgnored() {
        ClientIpResolver.setPolicy(TrustedProxyPolicy.parse("10.0.0.1"));
        try {
            assertEquals("198.51.100.7",
                    ClientIpResolver.resolve("10.0.0.1", "spoofed, 198.51.100.7, 10.0.0.1", null));
        } finally {
            ClientIpResolver.setPolicy(TrustedProxyPolicy.empty());
        }
    }

    @Test
    @DisplayName("XFF 无可采信跳时退到 X-Real-IP，再退到直连对端")
    void fallbacksWhenNoUsableHop() {
        ClientIpResolver.setPolicy(TrustedProxyPolicy.parse("10.0.0.1"));
        try {
            assertEquals("198.51.100.9", ClientIpResolver.resolve("10.0.0.1", null, "198.51.100.9"));
            assertEquals("10.0.0.1", ClientIpResolver.resolve("10.0.0.1", null, null),
                    "无任何转发头时以可信对端自身为兜底");
            assertEquals("10.0.0.1", ClientIpResolver.resolve("10.0.0.1", "unknown", "unknown"));
        } finally {
            ClientIpResolver.setPolicy(TrustedProxyPolicy.empty());
        }
    }

    @Test
    @DisplayName("取不到直连对端时返回 unknown，而不是采信转发头")
    void missingPeerDoesNotTrustHeaders() {
        ClientIpResolver.setPolicy(TrustedProxyPolicy.parse("10.0.0.1"));
        try {
            assertEquals("unknown", ClientIpResolver.resolve(null, "198.51.100.7", null),
                    "没有对端地址就没有信任基础，采信 XFF 等于无条件信任");
        } finally {
            ClientIpResolver.setPolicy(TrustedProxyPolicy.empty());
        }
    }
}
