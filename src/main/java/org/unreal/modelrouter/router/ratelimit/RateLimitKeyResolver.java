package org.unreal.modelrouter.router.ratelimit;

/**
 * 限流键解析（#161）：按配置的维度选出用于区分被限流对象的键值。
 *
 * <p>做成纯函数（无 Spring 依赖、不读全局状态），便于穷举各种维度与缺失组合做单元测试——
 * 这些组合是限流最容易出错的地方。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.4
 */
public final class RateLimitKeyResolver {

    /** 解析结果类型。 */
    public enum Outcome {
        /** 维度值本身可用。 */
        RESOLVED,
        /** 维度值缺失，已回退到客户端 IP（限流仍然生效）。 */
        FALLBACK_TO_CLIENT_IP,
        /** 维度值与回退值都缺失，无法确定限流对象（调用方应放行并告警）。 */
        UNAVAILABLE
    }

    /**
     * 解析结果。
     *
     * @param dimension 生效维度
     * @param key       限流键值；{@link Outcome#UNAVAILABLE} 时为 {@code null}
     * @param outcome   结果类型
     */
    public record Resolution(RateLimitKeyDimension dimension, String key, Outcome outcome) {

        /**
         * 是否可用于限流。
         *
         * @return 键值是否可用
         */
        public boolean available() {
            return outcome != Outcome.UNAVAILABLE;
        }

        /**
         * 是否发生了回退。
         *
         * @return 是否回退到客户端 IP
         */
        public boolean usedFallback() {
            return outcome == Outcome.FALLBACK_TO_CLIENT_IP;
        }

        /**
         * 回退是否属于异常情形。
         *
         * <p>租户维度的回退是设计行为（API Key 缺失即按客户端 IP 归组，与
         * {@code AffinityKeyResolver.resolveTenantKey} 一致）；而 API Key 维度下
         * 请求未携带 Key 说明配置与实际流量不匹配，需要告警。</p>
         *
         * @return 是否属于需要告警的回退
         */
        public boolean unexpectedFallback() {
            return outcome == Outcome.FALLBACK_TO_CLIENT_IP
                    && dimension == RateLimitKeyDimension.API_KEY;
        }
    }

    private RateLimitKeyResolver() {
        // 工具类，禁止实例化
    }

    /**
     * 按维度解析限流键值。
     *
     * <p>回退规则：{@link RateLimitKeyDimension#API_KEY} 与 {@link RateLimitKeyDimension#TENANT}
     * 在 API Key 缺失时回退客户端 IP——<b>保持限流生效</b>，而不是静默放行；两者都缺失时才判定
     * 为不可用，由调用方放行并告警。</p>
     *
     * @param dimension 配置的维度，{@code null} 视为 {@link RateLimitKeyDimension#CLIENT_IP}
     * @param clientIp  客户端 IP，可为 null
     * @param apiKeyId  调用方 API Key ID，可为 null
     * @return 解析结果，绝不返回 null
     */
    public static Resolution resolve(final RateLimitKeyDimension dimension,
                                     final String clientIp,
                                     final String apiKeyId) {
        final RateLimitKeyDimension dim =
                dimension != null ? dimension : RateLimitKeyDimension.CLIENT_IP;
        final boolean hasIp = isPresent(clientIp);
        final boolean hasApiKey = isPresent(apiKeyId);

        if (dim == RateLimitKeyDimension.CLIENT_IP) {
            return hasIp
                    ? new Resolution(dim, clientIp, Outcome.RESOLVED)
                    : new Resolution(dim, null, Outcome.UNAVAILABLE);
        }

        if (hasApiKey) {
            return new Resolution(dim, apiKeyId, Outcome.RESOLVED);
        }
        return hasIp
                ? new Resolution(dim, clientIp, Outcome.FALLBACK_TO_CLIENT_IP)
                : new Resolution(dim, null, Outcome.UNAVAILABLE);
    }

    private static boolean isPresent(final String value) {
        return value != null && !value.trim().isEmpty();
    }
}
