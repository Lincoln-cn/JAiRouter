package org.unreal.modelrouter.router.loadbalancer;

/**
 * v2.9.0: 会话亲和性上下文持有者
 *
 * <p>通过 ThreadLocal 在请求处理链路中传递亲和性键的原始组件(apiKeyId, clientIp, serviceType, modelName)，
 * 以便在 ModelServiceRegistry 中根据 sticky.scope 配置动态解析出正确粒度的亲和性键。
 *
 * <p>使用模式:
 * <pre>
 *   AffinityContextHolder.set(apiKeyId, clientIp, serviceType, modelName);
 *   try {
 *       // 在 ModelServiceRegistry 中按 scope 解析
 *       String key = AffinityContextHolder.resolveKey(scope);
 *   } finally {
 *       AffinityContextHolder.clear();
 *   }
 * </pre>
 */
public final class AffinityContextHolder {

    private static final ThreadLocal<String> AFFINITY_KEY = new ThreadLocal<>();
    private static final ThreadLocal<String> API_KEY_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CLIENT_IP = new ThreadLocal<>();
    private static final ThreadLocal<String> SERVICE_TYPE = new ThreadLocal<>();
    private static final ThreadLocal<String> MODEL_NAME = new ThreadLocal<>();

    private AffinityContextHolder() {
    }

    /**
     * 设置亲和性上下文(存储原始组件，支持按 scope 重新解析)
     */
    public static void set(final String apiKeyId, final String clientIp,
                           final String serviceType, final String modelName) {
        // #689: 入参为 null 时必须 remove 而不是跳过 —— 线程池会复用线程，跳过会把上一个
        // 请求的标识（尤其是 apiKeyId，它同时是 api-key/tenant 维度限流的键）留给下一个请求。
        setOrClear(API_KEY_ID, apiKeyId);
        setOrClear(CLIENT_IP, clientIp);
        setOrClear(SERVICE_TYPE, serviceType);
        setOrClear(MODEL_NAME, modelName);
        // 同时设置默认 key(兼容直接 get() 的场景)
        setOrClear(AFFINITY_KEY, AffinityKeyResolver.resolve(apiKeyId, clientIp, serviceType, modelName));
    }

    private static void setOrClear(final ThreadLocal<String> holder, final String value) {
        if (value != null) {
            holder.set(value);
        } else {
            holder.remove();
        }
    }

    /**
     * 按指定粒度解析亲和性键(从存储的原始组件重新解析)
     *
     * @param scope 粘性粒度: "tenant_model" / "tenant"
     * @return 按 scope 解析后的亲和性键; 无法生成时返回 null
     */
    public static String resolveKey(final String scope) {
        return AffinityKeyResolver.resolve(
                API_KEY_ID.get(),
                CLIENT_IP.get(),
                SERVICE_TYPE.get(),
                MODEL_NAME.get(),
                scope);
    }

    /**
     * 获取默认粒度(tenant_model)的亲和性键
     */
    public static String get() {
        return AFFINITY_KEY.get();
    }

    /**
     * 当前请求的 API Key ID（未设置或未鉴权时返回 null）。
     *
     * <p>限流的 {@code api-key} / {@code tenant} 键维度用它作为被限流对象的标识（#161）。</p>
     *
     * @return API Key ID，可能为 null
     */
    public static String apiKeyId() {
        return API_KEY_ID.get();
    }

    /**
     * 清理当前线程的全部亲和性上下文。
     *
     * <p>#689: 调用方把 {@link #set} 放在 try 内、{@link #clear} 放在 finally，早退路径因此也会清理。</p>
     */
    public static void clear() {
        AFFINITY_KEY.remove();
        API_KEY_ID.remove();
        CLIENT_IP.remove();
        SERVICE_TYPE.remove();
        MODEL_NAME.remove();
    }
}
