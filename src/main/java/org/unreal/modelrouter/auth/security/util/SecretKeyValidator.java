package org.unreal.modelrouter.auth.security.util;

/**
 * 密钥强度验证器
 * 用于验证 JWT 密钥、密码等敏感信息的安全性
 */
public class SecretKeyValidator {
    /** Private constructor to prevent instantiation. */
    private SecretKeyValidator() {}

    /**
     * 最小密钥长度（字节）- 对应 256 位
     */
    private static final int MIN_KEY_LENGTH_BYTES = 32;
    
    /**
     * 最小密码长度
     */
    private static final int MIN_PASSWORD_LENGTH = 12;
    
    /**
     * 推荐密码长度
     */
    private static final int RECOMMENDED_PASSWORD_LENGTH = 16;

    /**
     * 高熵串中弱模式“主导”判定：模式长度占比 ≥ 3/20（15%）。
     *
     * <p>依据（实测）：需捕获的样例中模式占比最低为 {@code test_key_123} 的 test（4/13 ≈ 31%）、
     * {@code ChangeMeOnFirstStartup123456} 的 123456（6/28 ≈ 21%）；而随机 Base64 密钥中
     * 偶然命中率最高的短模式 {@code key}/{@code dev} 在 64 字符串中仅占 4.7%、
     * 在 44 字符串（32 字节密钥）中占 6.8%，{@code test}/{@code demo} 为 9.1%，
     * 最长的 {@code secret}/{@code qwerty}/{@code 123456} 为 13.6%。15% 落在 13.6% 与 21% 之间。
     */
    private static final int DOMINANCE_NUMERATOR = 3;
    private static final int DOMINANCE_DENOMINATOR = 20;

    /**
     * 弱模式判定的香农熵阈值（bit/字符），用于区分「机器随机生成」与「人择」。
     *
     * <p>依据（实测，100 万次采样/长度）：随机 Base64 密钥 32/48/64 字节的**最低**观测熵为
     * 4.1826 / 4.5585 / 4.9152；而需要走低熵分支的弱样例**最高**熵为 3.3219
     * （{@code 12345678901234567890}，其本身的熵为 3.3219，其余更低）。
     * 取二者中点 3.75，两侧各留约 0.43 bit 余量。
     *
     * <p>注意：{@code ChangeMeOnFirstStartup123456} 的熵为 4.3518，**高于本阈值**，
     * 因此它不走低熵分支，而是由「模式占比 21% ≥ 15%」与下方的显式 {@code equals} 判定捕获。
     * 换言之本阈值不承重于任何既有弱样例，只用于保留「长人择弱密钥含低占比弱词」这一情形。
     */
    private static final double HIGH_ENTROPY_THRESHOLD = 3.75;

    /**
     * 密码场景的弱模式主导阈值：模式长度占密码长度的比例 ≥ 1/2。
     *
     * <p>与密钥场景的 {@link #DOMINANCE_NUMERATOR}/{@link #DOMINANCE_DENOMINATOR}（15%）刻意不同。
     * 15% 是为 32–64 字节密钥标定的——4 字符模式在 32 字节密钥中仅占 12.5%，低于阈值即视为偶然子串；
     * 但密码短得多，同样的模式在 20 字符密码中占 <b>20%</b>，<b>高于</b> 15%，直接复用会让随机密码
     * 含偶然 {@code test} 时仍被判弱（实测 100 万次误判率 1/41667，见 #171）。取 1/2 后，随机
     * 20 字符密码中 4–6 字符模式的占比 20%–30% 均低于阈值，不再命中。</p>
     *
     * <p>需捕获的弱样例中最低占比为 62.5%（{@code admin123} 的 {@code admin}）；
     * 唯一例外是 {@code ChangeMeOnFirstStartup123456}（{@code 123456} 占 21.4%），
     * 由 {@link #isCommonWeakPassword} 里的显式 equals 判定兜底。</p>
     */
    private static final int PASSWORD_DOMINANCE_NUMERATOR = 1;
    private static final int PASSWORD_DOMINANCE_DENOMINATOR = 2;

    /**
     * 密钥强度级别
     */
    public enum StrengthLevel {
        /**
         * 非常弱 - 存在严重安全风险
         */
        VERY_WEAK("非常弱", "存在严重安全风险，必须立即更换"),
        
        /**
         * 弱 - 存在安全风险
         */
        WEAK("弱", "存在安全风险，建议更换"),
        
        /**
         * 中等 - 基本安全
         */
        MEDIUM("中等", "基本安全，建议用于非生产环境"),
        
        /**
         * 强 - 安全
         */
        STRONG("强", "安全，可用于生产环境"),
        
        /**
         * 非常强 - 非常安全
         */
        VERY_STRONG("非常强", "非常安全");

        private final String displayName;
        private final String description;

        StrengthLevel(final String displayName, final String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final StrengthLevel strengthLevel;
        private final boolean passed;
        private final String message;

        public ValidationResult(final StrengthLevel strengthLevel, final boolean passed, final String message) {
            this.strengthLevel = strengthLevel;
            this.passed = passed;
            this.message = message;
        }

        public StrengthLevel getStrengthLevel() {
            return strengthLevel;
        }

        public boolean isPassed() {
            return passed;
        }

        public String getMessage() {
            return message;
        }

        public static ValidationResult success(final StrengthLevel level) {
            return new ValidationResult(level, true, "密钥强度：" + level.getDisplayName());
        }

        public static ValidationResult failure(final StrengthLevel level, final String reason) {
            return new ValidationResult(level, false, reason);
        }
    }

    /**
     * 验证 JWT 密钥强度
     * 
     * @param secret JWT 密钥
     * @return 验证结果
     */
    public static ValidationResult validateJwtSecret(final String secret) {
        if (secret == null || secret.isEmpty()) {
            return ValidationResult.failure(StrengthLevel.VERY_WEAK, "密钥不能为空");
        }

        // 检查常见弱密钥
        if (isCommonWeakSecret(secret)) {
            return ValidationResult.failure(StrengthLevel.VERY_WEAK, 
                "检测到常见弱密钥，请使用随机生成的密钥");
        }

        // 检查密钥长度（Base64 解码后）
        int keyLength;
        try {
            if (isBase64Encoded(secret)) {
                byte[] decoded = java.util.Base64.getDecoder().decode(secret);
                keyLength = decoded.length;
            } else {
                keyLength = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            }
        } catch (IllegalArgumentException e) {
            keyLength = secret.length();
        }

        // 评估强度
        if (keyLength < MIN_KEY_LENGTH_BYTES) {
            return ValidationResult.failure(StrengthLevel.WEAK, 
                String.format("密钥长度不足（当前%d字节，最小要求%d字节）", keyLength, MIN_KEY_LENGTH_BYTES));
        }

        if (keyLength < 48) {
            return ValidationResult.success(StrengthLevel.MEDIUM);
        }

        if (keyLength < 64) {
            return ValidationResult.success(StrengthLevel.STRONG);
        }

        return ValidationResult.success(StrengthLevel.VERY_STRONG);
    }

    /**
     * 验证密码强度
     * 
     * @param password 密码
     * @return 验证结果
     */
    public static ValidationResult validatePassword(final String password) {
        if (password == null || password.isEmpty()) {
            return ValidationResult.failure(StrengthLevel.VERY_WEAK, "密码不能为空");
        }

        // 检查常见弱密码
        if (isCommonWeakPassword(password)) {
            return ValidationResult.failure(StrengthLevel.VERY_WEAK, 
                "检测到常见弱密码，请使用更复杂的密码");
        }

        int length = password.length();
        int score = 0;

        // 长度评分：绝对长度是密码强度的首要来源。达到推荐长度（16）即给 4 分，使
        //「推荐长度 + 任意两类字符」达到 MEDIUM——实测 generateAlphanumericKey(16)/(20) 的
        // 产物分别有约 6% / 3% 概率整串不含数字，若仅因恰好缺少一个字符类别就落到 WEAK，
        // 会让密钥生成器的产物被自己的校验器拒掉（#171）。
        if (length >= RECOMMENDED_PASSWORD_LENGTH) {
            score += 4;
        } else if (length >= MIN_PASSWORD_LENGTH) {
            score += 2;
        } else if (length >= 8) {
            score += 1;
        }

        // 字符多样性评分
        if (password.matches(".*[a-z].*")) { score += 1; }  // 小写字母
        if (password.matches(".*[A-Z].*")) { score += 1; }  // 大写字母
        if (password.matches(".*\\d.*")) { score += 1; }    // 数字
        if (password.matches(".*[^a-zA-Z0-9].*")) { score += 1; }  // 特殊字符

        // 评估强度
        if (score <= 3) {
            return ValidationResult.failure(StrengthLevel.VERY_WEAK, 
                "密码过于简单，需要包含大小写字母、数字和特殊字符");
        }

        if (score <= 5) {
            return ValidationResult.failure(StrengthLevel.WEAK, 
                "密码强度不足，建议增加长度和字符多样性");
        }

        if (score <= 6) {
            return ValidationResult.success(StrengthLevel.MEDIUM);
        }

        if (score <= 7) {
            return ValidationResult.success(StrengthLevel.STRONG);
        }

        return ValidationResult.success(StrengthLevel.VERY_STRONG);
    }

    /**
     * 检查是否为常见弱密钥
     */
    private static boolean isCommonWeakSecret(final String secret) {
        String lower = secret.toLowerCase();

        // 常见弱密钥模式
        String[] weakPatterns = {
            "secret", "key", "token", "password", "admin",
            "123456", "qwerty", "abcdef",
            "your-", "change", "default",
            "test", "dev", "demo"
        };

        // 高熵密钥（如 SecretKeyGenerator 产出的随机 Base64）中短模式/长模式均可能偶然出现，
        // 仅当模式在密钥中占主导时才判定弱；人择低熵密钥则任意弱子串都算命中
        final boolean highEntropy = isHighEntropy(secret);
        final int secretLength = secret.length();

        for (String pattern : weakPatterns) {
            if (isWeakPatternHit(lower, pattern, secretLength, highEntropy)) {
                return true;
            }
        }

        // 检查是否为默认密钥
        if ("ChangeMeOnFirstStartup123456".equals(secret)) {
            return true;
        }

        // 检查连续字符
        if (secret.matches("^[a-zA-Z0-9]{1,}$")
        && (secret.equals(secret.toLowerCase()) || secret.equals(secret.toUpperCase()))) {
            return true;
        }

        return false;
    }

    /**
     * 单个弱模式是否应判定为命中。
     * 非高熵（人择）密钥：contains 即可。
     * 高熵（随机）密钥：仅当模式长度占比达到 {@link #DOMINANCE_NUMERATOR}/{@link #DOMINANCE_DENOMINATOR}
     * 才认为“密钥本身就是该弱模式”，否则视为随机串中的偶然子串。
     */
    private static boolean isWeakPatternHit(final String lower,
                                            final String pattern,
                                            final int secretLength,
                                            final boolean highEntropy) {
        if (!lower.contains(pattern)) {
            return false;
        }
        if (!highEntropy) {
            return true;
        }
        return pattern.length() * DOMINANCE_DENOMINATOR >= secretLength * DOMINANCE_NUMERATOR;
    }

    /**
     * 计算字符串的字符级香农熵（bit/字符）。
     */
    private static double shannonEntropy(final String s) {
        final int n = s.length();
        if (n == 0) {
            return 0.0;
        }
        final int[] counts = new int[256];
        for (int i = 0; i < n; i++) {
            final char c = s.charAt(i);
            if (c < 256) {
                counts[c]++;
            }
        }
        double entropy = 0.0;
        for (final int count : counts) {
            if (count > 0) {
                final double p = (double) count / n;
                entropy -= p * (Math.log(p) / Math.log(2.0));
            }
        }
        return entropy;
    }

    /**
     * 是否为高熵（疑似机器随机生成）密钥。
     */
    private static boolean isHighEntropy(final String secret) {
        return shannonEntropy(secret) >= HIGH_ENTROPY_THRESHOLD;
    }

    /**
     * 检查是否为常见弱密码
     */
    private static boolean isCommonWeakPassword(final String password) {
        String lower = password.toLowerCase();
        
        String[] weakPasswords = {
            "password", "admin", "123456", "12345678", "qwerty",
            "abc123", "password123", "admin123",
            "change", "default", "test", "guest"
        };

        // 与 isCommonWeakSecret 同一约定：人择低熵密码任意弱子串都算命中；高熵（机器随机生成，
        // 如 --generate-password 的产物）密码仅当弱模式在密码中占主导时才认为「密码本身就是该弱词」，
        // 否则视为随机串中的偶然子串
        final boolean highEntropy = isHighEntropy(password);
        final int passwordLength = password.length();

        for (String weak : weakPasswords) {
            if (!lower.contains(weak)) {
                continue;
            }
            if (!highEntropy
                    || weak.length() * PASSWORD_DOMINANCE_DENOMINATOR
                        >= passwordLength * PASSWORD_DOMINANCE_NUMERATOR) {
                return true;
            }
        }

        // 检查默认密码
        if ("ChangeMeOnFirstStartup123456".equals(password)) {
            return true;
        }

        return false;
    }

    /**
     * 检查字符串是否为 Base64 编码
     */
    private static boolean isBase64Encoded(final String str) {
        if (str == null || str.length() < 20) {
            return false;
        }
        
        // Base64 特征：只包含特定字符，长度通常是 4 的倍数
        if (!str.matches("^[A-Za-z0-9+/=]+$")) {
            return false;
        }

        try {
            java.util.Base64.getDecoder().decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 获取最小密钥长度要求
     */
    public static int getMinKeyLength() {
        return MIN_KEY_LENGTH_BYTES;
    }

    /**
     * 获取最小密码长度要求
     */
    public static int getMinPasswordLength() {
        return MIN_PASSWORD_LENGTH;
    }
}
