package org.unreal.modelrouter.common.api.auth;

import java.util.Map;

/**
 * JWT 服务接口 - 其他模块通过此接口访问 JWT 服务。
 *
 * <p>此接口用于模块间通信：
 * <ul>
 *   <li>router 模块通过此接口验证请求中的 JWT Token</li>
 *   <li>monitor 模块通过此接口获取 JWT 相关指标</li>
 * </ul>
 *
 * <p>微服务拆分后，此接口将成为 auth-service 对外暴露的 API。
 *
 * @since v2.8.0
 */
public interface JwtServiceApi {

    /**
     * 验证 JWT Token。
     *
     * @param token JWT Token 字串
     * @return 验证结果
     */
    JwtValidationResult validateToken(String token);

    /**
     * 从 Token 中提取账户信息。
     *
     * @param token JWT Token 字串
     * @return 账户信息，无效 Token 返回 null
     */
    JwtAccountInfo extractAccountInfo(String token);

    /**
     * 检查 Token 是否已撤销。
     *
     * @param jti JWT ID
     * @return true 如果已撤销
     */
    boolean isTokenRevoked(String jti);

    /**
     * 获取 JWT 服务统计信息。
     *
     * @return 统计数据
     */
    Map<String, Object> getStatistics();

    // === 内部 DTO ===

    /**
     * JWT 验证结果。
     */
    class JwtValidationResult {
        private boolean valid;
        private String error;
        private JwtAccountInfo accountInfo;

        public JwtValidationResult() {
        }

        public JwtValidationResult(boolean valid, String error) {
            this.valid = valid;
            this.error = error;
        }

        public static JwtValidationResult success(JwtAccountInfo accountInfo) {
            JwtValidationResult result = new JwtValidationResult(true, null);
            result.setAccountInfo(accountInfo);
            return result;
        }

        public static JwtValidationResult failure(String error) {
            return new JwtValidationResult(false, error);
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

        public JwtAccountInfo getAccountInfo() {
            return accountInfo;
        }

        public void setAccountInfo(JwtAccountInfo accountInfo) {
            this.accountInfo = accountInfo;
        }
    }

    /**
     * JWT 账户信息。
     */
    class JwtAccountInfo {
        private String accountId;
        private String username;
        private String role;
        private long expiresAt;
        private String jti;

        public JwtAccountInfo() {
        }

        public JwtAccountInfo(String accountId, String username, String role) {
            this.accountId = accountId;
            this.username = username;
            this.role = role;
        }

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public long getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(long expiresAt) {
            this.expiresAt = expiresAt;
        }

        public String getJti() {
            return jti;
        }

        public void setJti(String jti) {
            this.jti = jti;
        }
    }
}