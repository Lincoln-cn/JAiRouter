package org.unreal.modelrouter.auth.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.util.ApiKeyFormat;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.constants.ErrorCode.RenderPath;
import org.unreal.modelrouter.common.exception.AuthenticationException;

import java.util.Map;
import java.util.Optional;

/**
 * API Key 验证组件
 * 负责 API Key 的格式验证、安全检查和权限验证
 *
 * @since v2.14.0
 */
@Slf4j
@Component
public class ApiKeyValidator {

    @Autowired(required = false)
    private TokenBucketRateLimiter rateLimiter;

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
        return findMatchingKey(keyValue, apiKeyCache, null);
    }

    /**
     * 查找匹配的 API Key（#826：新格式按键内 keyId 直接定位，旧格式回退为遍历）.
     *
     * <p>新格式 {@code sk-<keyId>.<secret>} 可以先用 keyId 从索引里取唯一一条候选，
     * 再做一次哈希比较；因此密钥数量增长不再线性拖慢认证路径。密钥文本里声明的 keyId
     * 在索引里不存在时直接判无效，不做全表遍历 —— 否则攻击者构造 {@code sk-<随便>.<随便>}
     * 依然能触发遍历，抵消这次优化的安全意义。</p>
     *
     * @param keyValue 原始 API Key 值
     * @param apiKeyCache API Key 缓存（keyHash -> ApiKey）
     * @param keyIdIndex keyId -> keyHash 索引，为 {@code null} 时按旧行为遍历
     * @return 匹配的 ApiKey，如果未找到返回 null
     */
    public ApiKey findMatchingKey(final String keyValue, final Map<String, ApiKey> apiKeyCache,
                                  final Map<String, String> keyIdIndex) {
        final Optional<String> encodedKeyId = ApiKeyFormat.keyIdOf(keyValue);
        if (encodedKeyId.isPresent() && keyIdIndex != null) {
            final String keyHash = keyIdIndex.get(encodedKeyId.get());
            if (keyHash == null) {
                return null;
            }
            final ApiKey candidate = apiKeyCache.get(keyHash);
            return candidate != null && candidate.verifyKey(keyValue) ? candidate : null;
        }
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
            return ValidationResult.failure("API Key 已被禁用", ErrorCode.INVALID_API_KEY.code());
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
            return ValidationResult.failure("API Key 已过期", ErrorCode.EXPIRED_API_KEY.code());
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
                    ErrorCode.INVALID_API_KEY.code());
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
            return ValidationResult.failure("超过每日请求限制", ErrorCode.DAILY_QUOTA_EXCEEDED.code());
        }
        return ValidationResult.success();
    }

    /**
     * 验证每日 Token 使用限制
     *
     * @param apiKey 要验证的 ApiKey
     * @return 验证结果
     */
    public ValidationResult validateTokenLimit(final ApiKey apiKey) {
        if (apiKey.isDailyTokenLimitExceeded()) {
            return ValidationResult.failure("超过每日 Token 使用限制", ErrorCode.TOKEN_QUOTA_EXCEEDED.code());
        }
        return ValidationResult.success();
    }

    /**
     * 验证请求速率限制.
     *
     * <p>超限时返回的码是 {@link ErrorCode#RATE_LIMITED_API_KEY}（客户端可见 **429**），
     * **不是**管理面限流器直写扁平体时用的 {@link ErrorCode#RATE_LIMIT_EXCEEDED}：
     * 两者的客户端可见状态相同（429），但入口面与错误体形状不同，故按 issue #404 的裁定拆成两个码。</p>
     *
     * @param apiKey 要验证的 ApiKey
     * @return 验证结果
     */
    public ValidationResult validateRateLimit(final ApiKey apiKey) {
        if (apiKey.getRateLimitPerMinute() <= 0) {
            return ValidationResult.success();
        }
        if (rateLimiter == null) {
            return ValidationResult.success();
        }
        if (!rateLimiter.tryAcquire(apiKey.getKeyId(), apiKey.getRateLimitPerMinute())) {
            return ValidationResult.failure("请求频率超过限制，请稍后再试",
                    ErrorCode.RATE_LIMITED_API_KEY.code());
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
        return validateFully(keyValue, apiKeyCache, null, ipAddress);
    }

    /**
     * 执行完整的 API Key 验证流程（#826：带 keyId 索引，新格式走 O(1) 查找）.
     *
     * @param keyValue 原始 API Key 值
     * @param apiKeyCache API Key 缓存
     * @param keyIdIndex keyId -> keyHash 索引，为 {@code null} 时按旧行为遍历
     * @param ipAddress 客户端 IP 地址
     * @return 验证结果，包含匹配的 ApiKey（如果验证成功）
     */
    public FullValidationResult validateFully(final String keyValue,
                                               final Map<String, ApiKey> apiKeyCache,
                                               final Map<String, String> keyIdIndex,
                                               final String ipAddress) {
        // 1. 格式验证
        ValidationResult formatResult = validateFormat(keyValue);
        if (!formatResult.isSuccess()) {
            return FullValidationResult.formatError(formatResult.getMessage());
        }

        // 2. 查找匹配的 Key
        ApiKey matchedKey = findMatchingKey(keyValue, apiKeyCache, keyIdIndex);
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

        // 6. 速率限制验证
        ValidationResult rateResult = validateRateLimit(matchedKey);
        if (!rateResult.isSuccess()) {
            return FullValidationResult.validationError(matchedKey.getKeyId(),
                    rateResult.getMessage(), rateResult.getErrorCode());
        }

        // 7. 每日请求限制验证
        ValidationResult limitResult = validateDailyLimit(matchedKey);
        if (!limitResult.isSuccess()) {
            return FullValidationResult.validationError(matchedKey.getKeyId(),
                    limitResult.getMessage(), limitResult.getErrorCode());
        }

        // 8. 每日 Token 使用限制验证
        ValidationResult tokenResult = validateTokenLimit(matchedKey);
        if (!tokenResult.isSuccess()) {
            return FullValidationResult.validationError(matchedKey.getKeyId(),
                    tokenResult.getMessage(), tokenResult.getErrorCode());
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
        private final HttpStatus httpStatus;

        private FullValidationResult(final boolean success, final ApiKey apiKey,
                                     final String keyId, final String errorMessage,
                                     final String errorCode, final ValidationFailureType failureType,
                                     final HttpStatus httpStatus) {
            this.success = success;
            this.apiKey = apiKey;
            this.keyId = keyId;
            this.errorMessage = errorMessage;
            this.errorCode = errorCode;
            this.failureType = failureType;
            this.httpStatus = httpStatus;
        }

        public static FullValidationResult success(final ApiKey apiKey) {
            return new FullValidationResult(true, apiKey, apiKey.getKeyId(), null, null, null,
                    statusFromRegistry(null));
        }

        public static FullValidationResult formatError(final String message) {
            return new FullValidationResult(false, null, null, message,
                    AuthenticationException.INVALID_API_KEY, ValidationFailureType.FORMAT_ERROR,
                    statusFromRegistry(AuthenticationException.INVALID_API_KEY));
        }

        public static FullValidationResult notFound() {
            return new FullValidationResult(false, null, null, "无效的 API Key",
                    AuthenticationException.INVALID_API_KEY, ValidationFailureType.NOT_FOUND,
                    statusFromRegistry(AuthenticationException.INVALID_API_KEY));
        }

        public static FullValidationResult validationError(final String keyId,
                                                           final String message,
                                                           final String errorCode) {
            return new FullValidationResult(false, null, keyId, message, errorCode,
                    ValidationFailureType.VALIDATION_ERROR, statusFromRegistry(errorCode));
        }

        /**
         * 从错误码注册表推导该校验失败应携带的 HTTP 状态码.
         *
         * <p><b>耦合声明（显式）</b>：状态取自 {@link ErrorCode} 注册表里
         * {@link RenderPath#AUTHENTICATION} 渲染路径登记的 {@link ErrorCode#status()} ——
         * 校验链上抛出的异常状态**不在本类另立一张表**（否则「注册表说 429、异常携带 401」会再次分叉）。
         * 未登记、或不属于认证链渲染路径的码一律回落到 {@link HttpStatus#UNAUTHORIZED}，
         * 因此除 {@link ErrorCode#RATE_LIMITED_API_KEY} 外，本链上所有码的状态仍是 401（零行为变化）。</p>
         *
         * @param errorCode 校验失败的错误码，可为 {@code null}
         * @return 注册表登记的认证链状态；未登记或非认证链码 → 401
         */
        private static HttpStatus statusFromRegistry(final String errorCode) {
            return ErrorCode.of(errorCode)
                    .filter(entry -> entry.renderPath() == RenderPath.AUTHENTICATION)
                    .map(ErrorCode::status)
                    .orElse(HttpStatus.UNAUTHORIZED);
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

        /** 该失败结果经 {@link #toException()} 交给客户端的状态码（取自注册表的认证链路径） */
        public HttpStatus getHttpStatus() {
            return httpStatus;
        }

        /**
         * 转换为 AuthenticationException.
         *
         * <p>走 3 参构造器把状态一并带进异常：认证链上唯一非 401 的码是
         * {@link ErrorCode#RATE_LIMITED_API_KEY}（429），其余码经
         * {@link #statusFromRegistry(String)} 仍是 401。</p>
         */
        public AuthenticationException toException() {
            if (success) {
                throw new IllegalStateException("验证成功，无需转换为异常");
            }
            return new AuthenticationException(errorMessage, errorCode, httpStatus);
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