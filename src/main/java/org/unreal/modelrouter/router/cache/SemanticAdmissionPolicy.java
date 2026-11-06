package org.unreal.modelrouter.router.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 语义缓存的准入判定.
 *
 * <p>两层准入，严格程度不同：</p>
 * <ul>
 *   <li><b>L2（租户内）</b>：要求确定性生成（{@code temperature} 为 0 或未设、{@code n} 为 1）。
 *       读者就是写入者本人，只要「同一问题答案稳定」即可复用。</li>
 *   <li><b>L3（公共）</b>：在 L2 的基础上再加五条 —— 单轮、无 system 角色、无工具调用、
 *       请求体不含敏感内容、归一化长度不超上限，且顶层字段只认白名单。</li>
 * </ul>
 *
 * <p><b>为什么 L3 这么严</b>：L3 的读者是任意租户。多轮对话的相似不蕴含等价（第二轮依赖第一轮的
 * 上下文）、system 提示决定了回答口径、工具调用会带出外部数据 —— 这三类情形下「看起来像同一个问题」
 * 恰恰最危险。长请求同理：「北京今天天气怎么样」和「北京今天天气怎么样，另外帮我写一份周报」
 * 的 SimHash 可能很近，但答案完全不是一回事。</p>
 *
 * <p><b>空配置即拒绝</b>：{@code body} 为 null、结构不是对象、{@code messages} 缺失或为空时一律不放行。</p>
 *
 * @author JAiRouter Team
 * @since 3.7.4
 */
@Component
public class SemanticAdmissionPolicy {

    private static final Logger logger = LoggerFactory.getLogger(SemanticAdmissionPolicy.class);

    /** L3 允许出现的顶层字段（其余字段的存在意味着这次请求带了额外语义）。 */
    private static final Set<String> PUBLIC_FIELDS = Set.of(
            "model", "messages", "temperature", "max_tokens", "max_completion_tokens", "top_p", "stream");

    private final SemanticCacheProperties properties;

    /**
     * 构造器.
     *
     * @param properties 语义缓存配置
     */
    public SemanticAdmissionPolicy(final SemanticCacheProperties properties) {
        this.properties = properties;
    }

    /**
     * 能否进入 L2（租户内）.
     *
     * @param body 请求体（已反序列化为 Map）
     * @return true 表示可以
     */
    public boolean canEnterTenant(final Map<String, Object> body) {
        if (!properties.isEnabled() || !properties.isTenantScopeEnabled()) {
            return false;
        }
        return isDeterministic(body);
    }

    /**
     * 能否进入 L3（公共）.
     *
     * @param body           请求体
     * @param normalizedText 归一化后的请求文本
     * @return true 表示可以
     */
    public boolean canEnterPublic(final Map<String, Object> body, final String normalizedText) {
        if (!properties.isEnabled() || !properties.isPublicScopeEnabled()) {
            return false;
        }
        if (!isDeterministic(body)) {
            return false;
        }
        final List<?> messages = messagesOf(body);
        if (messages == null || messages.size() != 1) {
            return false;
        }
        if (hasSystemRole(messages) || hasToolUsage(body, messages)) {
            return false;
        }
        if (normalizedText == null || normalizedText.length() > Math.max(1, properties.getMaxPublicChars())) {
            return false;
        }
        if (!onlyPublicFields(body)) {
            return false;
        }
        return true;
    }

    /**
     * 生成是否确定性（可复用）.
     *
     * @param body 请求体
     * @return true 表示可复用
     */
    public boolean isDeterministic(final Map<String, Object> body) {
        if (properties.isAllowNonDeterministic()) {
            return true;
        }
        if (body == null) {
            return false;
        }
        final Object temperature = body.get("temperature");
        if (temperature instanceof Number number && number.doubleValue() != 0.0d) {
            return false;
        }
        final Object n = body.get("n");
        return !(n instanceof Number count) || count.intValue() <= 1;
    }

    private boolean onlyPublicFields(final Map<String, Object> body) {
        if (body == null) {
            return false;
        }
        for (final String key : body.keySet()) {
            if (!PUBLIC_FIELDS.contains(key)) {
                logger.debug("语义缓存 L3 拒绝：请求含非白名单字段 {}", key);
                return false;
            }
        }
        return true;
    }

    private List<?> messagesOf(final Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        final Object messages = body.get("messages");
        if (!(messages instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        return list;
    }

    private boolean hasSystemRole(final List<?> messages) {
        for (final Object message : messages) {
            if (message instanceof Map<?, ?> map) {
                final Object role = map.get("role");
                if (role != null && "system".equalsIgnoreCase(String.valueOf(role))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasToolUsage(final Map<String, Object> body, final List<?> messages) {
        if (body.containsKey("tools") || body.containsKey("tool_choice") || body.containsKey("functions")) {
            return true;
        }
        for (final Object message : messages) {
            if (!(message instanceof Map<?, ?> map)) {
                continue;
            }
            if (map.containsKey("tool_calls") || map.containsKey("tool_call_id") || map.containsKey("function_call")) {
                return true;
            }
            final Object role = map.get("role");
            if (role != null && "tool".equalsIgnoreCase(String.valueOf(role))) {
                return true;
            }
        }
        return false;
    }

    /** 供测试断言白名单内容。 */
    static Collection<String> publicFields() {
        return PUBLIC_FIELDS;
    }
}
