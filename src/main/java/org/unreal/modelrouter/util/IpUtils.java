package org.unreal.modelrouter.util;

import org.springframework.http.server.reactive.ServerHttpRequest;

public final class IpUtils {

    private IpUtils() {
        // 工具类不应实例化
    }

    private static final String[] IP_HEADER_CANDIDATES = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
    };

    /**
     * 从 ServerHttpRequest 获取客户端真实IP地址
     */
    public static String getClientIp(final ServerHttpRequest request) {
        if (request == null) {
            return "unknown";
        }

        // 检查各种代理头
        for (String header : IP_HEADER_CANDIDATES) {
            String ip = request.getHeaders().getFirst(header);
            if (isValidIp(ip)) {
                // X-Forwarded-For 可能包含多个IP，取第一个
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                if (isValidIp(ip)) {
                    return ip;
                }
            }
        }

        // 如果都没有，使用远程地址
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * 验证IP地址是否有效
     */
    private static boolean isValidIp(final String ip) {
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
}