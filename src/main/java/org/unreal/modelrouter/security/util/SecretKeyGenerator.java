package org.unreal.modelrouter.security.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密钥生成工具类
 * 用于生成安全的随机密钥，包括 JWT 密钥、API Key 等
 */
public class SecretKeyGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    /**
     * 默认密钥长度（字节）
     * 256 位 = 32 字节，适用于 HS256 算法
     */
    private static final int DEFAULT_KEY_LENGTH = 32;
    
    /**
     * 最小密钥长度（字节）
     * 256 位 = 32 字节
     */
    private static final int MIN_KEY_LENGTH = 32;
    
    /**
     * 推荐密钥长度（字节）
     * 512 位 = 64 字节
     */
    private static final int RECOMMENDED_KEY_LENGTH = 64;

    /**
     * 字符集用于生成可读性较好的密钥
     */
    private static final String ALPHANUMERIC_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * 生成默认的 Base64 编码密钥
     * 适用于 JWT HS256 算法
     * 
     * @return Base64 编码的密钥字符串
     */
    public static String generateBase64Key() {
        return generateBase64Key(DEFAULT_KEY_LENGTH);
    }

    /**
     * 生成指定长度的 Base64 编码密钥
     * 
     * @param length 密钥字节长度
     * @return Base64 编码的密钥字符串
     */
    public static String generateBase64Key(int length) {
        if (length < MIN_KEY_LENGTH) {
            throw new IllegalArgumentException("密钥长度不能小于 " + MIN_KEY_LENGTH + " 字节");
        }
        
        byte[] keyBytes = new byte[length];
        SECURE_RANDOM.nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    /**
     * 生成十六进制编码密钥
     * 
     * @param length 密钥字节长度
     * @return 十六进制编码的密钥字符串
     */
    public static String generateHexKey(int length) {
        if (length < MIN_KEY_LENGTH) {
            throw new IllegalArgumentException("密钥长度不能小于 " + MIN_KEY_LENGTH + " 字节");
        }
        
        byte[] keyBytes = new byte[length];
        SECURE_RANDOM.nextBytes(keyBytes);
        return bytesToHex(keyBytes);
    }

    /**
     * 生成可读性较好的字母数字密钥
     * 适用于 API Key 等需要手动输入的场景
     * 
     * @param length 密钥长度
     * @return 字母数字密钥字符串
     */
    public static String generateAlphanumericKey(int length) {
        if (length < 16) {
            throw new IllegalArgumentException("密钥长度不能小于 16 个字符");
        }
        
        StringBuilder key = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            key.append(ALPHANUMERIC_CHARS.charAt(SECURE_RANDOM.nextInt(ALPHANUMERIC_CHARS.length())));
        }
        return key.toString();
    }

    /**
     * 生成带分隔符的可读密钥
     * 格式示例：gpustack_a1b2c3d4e5f6_g7h8i9j0k1l2m3n4o5p6
     * 
     * @param prefix 前缀标识
     * @param segmentLength 每个分段的长度
     * @param segmentCount 分段数量
     * @return 带分隔符的可读密钥
     */
    public static String generateFormattedKey(String prefix, int segmentLength, int segmentCount) {
        StringBuilder key = new StringBuilder();
        
        if (prefix != null && !prefix.isEmpty()) {
            key.append(prefix).append("_");
        }
        
        for (int i = 0; i < segmentCount; i++) {
            if (i > 0) {
                key.append("_");
            }
            key.append(generateAlphanumericKey(segmentLength));
        }
        
        return key.toString();
    }

    /**
     * 生成默认格式的 API Token
     * 格式：gpustack_<16 位随机>_<32 位随机>
     * 
     * @return API Token
     */
    public static String generateApiToken() {
        return generateFormattedKey("gpustack", 16, 2);
    }

    /**
     * 生成默认格式的 API Token（自定义前缀）
     * 
     * @param prefix 前缀标识
     * @return API Token
     */
    public static String generateApiToken(String prefix) {
        return generateFormattedKey(prefix, 16, 2);
    }

    /**
     * 验证密钥长度是否满足最低要求
     * 
     * @param key 密钥字符串
     * @param minLength 最小长度要求
     * @return 是否满足要求
     */
    public static boolean validateKeyLength(String key, int minLength) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        return key.length() >= minLength;
    }

    /**
     * 验证 Base64 密钥是否有效
     * 
     * @param base64Key Base64 编码的密钥
     * @return 是否有效
     */
    public static boolean isValidBase64Key(String base64Key) {
        if (base64Key == null || base64Key.isEmpty()) {
            return false;
        }
        
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Key);
            return decoded.length >= MIN_KEY_LENGTH;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /**
     * 获取最小密钥长度要求（字符）
     * 
     * @return 最小密钥长度
     */
    public static int getMinKeyLength() {
        return MIN_KEY_LENGTH;
    }

    /**
     * 获取推荐密钥长度（字符）
     * 
     * @return 推荐密钥长度
     */
    public static int getRecommendedKeyLength() {
        return RECOMMENDED_KEY_LENGTH;
    }
}
