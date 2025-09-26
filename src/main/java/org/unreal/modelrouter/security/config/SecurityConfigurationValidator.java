package org.unreal.modelrouter.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.security.config.properties.*;
import org.unreal.modelrouter.security.model.ApiKeyInfo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 安全配置验证器
 * 提供配置有效性验证功能，防止无效配置导致系统异常
 */
@Slf4j
@Component
public class SecurityConfigurationValidator {

    // 支持的JWT算法
    private static final List<String> SUPPORTED_JWT_ALGORITHMS = List.of(
            "HS256", "HS384", "HS512", "RS256", "RS384", "RS512"
    );

    // 支持的日志级别
    private static final List<String> SUPPORTED_LOG_LEVELS = List.of(
            "TRACE", "DEBUG", "INFO", "WARN", "ERROR"
    );

    // API Key格式验证正则表达式
    private static final Pattern API_KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_]{8,128}$");

    // 邮箱格式验证正则表达式
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    /**
     * 验证完整的安全配置
     * @param properties 安全配置
     * @return 验证结果
     */
    public ValidationResult validateConfiguration(SecurityProperties properties) {
        log.debug("开始验证安全配置");
        
        ValidationResult result = new ValidationResult();
        
        if (properties == null) {
            result.addError("安全配置不能为空");
            return result;
        }

        // 验证API Key配置
        if (properties.getApiKey() != null) {
            validateApiKeyConfig(properties.getApiKey(), result);
        }

        // 验证JWT配置
        if (properties.getJwt() != null && properties.getJwt().isEnabled()) {
            validateJwtConfig(properties.getJwt(), result);
        }

        // 验证脱敏配置
        if (properties.getSanitization() != null) {
            validateSanitizationConfig(properties.getSanitization(), result);
        }

        // 验证审计配置
        if (properties.getAudit() != null) {
            validateAuditConfig(properties.getAudit(), result);
        }

        log.debug("安全配置验证完成，错误数量: {}, 警告数量: {}", 
                result.getErrors().size(), result.getWarnings().size());
        
        return result;
    }

    /**
     * 验证API Key配置
     */
    private void validateApiKeyConfig(ApiKeyConfig config, ValidationResult result) {
        if (config.isEnabled()) {
            // 验证请求头名称
            if (config.getHeaderName() == null || config.getHeaderName().trim().isEmpty()) {
                result.addError("API Key请求头名称不能为空");
            } else if (config.getHeaderName().length() > 100) {
                result.addError("API Key请求头名称长度不能超过100个字符");
            }

            // 验证默认过期天数
            if (config.getDefaultExpirationDays() <= 0) {
                result.addError("API Key默认过期天数必须大于0");
            } else if (config.getDefaultExpirationDays() > 3650) {
                result.addWarning("API Key默认过期天数超过10年，建议设置较短的过期时间");
            }

            // 验证API Key列表
            if (config.getKeys() != null) {
                validateApiKeyList(config.getKeys().stream().map(ApiKeyProperties::covertTo).toList(), result);
            }
        }
    }

    /**
     * 验证API Key列表
     */
    private void validateApiKeyList(List<ApiKeyInfo> apiKeys, ValidationResult result) {
        if (apiKeys.isEmpty()) {
            result.addWarning("API Key列表为空，系统将无法进行API Key认证");
            return;
        }

        for (int i = 0; i < apiKeys.size(); i++) {
            ApiKeyInfo apiKey = apiKeys.get(i);
            String prefix = "API Key[" + i + "]";

            // 验证Key ID
            if (apiKey.getKeyId() == null || apiKey.getKeyId().trim().isEmpty()) {
                result.addError(prefix + " Key ID不能为空");
            } else if (apiKey.getKeyId().length() > 100) {
                result.addError(prefix + " Key ID长度不能超过100个字符");
            }

            // 验证Key Value
            if (apiKey.getKeyValue() == null || apiKey.getKeyValue().trim().isEmpty()) {
                result.addError(prefix + " Key Value不能为空");
            } else if (!API_KEY_PATTERN.matcher(apiKey.getKeyValue()).matches()) {
                result.addError(prefix + " Key Value格式无效，应为8-128位字母数字字符");
            }

            // 验证过期时间
            if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
                result.addWarning(prefix + " 已过期");
            }

            // 验证权限列表
            if (apiKey.getPermissions() != null && apiKey.getPermissions().isEmpty()) {
                result.addWarning(prefix + " 权限列表为空，该API Key将无法访问任何资源");
            }

            // 检查重复的Key ID
            for (int j = i + 1; j < apiKeys.size(); j++) {
                if (apiKey.getKeyId().equals(apiKeys.get(j).getKeyId())) {
                    result.addError("存在重复的API Key ID: " + apiKey.getKeyId());
                    break;
                }
            }
        }
    }

    /**
     * 验证JWT配置
     */
    private void validateJwtConfig(JwtConfig config, ValidationResult result) {
        // 验证密钥
        if (config.getSecret() == null || config.getSecret().length() < 32) {
            result.addError("JWT密钥长度至少32个字符");
        } else if (config.getSecret().length() > 512) {
            result.addWarning("JWT密钥长度超过512个字符，可能影响性能");
        }

        // 验证算法
        if (config.getAlgorithm() == null || !SUPPORTED_JWT_ALGORITHMS.contains(config.getAlgorithm())) {
            result.addError("不支持的JWT算法: " + config.getAlgorithm() + 
                    "，支持的算法: " + String.join(", ", SUPPORTED_JWT_ALGORITHMS));
        }

        // 验证过期时间
        if (config.getExpirationMinutes() <= 0) {
            result.addError("JWT过期时间必须大于0分钟");
        } else if (config.getExpirationMinutes() > 1440) {
            result.addWarning("JWT过期时间超过24小时，建议设置较短的过期时间");
        }

        // 验证刷新过期天数
        if (config.getRefreshExpirationDays() <= 0) {
            result.addError("JWT刷新过期天数必须大于0");
        } else if (config.getRefreshExpirationDays() > 30) {
            result.addWarning("JWT刷新过期天数超过30天，建议设置较短的过期时间");
        }

        // 验证发行者
        if (config.getIssuer() == null || config.getIssuer().trim().isEmpty()) {
            result.addError("JWT发行者不能为空");
        } else if (config.getIssuer().length() > 100) {
            result.addError("JWT发行者长度不能超过100个字符");
        }
    }

    /**
     * 验证脱敏配置
     */
    private void validateSanitizationConfig(SanitizationConfig config, ValidationResult result) {
        // 验证请求脱敏配置
        if (config.getRequest() != null) {
            validateRequestSanitizationConfig(config.getRequest(), result);
        }

        // 验证响应脱敏配置
        if (config.getResponse() != null) {
            validateResponseSanitizationConfig(config.getResponse(), result);
        }
    }

    /**
     * 验证请求脱敏配置
     */
    private void validateRequestSanitizationConfig(
            SanitizationConfig.RequestSanitization config, ValidationResult result) {
        
        // 验证掩码字符
        if (config.getMaskingChar() == null || config.getMaskingChar().trim().isEmpty()) {
            result.addError("请求脱敏掩码字符不能为空");
        } else if (config.getMaskingChar().length() > 5) {
            result.addError("请求脱敏掩码字符长度不能超过5个字符");
        }

        // 验证敏感词列表
        if (config.getSensitiveWords() != null) {
            validateSensitiveWords(config.getSensitiveWords(), "请求脱敏", result);
        }

        // 验证PII模式列表
        if (config.getPiiPatterns() != null) {
            validatePiiPatterns(config.getPiiPatterns(), "请求脱敏", result);
        }

        // 验证白名单用户
        if (config.getWhitelistUsers() != null) {
            validateWhitelistUsers(config.getWhitelistUsers(), result);
        }
    }

    /**
     * 验证响应脱敏配置
     */
    private void validateResponseSanitizationConfig(
            SanitizationConfig.ResponseSanitization config, ValidationResult result) {
        
        // 验证掩码字符
        if (config.getMaskingChar() == null || config.getMaskingChar().trim().isEmpty()) {
            result.addError("响应脱敏掩码字符不能为空");
        } else if (config.getMaskingChar().length() > 5) {
            result.addError("响应脱敏掩码字符长度不能超过5个字符");
        }

        // 验证敏感词列表
        if (config.getSensitiveWords() != null) {
            validateSensitiveWords(config.getSensitiveWords(), "响应脱敏", result);
        }

        // 验证PII模式列表
        if (config.getPiiPatterns() != null) {
            validatePiiPatterns(config.getPiiPatterns(), "响应脱敏", result);
        }
    }

    /**
     * 验证敏感词列表
     */
    private void validateSensitiveWords(List<String> sensitiveWords, String context, ValidationResult result) {
        if (sensitiveWords.isEmpty()) {
            result.addWarning(context + "敏感词列表为空，脱敏功能可能无效");
            return;
        }

        for (int i = 0; i < sensitiveWords.size(); i++) {
            String word = sensitiveWords.get(i);
            if (word == null || word.trim().isEmpty()) {
                result.addError(context + "敏感词[" + i + "]不能为空");
            } else if (word.length() > 100) {
                result.addError(context + "敏感词[" + i + "]长度不能超过100个字符");
            }
        }
    }

    /**
     * 验证PII模式列表
     */
    private void validatePiiPatterns(List<String> piiPatterns, String context, ValidationResult result) {
        if (piiPatterns.isEmpty()) {
            result.addWarning(context + "PII模式列表为空，PII数据脱敏功能可能无效");
            return;
        }

        for (int i = 0; i < piiPatterns.size(); i++) {
            String pattern = piiPatterns.get(i);
            if (pattern == null || pattern.trim().isEmpty()) {
                result.addError(context + "PII模式[" + i + "]不能为空");
                continue;
            }

            try {
                Pattern.compile(pattern);
            } catch (Exception e) {
                result.addError(context + "PII模式[" + i + "]格式无效: " + e.getMessage());
            }
        }
    }

    /**
     * 验证白名单用户列表
     */
    private void validateWhitelistUsers(List<String> whitelistUsers, ValidationResult result) {
        for (int i = 0; i < whitelistUsers.size(); i++) {
            String user = whitelistUsers.get(i);
            if (user == null || user.trim().isEmpty()) {
                result.addError("白名单用户[" + i + "]不能为空");
            } else if (user.length() > 100) {
                result.addError("白名单用户[" + i + "]长度不能超过100个字符");
            }
        }
    }

    /**
     * 验证审计配置
     */
    private void validateAuditConfig(AuditConfig config, ValidationResult result) {
        // 验证日志级别
        if (config.getLogLevel() == null || !SUPPORTED_LOG_LEVELS.contains(config.getLogLevel())) {
            result.addError("不支持的日志级别: " + config.getLogLevel() + 
                    "，支持的级别: " + String.join(", ", SUPPORTED_LOG_LEVELS));
        }

        // 验证保留天数
        if (config.getRetentionDays() <= 0) {
            result.addError("审计日志保留天数必须大于0");
        } else if (config.getRetentionDays() > 3650) {
            result.addWarning("审计日志保留天数超过10年，可能占用大量存储空间");
        }

        // 验证告警阈值
        if (config.getAlertThresholds() != null) {
            validateAlertThresholds(config.getAlertThresholds(), result);
        }
    }

    /**
     * 验证告警阈值配置
     */
    private void validateAlertThresholds(AuditConfig.AlertThresholds thresholds, ValidationResult result) {
        if (thresholds.getAuthFailuresPerMinute() <= 0) {
            result.addError("认证失败告警阈值必须大于0");
        } else if (thresholds.getAuthFailuresPerMinute() > 1000) {
            result.addWarning("认证失败告警阈值过高，可能无法及时发现安全威胁");
        }

        if (thresholds.getSanitizationOperationsPerMinute() <= 0) {
            result.addError("脱敏操作告警阈值必须大于0");
        } else if (thresholds.getSanitizationOperationsPerMinute() > 10000) {
            result.addWarning("脱敏操作告警阈值过高，可能无法及时发现异常");
        }
    }

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public void addError(String error) {
            errors.add(error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ValidationResult{");
            sb.append("valid=").append(isValid());
            if (!errors.isEmpty()) {
                sb.append(", errors=").append(errors);
            }
            if (!warnings.isEmpty()) {
                sb.append(", warnings=").append(warnings);
            }
            sb.append("}");
            return sb.toString();
        }
    }
}