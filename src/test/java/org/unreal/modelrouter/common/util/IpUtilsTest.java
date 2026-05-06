package org.unreal.modelrouter.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IpUtils 单元测试 - v2.9.6
 * 
 * 注：getClientIp 需要 ServerHttpRequest，简化测试只测试 normalizeIp 方法
 */
@DisplayName("IpUtils v2.9.6 测试")
class IpUtilsTest {

    @Test
    @DisplayName("测试 1: normalizeIp - IPv6 localhost 转 IPv4")
    void testNormalizeIpIPv6Localhost() {
        String result = IpUtils.normalizeIp("0:0:0:0:0:0:0:1");
        assertEquals("127.0.0.1", result);
    }

    @Test
    @DisplayName("测试 2: normalizeIp - ::1 转 IPv4")
    void testNormalizeIpShortIPv6Localhost() {
        String result = IpUtils.normalizeIp("::1");
        assertEquals("127.0.0.1", result);
    }

    @Test
    @DisplayName("测试 3: normalizeIp - null 返回 unknown")
    void testNormalizeIpNull() {
        String result = IpUtils.normalizeIp(null);
        assertEquals("unknown", result);
    }

    @Test
    @DisplayName("测试 4: normalizeIp - 空字符串返回空")
    void testNormalizeIpEmpty() {
        String result = IpUtils.normalizeIp("");
        assertEquals("", result);
    }

    @Test
    @DisplayName("测试 5: normalizeIp - IPv4 地址保持不变")
    void testNormalizeIpIPv4() {
        String result = IpUtils.normalizeIp("192.168.1.100");
        assertEquals("192.168.1.100", result);
    }

    @Test
    @DisplayName("测试 6: normalizeIp - 带空格的IP去除空格")
    void testNormalizeIpWithSpaces() {
        String result = IpUtils.normalizeIp(" 192.168.1.100 ");
        assertEquals("192.168.1.100", result);
    }

    @Test
    @DisplayName("测试 7: normalizeIp - IPv6 地址保持不变")
    void testNormalizeIpIPv6() {
        String result = IpUtils.normalizeIp("2001:db8::1");
        assertEquals("2001:db8::1", result);
    }

    @Test
    @DisplayName("测试 8: normalizeIp - unknown 保持不变")
    void testNormalizeIpUnknown() {
        String result = IpUtils.normalizeIp("unknown");
        assertEquals("unknown", result);
    }

    @Test
    @DisplayName("测试 9: normalizeIp - localhost 返回 localhost")
    void testNormalizeIpLocalhost() {
        String result = IpUtils.normalizeIp("localhost");
        assertEquals("localhost", result);
    }

    @Test
    @DisplayName("测试 10: normalizeIp - 端口号保持不变")
    void testNormalizeIpWithPort() {
        String result = IpUtils.normalizeIp("192.168.1.100:8080");
        assertEquals("192.168.1.100:8080", result);
    }
}