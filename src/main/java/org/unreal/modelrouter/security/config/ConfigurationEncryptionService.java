package org.unreal.modelrouter.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 配置加密服务
 * 提供敏感配置信息的加密和解密功能
 */
@Slf4j
@Service
public class ConfigurationEncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int KEY_LENGTH = 256;

    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom;

    public ConfigurationEncryptionService(@Value("${jairouter.security.encryption.key:}") String encryptionKeyString) {
        this.secureRandom = new SecureRandom();
        
        if (encryptionKeyString != null && !encryptionKeyString.trim().isEmpty()) {
            // 使用提供的密钥
            this.encryptionKey = new SecretKeySpec(
                    Base64.getDecoder().decode(encryptionKeyString), ALGORITHM);
            log.info("使用配置的加密密钥");
        } else {
            // 生成新的密钥
            this.encryptionKey = generateKey();
            log.warn("未配置加密密钥，使用临时生成的密钥。重启后将无法解密现有数据！");
            log.info("生成的加密密钥（请保存到配置中）: {}", 
                    Base64.getEncoder().encodeToString(encryptionKey.getEncoded()));
        }
    }

    /**
     * 加密敏感数据
     * @param plainText 明文
     * @return 加密后的数据（Base64编码）
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            // 生成随机IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // 初始化加密器
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);

            // 加密数据
            byte[] encryptedData = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 将IV和加密数据合并
            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, 0, encryptedWithIv, GCM_IV_LENGTH, encryptedData.length);

            // 返回Base64编码的结果
            return Base64.getEncoder().encodeToString(encryptedWithIv);

        } catch (Exception e) {
            log.error("加密数据失败", e);
            throw new ConfigurationEncryptionException("加密数据失败", e);
        }
    }

    /**
     * 解密敏感数据
     * @param encryptedText 加密的数据（Base64编码）
     * @return 解密后的明文
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            // 解码Base64
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedText);

            // 提取IV和加密数据
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encryptedData = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedWithIv, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);

            // 初始化解密器
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, parameterSpec);

            // 解密数据
            byte[] decryptedData = cipher.doFinal(encryptedData);

            return new String(decryptedData, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("解密数据失败", e);
            throw new ConfigurationEncryptionException("解密数据失败", e);
        }
    }

    /**
     * 加密JWT密钥
     * @param jwtSecret JWT密钥
     * @return 加密后的JWT密钥
     */
    public String encryptJwtSecret(String jwtSecret) {
        log.debug("加密JWT密钥");
        return encrypt(jwtSecret);
    }

    /**
     * 解密JWT密钥
     * @param encryptedJwtSecret 加密的JWT密钥
     * @return 解密后的JWT密钥
     */
    public String decryptJwtSecret(String encryptedJwtSecret) {
        log.debug("解密JWT密钥");
        return decrypt(encryptedJwtSecret);
    }

    /**
     * 加密API Key值
     * @param apiKeyValue API Key值
     * @return 加密后的API Key值
     */
    public String encryptApiKeyValue(String apiKeyValue) {
        log.debug("加密API Key值");
        return encrypt(apiKeyValue);
    }

    /**
     * 解密API Key值
     * @param encryptedApiKeyValue 加密的API Key值
     * @return 解密后的API Key值
     */
    public String decryptApiKeyValue(String encryptedApiKeyValue) {
        log.debug("解密API Key值");
        return decrypt(encryptedApiKeyValue);
    }

    /**
     * 检查数据是否已加密
     * @param data 数据
     * @return 是否已加密
     */
    public boolean isEncrypted(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }

        try {
            // 尝试Base64解码
            byte[] decoded = Base64.getDecoder().decode(data);
            // 检查长度是否合理（至少包含IV和一些加密数据）
            return decoded.length > GCM_IV_LENGTH + GCM_TAG_LENGTH;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 生成新的加密密钥
     * @return 加密密钥
     */
    private SecretKey generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_LENGTH);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            log.error("生成加密密钥失败", e);
            throw new ConfigurationEncryptionException("生成加密密钥失败", e);
        }
    }

    /**
     * 获取当前加密密钥的Base64编码
     * @return 加密密钥的Base64编码
     */
    public String getEncryptionKeyBase64() {
        return Base64.getEncoder().encodeToString(encryptionKey.getEncoded());
    }

    /**
     * 配置加密异常
     */
    public static class ConfigurationEncryptionException extends RuntimeException {
        public ConfigurationEncryptionException(String message) {
            super(message);
        }

        public ConfigurationEncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}