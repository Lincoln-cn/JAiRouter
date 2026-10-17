package org.unreal.modelrouter.auth.security.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.common.util.ClientIpResolver;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

/**
 * 客户端IP地址获取工具类
 * 支持从各种代理头中获取真实的客户端IP地址
 */
@Slf4j
public class ClientIpUtils {
    /** Private constructor to prevent instantiation. */
    private ClientIpUtils() {}
    
    // 代理头列表，按优先级排序
    private static final List<String> PROXY_HEADERS = Arrays.asList(
        "X-Forwarded-For",
        "X-Real-IP", 
        "X-Original-Forwarded-For",
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
    );

    /**
     * 从ServerWebExchange中获取客户端真实IP地址
     * 
     * @param exchange ServerWebExchange对象
     * @return 客户端IP地址，如果无法获取则返回"unknown"
     */
    public static String getClientIpAddress(final ServerWebExchange exchange) {
        if (exchange == null || exchange.getRequest() == null) {
            log.warn("ServerWebExchange或Request为null，无法获取客户端IP");
            return "unknown";
        }
        
        // #151：转发头可被任意客户端伪造，信任决策统一交给共享 resolver。
        // 原实现无条件遍历 13 个候选头并优先取「首个公网 IP」，等于把伪造面直接暴露给客户端。
        return ClientIpResolver.resolve(exchange);
    }
    
    /**
     * 获取客户端IP地址的详细信息（用于调试）
     */
    public static String getClientIpDetails(final ServerWebExchange exchange) {
        if (exchange == null || exchange.getRequest() == null) {
            return "ServerWebExchange或Request为null";
        }
        
        StringBuilder details = new StringBuilder();
        details.append("IP获取详情:\n");
        
        // 检查所有代理头
        for (String header : PROXY_HEADERS) {
            String value = exchange.getRequest().getHeaders().getFirst(header);
            if (value != null && !value.trim().isEmpty()) {
                details.append(String.format("  %s: %s\n", header, value));
            }
        }
        
        // 检查远程地址
        try {
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null) {
                details.append(String.format("  RemoteAddress: %s\n", remoteAddress.toString()));
                if (remoteAddress.getAddress() != null) {
                    details.append(String.format("  RemoteAddress.HostAddress: %s\n", 
                        remoteAddress.getAddress().getHostAddress()));
                }
            } else {
                details.append("  RemoteAddress: null\n");
            }
        } catch (Exception e) {
            details.append(String.format("  RemoteAddress异常: %s\n", e.getMessage()));
        }
        
        // 最终获取的IP
        String finalIp = getClientIpAddress(exchange);
        details.append(String.format("  最终IP: %s", finalIp));
        
        return details.toString();
    }
}