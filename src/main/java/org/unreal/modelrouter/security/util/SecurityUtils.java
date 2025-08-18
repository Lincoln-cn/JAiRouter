package org.unreal.modelrouter.security.util;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

/**
 * 安全工具类
 * 提供安全相关的通用工具方法
 */
public class SecurityUtils {
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    /**
     * 生成唯一ID
     * @return 唯一ID
     */
    public static String generateId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * 生成安全的随机字符串
     * @param length 长度
     * @return 随机字符串
     */
    public static String generateSecureRandomString(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * 计算字符串的SHA-256哈希值
     * @param input 输入字符串
     * @return 哈希值
     */
    public static String sha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256算法不可用", e);
        }
    }
    
    /**
     * 从ServerWebExchange中提取客户端IP地址
     * @param exchange ServerWebExchange
     * @return 客户端IP地址
     */
    public static String extractClientIp(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 检查X-Forwarded-For头
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        // 检查X-Real-IP头
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // 使用远程地址
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }
    
    /**
     * 从ServerWebExchange中提取User-Agent
     * @param exchange ServerWebExchange
     * @return User-Agent字符串
     */
    public static String extractUserAgent(ServerWebExchange exchange) {
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        return userAgent != null ? userAgent : "unknown";
    }
    
    /**
     * 从ServerWebExchange中提取请求ID
     * @param exchange ServerWebExchange
     * @return 请求ID
     */
    public static String extractRequestId(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-ID");
        if (requestId == null || requestId.isEmpty()) {
            requestId = generateId();
            exchange.getResponse().getHeaders().add("X-Request-ID", requestId);
        }
        return requestId;
    }
    
    /**
     * 掩码处理敏感信息
     * @param input 输入字符串
     * @param maskChar 掩码字符
     * @param visibleChars 可见字符数（前后各保留的字符数）
     * @return 掩码后的字符串
     */
    public static String maskSensitiveInfo(String input, char maskChar, int visibleChars) {
        if (input == null || input.length() <= visibleChars * 2) {
            return input;
        }
        
        StringBuilder masked = new StringBuilder();
        masked.append(input, 0, visibleChars);
        
        for (int i = visibleChars; i < input.length() - visibleChars; i++) {
            masked.append(maskChar);
        }
        
        masked.append(input.substring(input.length() - visibleChars));
        return masked.toString();
    }
    
    /**
     * 获取今天的日期字符串（用于统计）
     * @return 日期字符串 (yyyy-MM-dd)
     */
    public static String getTodayDateString() {
        return LocalDateTime.now().format(DATE_FORMATTER);
    }
    
    /**
     * 验证字符串是否为有效的UUID
     * @param uuid UUID字符串
     * @return 是否有效
     */
    public static boolean isValidUuid(String uuid) {
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * 安全地比较两个字符串（防止时序攻击）
     * @param a 字符串a
     * @param b 字符串b
     * @return 是否相等
     */
    public static boolean secureEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        
        if (a.length() != b.length()) {
            return false;
        }
        
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        
        return result == 0;
    }
}