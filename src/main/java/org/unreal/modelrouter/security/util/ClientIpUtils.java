package org.unreal.modelrouter.security.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

/**
 * 客户端IP地址获取工具类
 * 支持从各种代理头中获取真实的客户端IP地址
 */
@Slf4j
public class ClientIpUtils {
    
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
    
    // 私有IP地址范围
    private static final List<String> PRIVATE_IP_PREFIXES = Arrays.asList(
        "10.",
        "172.16.", "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.", "172.23.",
        "172.24.", "172.25.", "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.",
        "192.168.",
        "127.",
        "169.254.", // 链路本地地址
        "::1", // IPv6 loopback
        "fc00:", "fd00:" // IPv6 私有地址
    );
    
    /**
     * 从ServerWebExchange中获取客户端真实IP地址
     * 
     * @param exchange ServerWebExchange对象
     * @return 客户端IP地址，如果无法获取则返回"unknown"
     */
    public static String getClientIpAddress(ServerWebExchange exchange) {
        if (exchange == null || exchange.getRequest() == null) {
            log.warn("ServerWebExchange或Request为null，无法获取客户端IP");
            return "unknown";
        }
        
        // 1. 尝试从代理头中获取IP
        String clientIp = getIpFromProxyHeaders(exchange);
        if (isValidIp(clientIp)) {
            log.debug("从代理头获取到客户端IP: {}", clientIp);
            return clientIp;
        }
        
        // 2. 从远程地址获取IP
        clientIp = getIpFromRemoteAddress(exchange);
        if (isValidIp(clientIp)) {
            log.debug("从远程地址获取到客户端IP: {}", clientIp);
            return clientIp;
        }
        
        log.warn("无法获取有效的客户端IP地址");
        return "unknown";
    }
    
    /**
     * 从代理头中获取IP地址
     */
    private static String getIpFromProxyHeaders(ServerWebExchange exchange) {
        for (String header : PROXY_HEADERS) {
            String headerValue = exchange.getRequest().getHeaders().getFirst(header);
            if (headerValue != null && !headerValue.trim().isEmpty() && !"unknown".equalsIgnoreCase(headerValue.trim())) {
                // 处理多个IP的情况（如X-Forwarded-For: client, proxy1, proxy2）
                String[] ips = headerValue.split(",");
                for (String ip : ips) {
                    String trimmedIp = ip.trim();
                    if (isValidIp(trimmedIp) && !isPrivateIp(trimmedIp)) {
                        log.debug("从头 {} 获取到公网IP: {}", header, trimmedIp);
                        return trimmedIp;
                    }
                }
                
                // 如果没有公网IP，返回第一个有效的私有IP
                for (String ip : ips) {
                    String trimmedIp = ip.trim();
                    if (isValidIp(trimmedIp)) {
                        log.debug("从头 {} 获取到私有IP: {}", header, trimmedIp);
                        return trimmedIp;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 从远程地址获取IP
     */
    private static String getIpFromRemoteAddress(ServerWebExchange exchange) {
        try {
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                String ip = remoteAddress.getAddress().getHostAddress();
                
                // 处理IPv6地址中的scope ID
                if (ip.contains("%")) {
                    ip = ip.substring(0, ip.indexOf("%"));
                }
                
                return ip;
            }
        } catch (Exception e) {
            log.warn("从远程地址获取IP时发生异常: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * 验证IP地址是否有效
     */
    private static boolean isValidIp(String ip) {
        if (ip == null || ip.trim().isEmpty() || "unknown".equalsIgnoreCase(ip.trim())) {
            return false;
        }
        
        String trimmedIp = ip.trim();
        
        // 基本格式检查
        if (trimmedIp.equals("0.0.0.0") || trimmedIp.equals("::") || trimmedIp.equals("::0")) {
            return false;
        }
        
        try {
            // 使用InetAddress验证IP格式
            InetAddress.getByName(trimmedIp);
            return true;
        } catch (Exception e) {
            log.debug("IP地址格式无效: {}", trimmedIp);
            return false;
        }
    }
    
    /**
     * 判断是否为私有IP地址
     */
    private static boolean isPrivateIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return true;
        }
        
        String trimmedIp = ip.trim().toLowerCase();
        
        return PRIVATE_IP_PREFIXES.stream().anyMatch(trimmedIp::startsWith);
    }
    
    /**
     * 获取客户端IP地址的详细信息（用于调试）
     */
    public static String getClientIpDetails(ServerWebExchange exchange) {
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