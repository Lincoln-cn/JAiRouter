package org.unreal.modelrouter.router.cache;

/**
 * 语义缓存的 scope 命名规则.
 *
 * <p>缓存条目的读者范围由 scope 决定，而不是由「模型名」决定 —— 这是本类存在的原因：</p>
 * <ul>
 *   <li>{@code t|<apiKeyId>|<user>|<model>} —— L2，读者是「同一 API Key 下的同一终端用户」；</li>
 *   <li>{@code p|<model>} —— L3，读者是任意租户。</li>
 * </ul>
 *
 * <p><b>{@code user} 不构成安全边界</b>：它由客户端自报，任何调用方都能随便填。它只用于把 L2 的
 * 范围收得更窄（同一 API Key 下不同终端用户互不可见），不能用于放宽 —— 缺省时退化成
 * {@code t|<apiKeyId>|<model>}，而不是退化成按 IP 或公共桶。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.0
 */
public final class SemanticCacheScope {

    /** L2 前缀。 */
    static final String TENANT_PREFIX = "t|";

    /** L3 前缀。 */
    static final String PUBLIC_PREFIX = "p|";

    private SemanticCacheScope() {
    }

    /**
     * 构造 L2（租户内）scope.
     *
     * @param apiKeyId API Key 标识（调用方身份的主键）
     * @param user     请求体里的 user 字段（可为 null）
     * @param modelName 模型名
     * @return scope
     */
    public static String tenant(final String apiKeyId, final String user, final String modelName) {
        final String tenant = safe(apiKeyId);
        final String who = user == null || user.isBlank() ? "-" : safe(user);
        return TENANT_PREFIX + tenant + "|" + who + "|" + safe(modelName);
    }

    /**
     * 构造 L3（公共）scope.
     *
     * @param modelName 模型名
     * @return scope
     */
    public static String publicScope(final String modelName) {
        return PUBLIC_PREFIX + safe(modelName);
    }

    /**
     * 是否为 L3 scope.
     *
     * @param scope scope
     * @return true 表示公共池
     */
    public static boolean isPublic(final String scope) {
        return scope != null && scope.startsWith(PUBLIC_PREFIX);
    }

    /**
     * scope 是否属于给定租户（用于「按租户清除 L2」）.
     *
     * @param scope    scope
     * @param apiKeyId 租户标识
     * @return true 表示属于该租户
     */
    public static boolean belongsToTenant(final String scope, final String apiKeyId) {
        if (scope == null || apiKeyId == null) {
            return false;
        }
        return scope.startsWith(TENANT_PREFIX + safe(apiKeyId) + "|");
    }

    /** scope 段里不允许出现分隔符，否则会串桶。 */
    private static String safe(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace('|', '/');
    }
}
