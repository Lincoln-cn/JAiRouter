package org.unreal.modelrouter.config.core.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * 会话粘性配置 DTO（issue #1095）。
 *
 * <p>对应配置面里的 {@code sticky} 块：
 *
 * <pre>{@code
 * sticky:
 *   enabled: false
 *   affinityKeyScope: tenant_model
 * }</pre>
 *
 * <p><b>为什么需要这个 record：</b>{@code GET /api/config/type/services/{serviceType}}
 * 是走 {@link ServiceConfiguration#toMap()} 返回的，而这个 record 此前没有 sticky 字段，
 * 于是「写进配置树的 sticky」在读面完全消失 —— 运维既看不到粘性当前是开是关，
 * 工具链也无法据回读值判断是否真的关闭成功。
 */
public record StickyConfiguration(
        Boolean enabled,
        String affinityKeyScope
) {
    /**
     * 创建默认粘性配置：开启 + {@code tenant_model} 粒度。
     *
     * <p>与服务端 {@code ModelRouterProperties.StickyConfig} 的默认值保持一致，
     * 避免「接口报默认开启、实际运行默认关闭」这类口径分裂。
     */
    public static StickyConfiguration defaultConfig() {
        return new StickyConfiguration(Boolean.TRUE, "tenant_model");
    }

    /**
     * 从 Map 转换为 DTO。
     *
     * <p>无法识别的 {@code enabled}（既不是布尔也不是 {@code "true"/"false"} 字符串）按
     * {@code null} 处理 —— 调用方据此回落到默认值，而不是把它当成「已关闭」。
     */
    public static StickyConfiguration fromMap(final Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        return new StickyConfiguration(
                getBoolean(map, "enabled"),
                getString(map, "affinityKeyScope")
        );
    }

    /**
     * 转换为 Map：只输出非 null 字段，避免往下游写 {@code "enabled": null}。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (enabled != null) {
            map.put("enabled", enabled);
        }
        if (affinityKeyScope != null) {
            map.put("affinityKeyScope", affinityKeyScope);
        }
        return map;
    }

    private static String getString(final Map<String, Object> map, final String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : null;
    }

    private static Boolean getBoolean(final Map<String, Object> map, final String key) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ("true".equalsIgnoreCase(text)) {
                return Boolean.TRUE;
            }
            if ("false".equalsIgnoreCase(text)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }
}
