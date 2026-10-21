package org.unreal.modelrouter.auth.sanitization.impl;

import java.util.Locale;
import java.util.Set;

/**
 * JSON 键名豁免清单（issue #561）。
 *
 * <p>这些键的值是<b>排障关联标识</b>：把它们打码会让响应体里的 ID 与日志里的 ID 对不上，
 * 属于「多破坏」而不是「多保护」。仅在 JSON-aware 模式下按键名生效 —— 非 JSON 文本
 * 没有键名可推断，行为不变。</p>
 *
 * <p>两个引擎（{@link DefaultSanitizationRuleEngine} 与 {@link OptimizedSanitizationRuleEngine}）
 * 共用同一份清单与判定，避免「改了一个引擎、生产用的是另一个」这类漂移：
 * 运行时真正装配的是 {@code @Primary} 的 Optimized 引擎，而语料测试跑的是 Default 引擎。</p>
 */
final class JsonKeyExemption {

    /**
     * 豁免键名（统一转小写比较）。
     */
    private static final Set<String> EXEMPT_KEYS =
            Set.of("traceid", "spanid", "requestid", "traceparent", "tracestate");

    private JsonKeyExemption() {
    }

    /**
     * 判断键名是否属于豁免清单（大小写不敏感）。
     *
     * @param key JSON 键名，{@code null} 表示该字符串值没有可推断的键名
     * @return {@code true} 表示该键的值必须原样保留
     */
    static boolean isExempt(final String key) {
        return key != null && EXEMPT_KEYS.contains(key.toLowerCase(Locale.ROOT));
    }
}
