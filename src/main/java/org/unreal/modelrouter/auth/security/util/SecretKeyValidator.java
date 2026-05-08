package org.unreal.modelrouter.auth.security.util;

/**
 * 密钥强度验证器
 * 用于验证 JWT 密钥、密码等敏感信息的安全性
 */
public class SecretKeyValidator {

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

        // 长度评分
        if (length >= RECOMMENDED_PASSWORD_LENGTH) {
            score += 3;
        } else if (length >= MIN_PASSWORD_LENGTH) {
            score += 2;
        } else if (length >= 8) {
            score += 1;
        }

        // 字符多样性评分
        if (password.matches(".*[a-z].*")) score += 1;  // 小写字母
        if (password.matches(".*[A-Z].*")) score += 1;  // 大写字母
        if (password.matches(".*\\d.*")) score += 1;    // 数字
        if (password.matches(".*[^a-zA-Z0-9].*")) score += 1;  // 特殊字符

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

        for (String pattern : weakPatterns) {
            if (lower.contains(pattern)) {
                return true;
            }
        }

        // 检查是否为默认密钥
        if ("UqfpTm2Zw7ff2BNnZb8AQo8t".equals(secret)) {
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
     * 检查是否为常见弱密码
     */
    private static boolean isCommonWeakPassword(final String password) {
        String lower = password.toLowerCase();
        
        String[] weakPasswords = {
            "password", "admin", "123456", "12345678", "qwerty",
            "abc123", "password123", "admin123",
            "change", "default", "test", "guest"
        };

        for (String weak : weakPasswords) {
            if (lower.contains(weak)) {
                return true;
            }
        }

        // 检查默认密码
        if ("UqfpTm2Zw7ff2BNnZb8AQo8t".equals(password)) {
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
