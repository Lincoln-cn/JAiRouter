package org.unreal.modelrouter.common.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * 出站 URL 安全校验：限制协议、拒绝危险主机/云元数据地址。
 * 适配器测试需要访问内网 AI 服务，因此不默认封禁私网/回环地址；
 * 拦截云元数据与链路本地等明确危险目标。
 */
public final class UrlSecurityValidator {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "metadata.google.internal",
            "metadata",
            "instance-data",
            "169.254.169.254"
    );

    private UrlSecurityValidator() {
    }

    /**
     * 校验并规范化出站 base URL。
     *
     * @throws SecurityException URL 非法或命中危险目标
     */
    public static String validateOutboundBaseUrl(final String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new SecurityException("Base URL is required");
        }

        final URI uri;
        try {
            uri = new URI(baseUrl.trim());
        } catch (URISyntaxException e) {
            throw new SecurityException("Invalid base URL syntax");
        }

        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new SecurityException("Only http/https base URLs are allowed");
        }
        if (uri.getUserInfo() != null && !uri.getUserInfo().isBlank()) {
            throw new SecurityException("Base URL must not contain user credentials");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SecurityException("Base URL must contain a host");
        }

        if (isBlockedHostLiteral(host) || isDangerousIpLiteral(host)) {
            throw new SecurityException("Base URL host is not allowed");
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isDangerousAddress(address)) {
                    throw new SecurityException("Base URL resolves to a blocked address");
                }
            }
        } catch (UnknownHostException e) {
            // 解析失败留给连接阶段处理，保持现有超时/错误语义
        }

        return normalize(scheme, host, uri.getPort(), uri.getRawPath());
    }

    private static String normalize(final String scheme, final String host,
                                    final int port, final String rawPath) {
        StringBuilder normalized = new StringBuilder();
        normalized.append(scheme).append("://");
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
            normalized.append('[').append(host).append(']');
        } else {
            normalized.append(host);
        }
        if (port > 0) {
            normalized.append(':').append(port);
        }
        if (rawPath != null && !rawPath.isBlank() && !"/".equals(rawPath)) {
            if (!rawPath.startsWith("/")) {
                normalized.append('/');
            }
            normalized.append(rawPath);
        }
        String result = normalized.toString();
        return result.endsWith("/") ? result.substring(0, result.length() - 1) : result;
    }

    private static boolean isBlockedHostLiteral(final String host) {
        String value = unwrapIpv6(host).toLowerCase(Locale.ROOT);
        return BLOCKED_HOSTS.contains(value) || isDangerousIpLiteral(value);
    }

    private static boolean isDangerousIpLiteral(final String host) {
        String value = unwrapIpv6(host).toLowerCase(Locale.ROOT);
        return "169.254.169.254".equals(value)
                || "fd00:ec2::254".equals(value)
                || "0.0.0.0".equals(value)
                || "::".equals(value);
    }

    private static String unwrapIpv6(final String host) {
        String value = host.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean isDangerousAddress(final InetAddress address) {
        if (address.isLinkLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4 && (bytes[0] & 0xFF) == 169 && (bytes[1] & 0xFF) == 254) {
            return true;
        }
        // 云元数据 IPv6 fd00:ec2::/64
        return bytes.length == 16
                && (bytes[0] & 0xFF) == 0xfd
                && (bytes[1] & 0xFF) == 0x00
                && (bytes[2] & 0xFF) == 0x0e
                && (bytes[3] & 0xFF) == 0xc2;
    }
}
