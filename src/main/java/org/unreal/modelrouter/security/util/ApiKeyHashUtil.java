package org.unreal.modelrouter.security.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API Key 安全哈希工具类
 * 用于安全存储和验证 API Key
 */
@Slf4j
public final class ApiKeyHashUtil {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private ApiKeyHashUtil() {
        // 工具类，禁止实例化
    }

    /**
     * 对 API Key 进行哈希处理（带盐值）
     * 格式：Base64(salt) + ":" + Base64(hash)
     *
     * @param apiKey 原始 API Key 值
     * @return 哈希后的字符串
     */
    public static String hashApiKey(final String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }

        try {
            // 生成随机盐值
            byte[] salt = new byte[SALT_LENGTH];
            SECURE_RANDOM.nextBytes(salt);

            // 创建消息摘要实例
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);

            // 将盐值和 API Key 组合进行哈希
            digest.update(salt);
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));

            // 返回 Base64 编码的盐值和哈希值
            return Base64.getEncoder().encodeToString(salt) + ":" +
                   Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 算法不可用", e);
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 验证 API Key 是否匹配存储的哈希值
     *
     * @param apiKey      用户提供的原始 API Key
     * @param storedHash  存储的哈希值（格式：salt:hash）
     * @return 是否匹配
     */
    public static boolean verifyApiKey(final String apiKey, final String storedHash) {
        if (apiKey == null || apiKey.isEmpty() || storedHash == null || storedHash.isEmpty()) {
            return false;
        }

        try {
            // 解析存储的哈希值
            String[] parts = storedHash.split(":");
            if (parts.length != 2) {
                log.warn("无效的存储哈希格式");
                return false;
            }

            // 解码盐值和哈希值
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] storedHashBytes = Base64.getDecoder().decode(parts[1]);

            // 使用相同的盐值计算新哈希
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            digest.update(salt);
            byte[] computedHash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));

            // 比较哈希值（使用恒定时间比较防止时序攻击）
            return constantTimeEquals(storedHashBytes, computedHash);
        } catch (Exception e) {
            log.error("验证 API Key 时发生错误", e);
            return false;
        }
    }

    /**
     * 恒定时间比较（防止时序攻击）
     *
     * @param a 数组 a
     * @param b 数组 b
     * @return 是否相等
     */
    private static boolean constantTimeEquals(final byte[] a, final byte[] b) {
        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /**
     * 检查字符串是否是哈希格式
     *
     * @param value 待检查的字符串
     * @return 是否是哈希格式
     */
    public static boolean isHashedFormat(final String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        // 哈希格式应为：Base64(salt):Base64(hash)
        String[] parts = value.split(":");
        if (parts.length != 2) {
            return false;
        }
        try {
            Base64.getDecoder().decode(parts[0]);
            Base64.getDecoder().decode(parts[1]);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从哈希值中提取盐值
     *
     * @param storedHash 存储的哈希值
     * @return 盐值的 Base64 编码
     */
    public static String extractSalt(final String storedHash) {
        if (storedHash == null || !storedHash.contains(":")) {
            return null;
        }
        return storedHash.split(":")[0];
    }
}