package org.unreal.modelrouter.config.core.helper;

import org.unreal.modelrouter.config.version.diff.ConfigDiff;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 实例配置读面的敏感请求头脱敏（issue #1053）
 *
 * <p>实例的 {@code headers} 字段存的是**发往上游的鉴权头**（{@code Authorization}、{@code x-api-key} 等），
 * 由 {@code NonStreamingRequestProcessor} 原样附加到出站请求。而 {@code GET /api/config/instance/{serviceType}}
 * 只需要 {@code config:instances:read} 权限 —— 这个权限同时授予 USER 与 VIEWER 角色，
 * 于是任何一个普通登录用户都能直接读到上游供应商的长期凭证。</p>
 *
 * <p>读面因此改为返回掩码值（保留末 4 位，便于运维辨认是哪一把 key，但不足以复用）。
 * 写面（{@code CreateServiceInstanceRequest}）是独立的类型，但管理台会把读到的值回填到编辑表单，
 * 保存时再原样提交 ⇒ {@link #preserveMasked(Map, Map)} 用来在写入前把「掩码值」替换回库里的原值，
 * 避免掩码覆盖掉真实凭证。</p>
 */
public final class SensitiveHeaderMasker {

    /** 掩码前缀。写入侧靠它识别「这是读面回显的掩码，而不是用户新填的值」。 */
    public static final String MASK_PREFIX = "***";

    /** 掩码里保留的原值尾字符数。 */
    private static final int TAIL_LENGTH = 4;

    /** 短于该长度的值不做尾部保留 —— 保留 4 位就几乎等于泄漏全长。 */
    private static final int MIN_LENGTH_FOR_TAIL = 12;

    /** 归一化（小写、去掉分隔符）后的敏感头名。 */
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "authorization",
            "proxyauthorization",
            "xapikey",
            "apikey",
            "xauthtoken",
            "authtoken",
            "token",
            "accesstoken",
            "xaccesstoken",
            "secret",
            "clientsecret",
            "password",
            "cookie",
            "setcookie");

    private SensitiveHeaderMasker() {
    }

    /**
     * 把敏感头（以及名字里含 {@code key}/{@code token}/{@code secret}/{@code auth} 的头）的值替换为掩码。
     *
     * <p>非敏感头原样返回；{@code null} 与空 map 返回空 map（不返回 {@code null}，
     * 免得调用方在序列化时多一层判空）。</p>
     */
    public static Map<String, String> mask(final Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> masked = new LinkedHashMap<>(headers.size());
        headers.forEach((name, value) -> masked.put(name, isSensitiveName(name) ? maskValue(value) : value));
        return masked;
    }

    /**
     * 写入侧：把入参里「等于库里原值掩码」的值还原为库里的原值，其余照入参保存。
     *
     * <p>典型场景 = 管理台先 GET（拿到 {@code ***abcd}）→ 改个权重 → PUT 整份表单回来。
     * 若不还原，库里的真实凭证就被 {@code ***abcd} 覆盖，上游立刻 401。</p>
     */
    public static Map<String, String> preserveMasked(final Map<String, String> incoming,
                                                     final Map<String, String> existing) {
        if (incoming == null || incoming.isEmpty()) {
            return incoming;
        }
        if (existing == null || existing.isEmpty()) {
            return incoming;
        }
        Map<String, String> merged = new LinkedHashMap<>(incoming);
        incoming.forEach((name, value) -> {
            if (!isSensitiveName(name) || !isMasked(value)) {
                return;
            }
            String original = findIgnoreCase(existing, name);
            // 只有「入参确实是库里原值的掩码」才还原：写死一串 *** 想清空凭证的请求不受影响。
            if (original != null && maskValue(original).equals(value)) {
                merged.put(name, original);
            }
        });
        return merged;
    }

    /** 判断一个头名是否属于「值不能明文外泄」的那一类。 */
    public static boolean isSensitiveName(final String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String normalized = normalizeName(name);
        if (SENSITIVE_NAMES.contains(normalized)) {
            return true;
        }
        return normalized.contains("apikey")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("authorization")
                || normalized.contains("password");
    }

    /**
     * 递归脱敏一棵配置树里所有 {@code headers} 节点的值（issue #1053）。
     *
     * <p>配置版本读面（{@code GET /api/config/version/{version}}）返回的是 config_data 的完整 JSON，
     * 里面同样带着实例的上游鉴权头。这个端点只需要 {@code config:versions:read}，而 VIEWER 角色持有全部
     * {@code :read} 权限 ⇒ 与实例读面是同一个泄漏面。</p>
     *
     * <p>只影响**返回给调用方**的副本：{@code config_data} 里存的仍是原值，
     * 所以 {@code POST /api/config/version/apply/{version}} 回滚后上游凭证依然可用。</p>
     */
    public static Map<String, Object> maskConfigTree(final Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        Object masked = maskNode(config);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) masked;
        return result;
    }

    /**
     * 对配置版本差异做同样的脱敏：路径指向 {@code headers} 时，旧值/新值一并打码。
     */
    public static ConfigDiff maskDiff(final ConfigDiff diff) {
        if (diff == null) {
            return null;
        }
        return ConfigDiff.builder()
                .sourceVersion(diff.getSourceVersion())
                .targetVersion(diff.getTargetVersion())
                .added(maskItems(diff.getAdded()))
                .removed(maskItems(diff.getRemoved()))
                .modified(maskItems(diff.getModified()))
                .unchangedCount(diff.getUnchangedCount())
                .build();
    }

    private static List<ConfigDiff.DiffItem> maskItems(final List<ConfigDiff.DiffItem> items) {
        if (items == null || items.isEmpty()) {
            return items;
        }
        List<ConfigDiff.DiffItem> masked = new ArrayList<>(items.size());
        for (ConfigDiff.DiffItem item : items) {
            if (!touchesHeaders(item.getPath())) {
                masked.add(item);
                continue;
            }
            masked.add(ConfigDiff.DiffItem.builder()
                    .path(item.getPath())
                    .oldValue(maskValueNode(item.getOldValue()))
                    .newValue(maskValueNode(item.getNewValue()))
                    .changeType(item.getChangeType())
                    .valueType(item.getValueType())
                    .build());
        }
        return masked;
    }

    private static boolean touchesHeaders(final String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains(".headers") || lower.startsWith("headers");
    }

    private static Object maskValueNode(final Object value) {
        if (value instanceof Map<?, ?> map) {
            return mask(mapToStringMap(map));
        }
        if (value instanceof String text) {
            return maskValue(text);
        }
        return value;
    }

    private static Object maskNode(final Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>(map.size());
            map.forEach((key, value) -> {
                String name = key == null ? "" : key.toString();
                if ("headers".equalsIgnoreCase(name) && value instanceof Map<?, ?> headers) {
                    out.put(name, mask(mapToStringMap(headers)));
                } else {
                    out.put(name, maskNode(value));
                }
            });
            return out;
        }
        if (node instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(maskNode(item));
            }
            return out;
        }
        return node;
    }

    private static Map<String, String> mapToStringMap(final Map<?, ?> source) {
        Map<String, String> out = new LinkedHashMap<>(source.size());
        source.forEach((key, value) -> out.put(String.valueOf(key), value == null ? null : String.valueOf(value)));
        return out;
    }

    /** 判断一个值是否已是本类产出的掩码形态。 */
    public static boolean isMasked(final String value) {
        return value != null && value.startsWith(MASK_PREFIX);
    }

    /** 生成掩码：长值保留末 4 位，短值整段遮住。 */
    public static String maskValue(final String value) {
        if (value == null || value.isEmpty()) {
            return MASK_PREFIX;
        }
        if (value.length() < MIN_LENGTH_FOR_TAIL) {
            return MASK_PREFIX;
        }
        return MASK_PREFIX + value.substring(value.length() - TAIL_LENGTH);
    }

    private static String normalizeName(final String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "").trim();
    }

    private static String findIgnoreCase(final Map<String, String> headers, final String name) {
        String direct = headers.get(name);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
