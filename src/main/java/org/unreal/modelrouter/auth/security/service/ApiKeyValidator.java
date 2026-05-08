package org.unreal.modelrouter.auth.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.common.exception.AuthenticationException;

import java.util.Map;

/**
 * API Key 验证组件
 * 负责 API Key 的格式验证、安全检查和权限验证
 *
 * @since v2.14.0
 */
@Slf4j
@Component
public class ApiKeyValidator {

    /**
     * 验证 API Key 格式
     * 检查 keyValue 是否符合基本格式要求
     *
     * @param keyValue 原始 API Key 值
     * @return 验证结果
     */
    public ValidationResult validateFormat(final String keyValue) {
        if (keyValue == null || keyValue.trim().isEmpty()) {
            return ValidationResult.failure("API Key 不能为空");
        }

        if (keyValue.length() < 10) {
            return ValidationResult.failure("API Key 长度不足");
        }

        if (!keyValue.startsWith("sk-")) {
            return ValidationResult.failure("API Key 格式无效，必须以 'sk-' 开头");
        }

        return ValidationResult.success();
    }

    /**
     * 在缓存中查找匹配的 API Key
     * 使用哈希验证方式匹配
     *
     * @param keyValue 原始 API Key 值
     * @param apiKeyCache API Key 缓存（keyHash -> ApiKey）
     * @return 匹配的 ApiKey，如果未找到返回 null
     */
    public ApiKey findMatchingKey(final String keyValue, final Map<String, ApiKey> apiKeyCache) {
        for (ApiKey apiKey : apiKeyCache.values()) {
            if (apiKey.verifyKey(keyValue)) {
                return apiKey;
            }
        }
        return null;
    }

    /**
     * 验证 API Key 启用状态
     *
     * @param apiKey 要验证的 ApiKey
     * @return 验证结果
     */
    public ValidationResult validateEnabled(final ApiKey apiKey) {
        if (!apiKey.isEnabled()) {
            return ValidationResult.failure("API Key 已被禁用", AuthenticationException.INVALID_API_KEY);
        }
        return ValidationResult.success();
    }

    /**
     * 验证 API Key 是否过期
     *
     * @param apiKey 要验证的 ApiKey
     * @return 验证结果
     */
    public ValidationResult validateExpiration(final ApiKey apiKey) {
        if (apiKey.isExpired()) {
            return ValidationResult.failure("API Key 已过期", AuthenticationException.EXPIRED_API_KEY);
        }
        return ValidationResult.success();
    }

    /**
     * 验证 IP 白名单
     *
     * @param apiKey 要验证的 ApiKey
     * @param ipAddress 客户端 IP 地址
     * @return 验证结果
     */
    public ValidationResult validateIpWhitelist(final ApiKey apiKey, final String ipAddress) {
        if (!apiKey.isIpAllowed(ipAddress)) {
            return ValidationResult.failure("IP 地址不允许访问: " + ipAddress,
                    AuthenticationException.INVALID_API_KEY);
        }
        return ValidationResult.success();
    }

    /**
     * 验证每日请求限制
     *
     * @param apiKey 要验证的 ApiKey
     * @return 验证结果
     */
    public ValidationResult validateDailyLimit(final ApiKey apiKey) {
        if (apiKey.isDailyLimitExceeded()) {
            return ValidationResult.failure("超过每日请求限制", AuthenticationException.INVALID_API_KEY);
        }
        return ValidationResult.success();
    }

    /**
     * 执行完整的 API Key 验证流程
     *
     * @param keyValue 原始 API Key 值
     * @param apiKeyCache API Key 缓存
     * @param ipAddress 客户端 IP 地址
     * @return 验证结果，包含匹配的 ApiKey（如果验证成功）
     */
    public FullValidationResult validateFully(final String keyValue,
                                               final Map<String, ApiKey> apiKeyCache,
                                               final String ipAddress) {
        // 1. 格式验证
        ValidationResult formatResult = validateFormat(keyValue);
        if (!formatResult.isSuccess()) {
            return FullValidationResult.formatError(formatResult.getMessage());
        }

        // 2. 查找匹配的 Key
        ApiKey matchedKey = findMatchingKey(keyValue, apiKeyCache);
        if (matchedKey == null) {
            return FullValidationResult.notFound();
        }

        // 3. 启用状态验证
        ValidationResult enabledResult = validateEnabled(matchedKey);
        if (!enabledResult.isSuccess()) {
            return FullValidationResult.validationError(matchedKey.getKeyId(),
                    enabledResult.getMessage(), enabledResult.getErrorCode());
        }

        // 4. 过期验证
        ValidationResult expirationResult = validateExpiration(matchedKey);
        if (!expirationResult.isSuccess()) {
            return FullValidationResult.validationError(matchedKey.getKeyId(),
                    expirationResult.getMessage(), expirationResult.getErrorCode());
        }

        // 5. IP 白名单验证
        ValidationResult ipResult = validateIpWhitelist(matchedKey, ipAddress);
        if (!ipResult.isSuccess()) {
            return FullValidationResult.validationError(matchedKey.getKeyId(),
                    ipResult.getMessage(), ipResult.getErrorCode());
        }

        // 6. 每日限制验证
        ValidationResult limitResult = validateDailyLimit(matchedKey);
        if (!limitResult.isSuccess()) {
            return FullValidationResult.validationError(matchedKey.getKeyId(),
                    limitResult.getMessage(), limitResult.getErrorCode());
        }

        // 所有验证通过
        log.debug("API Key 验证成功: {}", matchedKey.getKeyId());
        return FullValidationResult.success(matchedKey.createSecureCopy());
    }

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final boolean success;
        private final String message;
        private final String errorCode;

        private ValidationResult(final boolean success, final String message, final String errorCode) {
            this.success = success;
            this.message = message;
            this.errorCode = errorCode;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult failure(final String message) {
            return new ValidationResult(false, message, AuthenticationException.INVALID_API_KEY);
        }

        public static ValidationResult failure(final String message, final String errorCode) {
            return new ValidationResult(false, message, errorCode);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    /**
     * 完整验证结果（包含 ApiKey 信息）
     */
    public static class FullValidationResult {
        private final boolean success;
        private final ApiKey apiKey;
        private final String keyId;
        private final String errorMessage;
        private final String errorCode;
        private final ValidationFailureType failureType;

        private FullValidationResult(final boolean success, final ApiKey apiKey,
                                     final String keyId, final String errorMessage,
                                     final String errorCode, final ValidationFailureType failureType) {
            this.success = success;
            this.apiKey = apiKey;
            this.keyId = keyId;
            this.errorMessage = errorMessage;
            this.errorCode = errorCode;
            this.failureType = failureType;
        }

        public static FullValidationResult success(final ApiKey apiKey) {
            return new FullValidationResult(true, apiKey, apiKey.getKeyId(), null, null, null);
        }

        public static FullValidationResult formatError(final String message) {
            return new FullValidationResult(false, null, null, message,
                    AuthenticationException.INVALID_API_KEY, ValidationFailureType.FORMAT_ERROR);
        }

        public static FullValidationResult notFound() {
            return new FullValidationResult(false, null, null, "无效的 API Key",
                    AuthenticationException.INVALID_API_KEY, ValidationFailureType.NOT_FOUND);
        }

        public static FullValidationResult validationError(final String keyId,
                                                           final String message,
                                                           final String errorCode) {
            return new FullValidationResult(false, null, keyId, message, errorCode,
                    ValidationFailureType.VALIDATION_ERROR);
        }

        public boolean isSuccess() {
            return success;
        }

        public ApiKey getApiKey() {
            return apiKey;
        }

        public String getKeyId() {
            return keyId;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public ValidationFailureType getFailureType() {
            return failureType;
        }

        /**
         * 转换为 AuthenticationException
         */
        public AuthenticationException toException() {
            if (success) {
                throw new IllegalStateException("验证成功，无需转换为异常");
            }
            return new AuthenticationException(errorMessage, errorCode);
        }
    }

    /**
     * 验证失败类型
     */
    public enum ValidationFailureType {
        FORMAT_ERROR,
        NOT_FOUND,
        VALIDATION_ERROR
    }
}