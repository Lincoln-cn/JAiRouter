package org.unreal.modelrouter.pii;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 敏感内容探测器（只检测、不替换）.
 *
 * <p><b>为什么需要它</b>：响应里的个人身份信息有两个来源 —— 一是网关自己换出去的占位符被还原
 * （可由 {@code piiRestoredCount > 0} 检测），二是<b>模型自己生成或复述的</b>。后者与可逆脱敏开关
 * 无关：即便请求里没有任何 PII，模型也可能吐出一串格式合法的身份证号。只靠前一个信号会漏掉它。</p>
 *
 * <p><b>为什么独立于脱敏开关</b>：开关表达的是「用户要不要脱敏」这一策略选择，不是「这段内容敏不敏感」
 * 这一事实判断。用开关当判据，等于让策略漏洞变成数据漏洞。</p>
 *
 * <p><b>失败语义 = 视为敏感</b>：正则编译失败、扫描异常一律返回 {@code true}。缓存是旁路能力，
 * 少一次命中可以接受，把敏感内容写进公共池不可接受。</p>
 *
 * <p>规则集 = 内置四条（身份证、手机号、银行卡、邮箱）+ {@code jairouter.pii.reversible.rules}
 * 里追加的自定义规则。只做匹配、不做替换，因此不会改变任何内容。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.0
 */
@Component
public class PiiDetector {

    private static final Logger logger = LoggerFactory.getLogger(PiiDetector.class);

    /** 内置规则（与可逆脱敏保持一致，改动须同步 {@link ReversiblePiiService}）。 */
    private static final List<NamedPattern> BUILTIN = List.of(
            new NamedPattern("ID_CARD", "\\b\\d{17}[\\dXx]\\b|\\b\\d{15}\\b"),
            new NamedPattern("MOBILE", "\\b1[3-9]\\d{9}\\b"),
            new NamedPattern("BANK_CARD", "\\b\\d{16,19}\\b"),
            new NamedPattern("EMAIL", "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")
    );

    private final PiiProperties properties;
    private final List<Pattern> patterns = new ArrayList<>();

    /**
     * 构造器.
     *
     * @param properties 可逆脱敏配置（自定义规则从这里追加）
     */
    @Autowired
    public PiiDetector(final PiiProperties properties) {
        this.properties = properties;
    }

    /** 编译规则（内置 + 自定义）。 */
    @PostConstruct
    void compile() {
        patterns.clear();
        if (properties.isBuiltinRulesEnabled()) {
            for (final NamedPattern named : BUILTIN) {
                final Pattern builtin = patternOf(named.name(), named.regex());
                if (builtin != null) {
                    patterns.add(builtin);
                }
            }
        }
        if (properties.getRules() != null) {
            for (final PiiProperties.Rule rule : properties.getRules()) {
                if (rule == null || rule.getPattern() == null || rule.getPattern().isBlank()) {
                    continue;
                }
                patterns.add(patternOf(rule.getName(), rule.getPattern()));
            }
        }
        logger.debug("PII 探测器编译完成，规则数={}", patterns.size());
    }

    /**
     * 文本里是否含敏感内容.
     *
     * @param text 待检测文本（可为 null）
     * @return 含敏感内容或检测失败时返回 {@code true}
     */
    public boolean containsSensitive(final String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (patterns.isEmpty()) {
            return false;
        }
        try {
            for (final Pattern pattern : patterns) {
                if (pattern.matcher(text).find()) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException ex) {
            // 检测本身出错时按「敏感」处理：宁可少缓存，也不把内容写进公共池
            logger.warn("PII 探测失败，按含敏感内容处理: {}", ex.toString());
            return true;
        }
    }

    /**
     * 当前生效的规则数量（供测试与运维观测）.
     *
     * @return 规则数
     */
    public int ruleCount() {
        return patterns.size();
    }

    /** 只读快照，供测试断言规则集。 */
    List<Pattern> patterns() {
        return Collections.unmodifiableList(patterns);
    }

    private Pattern patternOf(final String name, final String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException ex) {
            logger.warn("PII 探测规则 {} 的正则非法，已跳过: {}", name, ex.getDescription());
            return null;
        } catch (RuntimeException ex) {
            logger.warn("PII 探测规则 {} 编译失败，已跳过: {}", name, ex.toString());
            return null;
        }
    }

    /**
     * 内置规则（名称 + 正则）.
     *
     * @param name  规则名
     * @param regex 正则
     */
    private record NamedPattern(String name, String regex) {
    }
}
