package org.unreal.modelrouter.common.api.auth;

import java.util.Map;

/**
 * ApiKey 服务接口 - 其他模块通过此接口访问 ApiKey 服务。
 *
 * <p>此接口用于模块间通信：
 * <ul>
 *   <li>router 模块通过此接口验证请求中的 ApiKey</li>
 *   <li>monitor 模块通过此接口获取 ApiKey 相关指标</li>
 * </ul>
 *
 * <p>微服务拆分后，此接口将成为 auth-service 对外暴露的 API。
 *
 * @since v2.8.0
 */
public interface ApiKeyServiceApi {

    /**
     * 验证 ApiKey。
     *
     * @param apiKey ApiKey 字串
     * @return 验证结果
     */
    ApiKeyValidationResult validateApiKey(String apiKey);

    /**
     * 获取 ApiKey 信息。
     *
     * @param apiKeyId ApiKey ID
     * @return ApiKey 信息，不存在时返回 null
     */
    ApiKeyInfo getApiKeyInfo(String apiKeyId);

    /**
     * 检查 ApiKey 是否有效。
     *
     * @param apiKey ApiKey 字串
     * @return true 如果有效
     */
    boolean isValidApiKey(String apiKey);

    /**
     * 获取 ApiKey 服务统计信息。
     *
     * @return 统计数据
     */
    Map<String, Object> getStatistics();

    // === 内部 DTO ===

    /**
     * ApiKey 验证结果。
     */
    class ApiKeyValidationResult {
        private boolean valid;
        private String error;
        private ApiKeyInfo apiKeyInfo;

        public ApiKeyValidationResult() {
        }

        public ApiKeyValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }

        public static ApiKeyValidationResult success(ApiKeyInfo apiKeyInfo) {
            ApiKeyValidationResult result = new ApiKeyValidationResult(true, null);
            result.setApiKeyInfo(apiKeyInfo);
            return result;
        }

        public static ApiKeyValidationResult failure(String error) {
            return new ApiKeyValidationResult(false, error);
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public ApiKeyInfo getApiKeyInfo() {
            return apiKeyInfo;
        }

        public void setApiKeyInfo(ApiKeyInfo apiKeyInfo) {
            this.apiKeyInfo = apiKeyInfo;
        }
    }

    /**
     * ApiKey 信息。
     */
    class ApiKeyInfo {
        private String apiKeyId;
        private String accountId;
        private String prefix;
        private String status;
        private long expiresAt;
        private String[] permissions;

        public ApiKeyInfo() {
        }

        public ApiKeyInfo(String apiKeyId, String accountId, String prefix) {
            this.apiKeyId = apiKeyId;
            this.accountId = accountId;
            this.prefix = prefix;
        }

        public String getApiKeyId() {
            return apiKeyId;
        }

        public void setApiKeyId(String apiKeyId) {
            this.apiKeyId = apiKeyId;
        }

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public long getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(long expiresAt) {
            this.expiresAt = expiresAt;
        }

        public String[] getPermissions() {
            return permissions;
        }

        public void setPermissions(String[] permissions) {
            this.permissions = permissions;
        }
    }
}