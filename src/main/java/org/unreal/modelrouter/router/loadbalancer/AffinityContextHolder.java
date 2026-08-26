package org.unreal.modelrouter.router.loadbalancer;

/**
 * v2.9.0: 会话亲和性上下文持有者
 *
 * <p>通过 ThreadLocal 在请求处理链路中传递 affinityKey，
 * 避免修改 selectInstance 签名影响所有调用方。
 *
 * <p>使用模式:
 * <pre>
 *   AffinityContextHolder.set(affinityKey);
 *   try {
 *       selectInstance(...);  // 内部读取 AffinityContextHolder.get()
 *   } finally {
 *       AffinityContextHolder.clear();
 *   }
 * </pre>
 */
public final class AffinityContextHolder {

    private static final ThreadLocal<String> AFFINITY_KEY = new ThreadLocal<>();

    private AffinityContextHolder() {
    }

    public static void set(final String affinityKey) {
        if (affinityKey != null) {
            AFFINITY_KEY.set(affinityKey);
        }
    }

    public static String get() {
        return AFFINITY_KEY.get();
    }

    public static void clear() {
        AFFINITY_KEY.remove();
    }
}
