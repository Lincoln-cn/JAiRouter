package org.unreal.modelrouter.monitor.callhistory.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.util.SecretKeyGenerator;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 调用记录内容加密/解密组件
 * 使用 AES-256-GCM 算法对请求/响应体内容进行加密存储
 *
 * <p>密钥来源：
 * <ul>
 *   <li>encryptionKeySource=env 时：读取环境变量 JAIR_CALL_HISTORY_KEY（Base64 编码的 256-bit 密钥）</li>
 *   <li>encryptionKeySource=auto 时：自动生成密钥并持久化到文件</li>
 * </ul>
 *
 * <p>密文格式：Base64(IV[12] || ciphertext || authTag[16])
 *
 * @author JAiRouter Team
 * @since 2.9.2
 */
@Slf4j
@Component
public class RecordContentCipher {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32; // 256-bit

    private final SecretKey secretKey;

    /**
     * 自动模式构造：生成或加载密钥，持久化到 dataDir
     */
    public RecordContentCipher() {
        this.secretKey = loadOrCreateKey("auto", null);
    }

    /**
     * 通过加密源配置构造
     *
     * @param encryptionKeySource 密钥来源："env" 或 "auto"
     * @param dataDir             auto 模式下密钥文件存储目录（null 则使用默认）
     */
    public RecordContentCipher(String encryptionKeySource, Path dataDir) {
        this.secretKey = loadOrCreateKey(encryptionKeySource, dataDir);
    }

    /**
     * 通过已有密钥构造（测试用）
     *
     * @param secretKey AES-256 密钥
     */
    public RecordContentCipher(SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * 加密明文内容
     *
     * @param plaintext 明文字符串
     * @return Base64(IV || ciphertext || authTag)，null/空输入返回 null
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            byte[] encryptedData = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 组合 IV || ciphertext || authTag
            byte[] result = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encryptedData, 0, result, iv.length, encryptedData.length);

            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            log.error("加密记录内容失败", e);
            throw new RuntimeException("加密记录内容失败", e);
        }
    }

    /**
     * 解密密文内容
     *
     * @param ciphertext Base64(IV || ciphertext || authTag) 格式的密文
     * @return 解密后的明文，null/空输入返回 null
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return null;
        }
        try {
            byte[] data = Base64.getDecoder().decode(ciphertext);

            // 提取 IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, GCM_IV_LENGTH);

            // 提取密文（含 authTag）
            byte[] encrypted = new byte[data.length - GCM_IV_LENGTH];
            System.arraycopy(data, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            byte[] decryptedData = cipher.doFinal(encrypted);
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("解密记录内容失败", e);
            throw new RuntimeException("解密记录内容失败", e);
        }
    }

    /**
     * 获取密钥来源策略下加载或创建密钥
     */
    private SecretKey loadOrCreateKey(String source, Path dataDir) {
        if ("env".equalsIgnoreCase(source)) {
            String envKey = System.getenv("JAIR_CALL_HISTORY_KEY");
            if (envKey != null && !envKey.isEmpty()) {
                byte[] keyBytes = Base64.getDecoder().decode(envKey);
                if (keyBytes.length != KEY_LENGTH_BYTES) {
                    throw new IllegalArgumentException(
                            "环境变量 JAIR_CALL_HISTORY_KEY 解码后长度应为 32 字节，实际: " + keyBytes.length);
                }
                log.info("已从环境变量加载调用记录加密密钥");
                return new SecretKeySpec(keyBytes, "AES");
            }
            // env 模式但环境变量未设置，回退到 auto
            log.warn("环境变量 JAIR_CALL_HISTORY_KEY 未设置，回退到自动生成密钥");
        }

        // auto 模式：尝试从文件加载，不存在则生成并持久化
        Path keyFile = resolveKeyFile(dataDir);
        if (Files.exists(keyFile)) {
            try {
                String base64Key = Files.readString(keyFile).trim();
                byte[] keyBytes = Base64.getDecoder().decode(base64Key);
                if (keyBytes.length == KEY_LENGTH_BYTES) {
                    log.info("已从文件加载调用记录加密密钥: {}", keyFile);
                    return new SecretKeySpec(keyBytes, "AES");
                }
                log.warn("密钥文件内容无效，将重新生成: {}", keyFile);
            } catch (IOException e) {
                log.warn("读取密钥文件失败，将重新生成: {}", keyFile, e);
            }
        }

        // 生成新密钥并持久化
        String base64Key = SecretKeyGenerator.generateBase64Key(KEY_LENGTH_BYTES);
        try {
            Files.createDirectories(keyFile.getParent());
            Files.writeString(keyFile, base64Key);
            log.info("已生成并持久化调用记录加密密钥: {}", keyFile);
        } catch (IOException e) {
            log.error("持久化加密密钥失败: {}", keyFile, e);
        }
        return new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
    }

    /**
     * 解析密钥文件路径
     */
    private Path resolveKeyFile(Path dataDir) {
        if (dataDir != null) {
            return dataDir.resolve("call-history.key");
        }
        String userHome = System.getProperty("user.home");
        return Path.of(userHome, ".jairouter", "call-history.key");
    }
}
