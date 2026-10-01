package org.unreal.modelrouter.common.util;

import java.lang.management.ManagementFactory;

/**
 * 本**应用副本**（进程）的标识。
 *
 * <p>注意与 {@link InstanceIdUtils} 区分：后者解析的是**下游 AI 服务实例**的 ID（业务配置里的
 * {@code instanceId}），本类解析的是**当前运行的这个 JAiRouter 副本自身**的标识。</p>
 *
 * <p>取值顺序：{@code INSTANCE_ID} 环境变量 → {@code POD_NAME}（Kubernetes downward API 注入）
 * → {@code HOSTNAME} → 进程 PID 兜底。结果会被规范化为可安全用作文件名与 Redis key 的 token
 * （只保留字母数字与 {@code . _ -}），并缓存。</p>
 *
 * <p>用途：多副本部署下让各副本的**本地产物彼此可区分**（例如归档文件、指标落盘文件按副本命名，
 * 避免多个副本写同一个文件名造成内容交错）。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.3
 */
public final class InstanceIdentity {

    /** 规范化后的本副本标识，进程生命周期内不变。 */
    private static final String ID = resolve();

    /** 非法字符的最大长度（避免超长主机名破坏文件名/key）。 */
    private static final int MAX_LENGTH = 64;

    private InstanceIdentity() {
    }

    /**
     * 本副本的标识。
     *
     * @return 非空、已规范化、可安全用于文件名与 Redis key 的字符串
     */
    public static String id() {
        return ID;
    }

    private static String resolve() {
        String raw = firstNonBlank(
                System.getenv("INSTANCE_ID"),
                System.getenv("POD_NAME"),
                System.getenv("HOSTNAME"));
        if (raw == null) {
            raw = "pid-" + currentPid();
        }
        return sanitize(raw);
    }

    private static String firstNonBlank(final String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.trim().isEmpty()) {
                return candidate.trim();
            }
        }
        return null;
    }

    private static String currentPid() {
        try {
            // JVM 名形如 "12345@hostname"
            String name = ManagementFactory.getRuntimeMXBean().getName();
            int at = name.indexOf('@');
            return at > 0 ? name.substring(0, at) : name;
        } catch (Exception e) {
            // 取不到 PID 时退化为进程内唯一值，保证同一进程内稳定
            return Integer.toHexString(System.identityHashCode(InstanceIdentity.class));
        }
    }

    private static String sanitize(final String raw) {
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), MAX_LENGTH));
        for (int i = 0; i < raw.length() && sb.length() < MAX_LENGTH; i++) {
            char c = raw.charAt(i);
            boolean safe = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-';
            sb.append(safe ? c : '_');
        }
        String result = sb.toString();
        return result.isEmpty() ? "unknown" : result;
    }
}
