package org.unreal.modelrouter.router.loadbalancer;

/**
 * v2.9.0: 会话亲和性键解析器
 *
 * <p>解析粘性路由键: apiKeyId + "|" + serviceType + "|" + modelName
 * 无 apiKeyId 时回退到 clientIp 作为亲和性键。
 *
 * <p>同一亲和性键的请求会被路由到同一实例，利用 LLM KV 缓存前缀复用。
 */
public final class AffinityKeyResolver {

    private static final String DELIMITER = "|";

    private AffinityKeyResolver() {
        // 工具类，禁止实例化
    }

    /**
     * 解析亲和性键
     *
     * @param apiKeyId  API Key ID (可为 null)
     * @param clientIp  客户端 IP (可为 null)
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @return 亲和性键; apiKeyId 存在时返回 apiKeyId|serviceType|modelName，
     *         否则返回 clientIp|serviceType|modelName，clientIp 也无则返回 null
     */
    public static String resolve(final String apiKeyId,
                                 final String clientIp,
                                 final String serviceType,
                                 final String modelName) {
        String tenantKey = apiKeyId != null && !apiKeyId.isBlank() ? apiKeyId : clientIp;
        if (tenantKey == null || tenantKey.isBlank()) {
            return null;
        }
        String st = serviceType != null ? serviceType : "";
        String mn = modelName != null ? modelName : "";
        return tenantKey + DELIMITER + st + DELIMITER + mn;
    }

    /**
     * 解析无服务类型和模型名的亲和性键(纯租户维度)
     */
    public static String resolveTenantKey(final String apiKeyId, final String clientIp) {
        String tenantKey = apiKeyId != null && !apiKeyId.isBlank() ? apiKeyId : clientIp;
        if (tenantKey == null || tenantKey.isBlank()) {
            return null;
        }
        return tenantKey;
    }
}
