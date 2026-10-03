package org.unreal.modelrouter.common.util;

import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetAddress;

/**
 * IP 工具类：规范化、比较与 CIDR 匹配（#151）。
 *
 * <p>客户端 IP 的<b>信任决策不在本类</b>，统一由 {@link ClientIpResolver} 承担。
 * {@link #getClientIp(ServerHttpRequest)} 已改为委托它——此前的实现会无条件遍历转发头并取
 * XFF 首跳，任何客户端伪造该头即可污染路由限流键、会话亲和分片与访问日志。</p>
 */
public final class IpUtils {

    private IpUtils() {
        // 工具类不应实例化
    }

    /**
     * 推导客户端 IP（按信任策略）。
     *
     * <p>保留此入口是为了让既有调用方零成本收敛；语义与 {@link ClientIpResolver#resolve} 完全一致：
     * 直连对端不在可信列表时忽略所有转发头。</p>
     *
     * @param request 请求对象，可为 null
     * @return 规范化后的客户端 IP；无法判定时返回 {@code "unknown"}
     */
    public static String getClientIp(final ServerHttpRequest request) {
        return ClientIpResolver.resolve(request);
    }

    /**
     * 验证 IP 地址是否有效
     */
    public static boolean isValidIp(final String ip) {
        return ip != null
                && !ip.trim().isEmpty()
                && !"unknown".equalsIgnoreCase(ip.trim())
                && !"0:0:0:0:0:0:0:1".equals(ip.trim());
    }

    /**
     * 标准化IP地址（将IPv6的localhost转换为IPv4）
     */
    public static String normalizeIp(final String ip) {
        if (ip == null) {
            return "unknown";
        }

        String normalizedIp = ip.trim();

        // IPv6 localhost 转换为 IPv4
        if ("0:0:0:0:0:0:0:1".equals(normalizedIp) || "::1".equals(normalizedIp)) {
            return "127.0.0.1";
        }

        return normalizedIp;
    }

    /**
     * 规范化用于比较的 IP 书写形式（#151 §3.5）。
     *
     * <p>统一处理：两侧空白 → 剥端口 → 去 IPv6 方括号 → IPv6 压缩为规范形式
     * （{@link InetAddress#getHostAddress()}，十六进制小写、零段压缩）→ {@link #normalizeIp}
     * 归一（{@code ::1} → {@code 127.0.0.1}）。</p>
     *
     * <p>剥端口规则（§3.5）：先判 {@code [IPv6]:port}，再判「恰有一个冒号且左侧是合法 IPv4」的
     * {@code IPv4:port}；<b>裸 IPv6 不剥</b>——否则 {@code 2001:db8::1} 会被误截。</p>
     *
     * @param raw 原始书写形式，可为 null
     * @return 规范化结果；无法识别时返回 {@code "unknown"}
     */
    public static String canonicalizeIp(final String raw) {
        if (raw == null) {
            return "unknown";
        }
        String value = stripPort(raw.trim());
        if (value.isEmpty()) {
            return "unknown";
        }
        if (value.indexOf(':') >= 0) {
            // 必然含 ':'，是 IPv6 字面量，InetAddress 不会做 DNS 解析
            try {
                value = InetAddress.getByName(value).getHostAddress();
            } catch (Exception e) {
                // 非法 IPv6 字面量：按原样交给 normalizeIp
            }
        }
        return normalizeIp(value);
    }

    /**
     * 按 §3.5 的规则剥除端口。
     *
     * @param raw 原始书写形式
     * @return 剥除端口后的地址
     */
    static String stripPort(final String raw) {
        if (raw == null) {
            return "";
        }
        final String value = raw.trim();
        if (value.isEmpty()) {
            return value;
        }
        if (value.startsWith("[")) {
            final int close = value.indexOf(']');
            return close > 0 ? value.substring(1, close) : value;
        }
        final int colon = value.lastIndexOf(':');
        if (colon > 0 && value.indexOf(':') == colon) {
            final String host = value.substring(0, colon);
            return isIpv4Literal(host) ? host : value;
        }
        return value;
    }

    private static boolean isIpv4Literal(final String candidate) {
        final String[] octets = candidate.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3) {
                return false;
            }
            for (int i = 0; i < octet.length(); i++) {
                if (!Character.isDigit(octet.charAt(i))) {
                    return false;
                }
            }
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    /**
     * IPv4 CIDR 匹配（#151 §3.3：由 {@code RuleEngineService.cidrMatches} 原样提升，行为不变）。
     *
     * <p>支持 {@code 192.168.1.0/24} 与精确 IP（无后缀视为 {@code /32}）；前缀越界返回 false。
     * <b>仅 IPv4</b>——IPv6 CIDR 不在 #151 Stage 3 范围（§3.4），含 {@code :} 的参数一律不匹配。</p>
     *
     * @param ip   待匹配的 IP（应已规范化）
     * @param cidr 网段，形如 {@code 10.0.0.0/8}
     * @return 是否落在网段内
     */
    public static boolean matchesCidr(final String ip, final String cidr) {
        if (ip == null || cidr == null || ip.indexOf(':') >= 0 || cidr.indexOf(':') >= 0) {
            return false;
        }
        try {
            String[] parts = cidr.split("/");
            String cidrIp = parts[0];
            int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;
            if (prefix < 0 || prefix > 32) {
                return false;
            }
            long ipLong = ipToLong(ip);
            long cidrLong = ipToLong(cidrIp);
            long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
            return (ipLong & mask) == (cidrLong & mask);
        } catch (Exception e) {
            return false;
        }
    }

    private static long ipToLong(final String ip) {
        String[] octets = ip.split("\\.");
        if (octets.length != 4) {
            throw new IllegalArgumentException("非法 IP: " + ip);
        }
        long result = 0;
        for (String octet : octets) {
            result = (result << 8) | (Long.parseLong(octet) & 0xFF);
        }
        return result;
    }
}
