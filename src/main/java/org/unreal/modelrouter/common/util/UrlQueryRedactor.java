package org.unreal.modelrouter.common.util;

/**
 * 日志 / span 用的 URL 查询串脱敏工具（issue #695）。
 *
 * <p>凭据可以经 query 传给认证转换器（WebSocket 握手的 {@code ?token=} —— 浏览器
 * {@code new WebSocket} 无法设置自定义头），而结构化日志的 {@code url} 字段与 tracing
 * span 的 {@code http.url} 属性会记录完整请求 URL，凭据因此会进应用日志、APM、反向代理
 * 访问日志。本工具保留 query 的键名、把敏感键的值替换为 {@link #REDACTED}，使「谁请求了
 * 哪个接口」仍然可读，但凭据不再落盘。</p>
 *
 * <p>敏感键的判定沿用本仓既有口径（{@code RequestLogBuilder} 的 query 脱敏规则）：
 * 键名（小写）包含 {@code token} / {@code key} / {@code password} / {@code secret} /
 * {@code auth} / {@code credential} 之一即视为敏感，覆盖 {@code token}、
 * {@code access_token}、{@code api_key}、{@code apiKey}、{@code api-key}。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.3
 */
public final class UrlQueryRedactor {

    /** 敏感参数值的替换占位符。 */
    public static final String REDACTED = "[REDACTED]";

    /** 敏感 query 键名的片段（小写比对）。 */
    private static final String[] SENSITIVE_KEY_FRAGMENTS = {
        "token", "key", "password", "secret", "auth", "credential"
    };

    private UrlQueryRedactor() {
        // 工具类，禁止实例化
    }

    /**
     * 抹掉 URL 中敏感 query 参数的值，保留键名与非敏感参数。
     *
     * <p>无 query（或 {@code null}）时原样返回；URL 片段（{@code #...}）原样保留。</p>
     *
     * @param url 原始 URL，可为 {@code null}
     * @return 脱敏后的 URL
     */
    public static String redact(final String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        final int queryStart = url.indexOf('?');
        if (queryStart < 0) {
            return url;
        }

        final String base = url.substring(0, queryStart);
        String rest = url.substring(queryStart + 1);

        String fragment = "";
        final int hash = rest.indexOf('#');
        if (hash >= 0) {
            fragment = rest.substring(hash);
            rest = rest.substring(0, hash);
        }

        return base + '?' + redactQuery(rest) + fragment;
    }

    /**
     * 抹掉单个 query 串（不含首个 {@code ?}）中敏感参数的值。
     *
     * @param query query 串，可为 {@code null}
     * @return 脱敏后的 query 串
     */
    static String redactQuery(final String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }

        final String[] params = query.split("&", -1);
        final StringBuilder sanitized = new StringBuilder(query.length());
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sanitized.append('&');
            }
            final String param = params[i];
            final int eq = param.indexOf('=');
            if (eq < 0) {
                sanitized.append(param);
                continue;
            }
            final String key = param.substring(0, eq);
            if (isSensitiveQueryParam(key)) {
                sanitized.append(key).append('=').append(REDACTED);
            } else {
                sanitized.append(param);
            }
        }
        return sanitized.toString();
    }

    /**
     * 判断 query 参数名是否携带凭据。
     *
     * @param key 参数名
     * @return 命中敏感片段则为 {@code true}
     */
    static boolean isSensitiveQueryParam(final String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        final String lowerKey = key.toLowerCase();
        for (String fragment : SENSITIVE_KEY_FRAGMENTS) {
            if (lowerKey.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
