package org.unreal.modelrouter.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * JWT令牌哈希工具类
 * 提供令牌的SHA-256哈希计算功能
 */
@Slf4j
public class TokenHashUtils {
    
    private static final String HASH_ALGORITHM = "SHA-256";
    
    /**
     * 计算令牌的SHA-256哈希值
     * @param token JWT令牌字符串
     * @return 哈希值的十六进制字符串
     */
    public static String hashToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            
            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }
            
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            throw new RuntimeException("Failed to hash token", e);
        }
    }
    
    /**
     * 验证令牌哈希值
     * @param token 原始令牌
     * @param expectedHash 期望的哈希值
     * @return 是否匹配
     */
    public static boolean verifyTokenHash(String token, String expectedHash) {
        if (token == null || expectedHash == null) {
            return false;
        }
        
        try {
            String actualHash = hashToken(token);
            return actualHash.equals(expectedHash);
        } catch (Exception e) {
            log.warn("Failed to verify token hash", e);
            return false;
        }
    }
}