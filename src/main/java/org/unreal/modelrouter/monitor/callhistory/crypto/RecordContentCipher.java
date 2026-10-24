package org.unreal.modelrouter.monitor.callhistory.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.util.SecretKeyGenerator;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 调用记录内容加密/解密组件
 * 使用 AES-256-GCM 算法对请求/响应体内容进行加密存储
 *
 * <p>密钥来源（对应配置 {@code jairouter.call-history.encryption-key-source}）：
 * <ul>
 *   <li>{@code env}：读取环境变量 {@code JAIR_CALL_HISTORY_KEY}（Base64 编码的 256-bit 密钥）；
 *       环境变量缺失或非法时回退到 {@code auto}，并在 {@link #keySourceEffective()} 中如实反映回退结果</li>
 *   <li>{@code auto}（默认）：从密钥文件加载，不存在则自动生成并持久化到 {@code ~/.jairouter/call-history.key}</li>
 * </ul>
 *
 * <p>密文格式：Base64(IV[12] || ciphertext || authTag[16])
 *
 * <p><b>装配说明</b>：本类的 Spring 装配走 {@link #RecordContentCipher(CallHistoryProperties)}，
 * 因此 {@code encryption-key-source} 会真实生效。其余构造器保留给单元测试与手工构造使用。
 * 3.5.1 之前本类只有无参构造器被 Spring 使用，导致 {@code encryption-key-source: env} 被静默忽略
 * （配置可设、接口可回显，但实现始终走 auto），暴露只读状态字段就是为了让这种漂移在控制台可见。
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

    /** 环境变量形式的密钥来源 */
    private static final String SOURCE_ENV = "env";
    /** 自动生成并持久化到文件的密钥来源 */
    private static final String SOURCE_AUTO = "auto";
    /** 由调用方直接传入密钥（测试/手工构造） */
    private static final String SOURCE_EXPLICIT = "explicit";

    private static final String ENV_KEY_NAME = "JAIR_CALL_HISTORY_KEY";

    private final SecretKey secretKey;
    private final String keySourceRequested;
    private final String keySourceEffective;
    private final boolean environmentKeyPresent;
    private final String keyFingerprint;

    /**
     * 自动模式构造：生成或加载密钥，持久化到默认目录（保持 3.5.0 之前的语义，供测试与手工构造使用）
     */
    public RecordContentCipher() {
        this(resolveKey(SOURCE_AUTO, null));
    }

    /**
     * 通过加密源配置构造
     *
     * @param encryptionKeySource 密钥来源："env" 或 "auto"
     * @param dataDir             auto 模式下密钥文件存储目录（null 则使用默认）
     */
    public RecordContentCipher(String encryptionKeySource, Path dataDir) {
        this(resolveKey(encryptionKeySource, dataDir));
    }

    /**
     * Spring 装配入口：按 {@code jairouter.call-history.encryption-key-source} 选择密钥来源。
     *
     * @param properties 调用记录配置（为 null 时按 auto 处理）
     */
    @Autowired
    public RecordContentCipher(CallHistoryProperties properties) {
        this(resolveKey(properties == null ? null : properties.getEncryptionKeySource(), null));
    }

    /**
     * 通过已有密钥构造（测试用）
     *
     * @param secretKey AES-256 密钥
     */
    public RecordContentCipher(SecretKey secretKey) {
        this(new KeyMaterial(secretKey, SOURCE_EXPLICIT, SOURCE_EXPLICIT, false));
    }

    private RecordContentCipher(KeyMaterial material) {
        this.secretKey = material.key;
        this.keySourceRequested = material.requested;
        this.keySourceEffective = material.effective;
        this.environmentKeyPresent = material.environmentKeyPresent;
        this.keyFingerprint = fingerprintOf(material.key);
    }

    /**
     * 配置里请求的密钥来源（原样回显，便于与生效值对照）
     *
     * @return "env" / "auto" / "explicit"
     */
    public String keySourceRequested() {
        return keySourceRequested;
    }

    /**
     * 实际生效的密钥来源。请求 {@code env} 但环境变量缺失时会回退为 {@code auto}，
     * 此时本方法返回 {@code auto} 而不是配置里的值。
     *
     * @return 实际生效的密钥来源
     */
    public String keySourceEffective() {
        return keySourceEffective;
    }

    /**
     * env 模式下环境变量 {@code JAIR_CALL_HISTORY_KEY} 是否存在且可解码为 32 字节
     *
     * @return 环境变量密钥是否可用
     */
    public boolean environmentKeyPresent() {
        return environmentKeyPresent;
    }

    /**
     * 密钥指纹：SHA-256 的前 4 字节十六进制（8 个字符），用于在控制台核对
     * 多副本是否使用同一把密钥，<b>不暴露密钥本身</b>。
     *
     * @return 8 位十六进制指纹
     */
    public String keyFingerprint() {
        return keyFingerprint;
    }

    /**
     * 加密算法描述（供只读展示）
     *
     * @return 固定返回 "AES-256-GCM"
     */
    public String algorithm() {
        return "AES-256-GCM";
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
     * 按密钥来源策略加载或创建密钥，并记录「请求来源 / 生效来源」用于只读展示。
     *
     * @param encryptionKeySource 配置里的密钥来源
     * @param dataDir             auto 模式下的密钥文件目录（null 用默认目录）
     * @return 密钥与来源元数据
     */
    private static KeyMaterial resolveKey(String encryptionKeySource, Path dataDir) {
        String requested = normalizeSource(encryptionKeySource);
        if (SOURCE_ENV.equals(requested)) {
            String envKey = System.getenv(ENV_KEY_NAME);
            if (envKey != null && !envKey.isEmpty()) {
                byte[] keyBytes = Base64.getDecoder().decode(envKey);
                if (keyBytes.length != KEY_LENGTH_BYTES) {
                    throw new IllegalArgumentException(
                            "环境变量 " + ENV_KEY_NAME + " 解码后长度应为 32 字节，实际: " + keyBytes.length);
                }
                log.info("已从环境变量加载调用记录加密密钥");
                return new KeyMaterial(new SecretKeySpec(keyBytes, "AES"), SOURCE_ENV, SOURCE_ENV, true);
            }
            // env 模式但环境变量未设置：回退到 auto，但保留 requested=env 以便控制台显示回退
            log.warn("环境变量 {} 未设置，回退到自动生成密钥", ENV_KEY_NAME);
            return loadOrCreateFileKey(dataDir, SOURCE_ENV, false);
        }
        return loadOrCreateFileKey(dataDir, requested, false);
    }

    /**
     * 从文件加载密钥，不存在则生成并持久化。
     *
     * @param dataDir              密钥文件目录（null 用默认目录）
     * @param requested            配置请求的来源
     * @param environmentKeyPresent env 密钥是否可用
     * @return 密钥与来源元数据（effective 固定为 auto）
     */
    private static KeyMaterial loadOrCreateFileKey(Path dataDir, String requested, boolean environmentKeyPresent) {
        Path keyFile = resolveKeyFile(dataDir);
        if (Files.exists(keyFile)) {
            try {
                String base64Key = Files.readString(keyFile).trim();
                byte[] keyBytes = Base64.getDecoder().decode(base64Key);
                if (keyBytes.length == KEY_LENGTH_BYTES) {
                    log.info("已从文件加载调用记录加密密钥: {}", keyFile);
                    return new KeyMaterial(new SecretKeySpec(keyBytes, "AES"),
                            requested, SOURCE_AUTO, environmentKeyPresent);
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
        return new KeyMaterial(new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES"),
                requested, SOURCE_AUTO, environmentKeyPresent);
    }

    /**
     * 归一化密钥来源配置值：空白或 null 按 auto 处理，大小写不敏感。
     *
     * @param encryptionKeySource 原始配置值
     * @return "env" 或 "auto"
     */
    private static String normalizeSource(String encryptionKeySource) {
        if (encryptionKeySource == null || encryptionKeySource.isBlank()) {
            return SOURCE_AUTO;
        }
        String normalized = encryptionKeySource.trim().toLowerCase(Locale.ROOT);
        return SOURCE_ENV.equals(normalized) ? SOURCE_ENV : SOURCE_AUTO;
    }

    /**
     * 解析密钥文件路径
     *
     * @param dataDir 指定目录（null 用 ~/.jairouter）
     * @return 密钥文件路径
     */
    private static Path resolveKeyFile(Path dataDir) {
        if (dataDir != null) {
            return dataDir.resolve("call-history.key");
        }
        String userHome = System.getProperty("user.home");
        return Path.of(userHome, ".jairouter", "call-history.key");
    }

    /**
     * 计算密钥指纹：SHA-256 前 4 字节的十六进制表示
     *
     * @param key 密钥
     * @return 8 位十六进制指纹，计算失败时返回 null
     */
    private static String fingerprintOf(SecretKey key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            log.warn("计算密钥指纹失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 密钥与其来源元数据
     */
    private static final class KeyMaterial {

        private final SecretKey key;
        private final String requested;
        private final String effective;
        private final boolean environmentKeyPresent;

        private KeyMaterial(SecretKey key, String requested, String effective, boolean environmentKeyPresent) {
            this.key = key;
            this.requested = requested;
            this.effective = effective;
            this.environmentKeyPresent = environmentKeyPresent;
        }
    }
}
