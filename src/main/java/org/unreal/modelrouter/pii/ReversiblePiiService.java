package org.unreal.modelrouter.pii;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.monitor.callhistory.crypto.RecordContentCipher;
import org.unreal.modelrouter.persistence.jpa.entity.PiiTokenVaultEntity;
import org.unreal.modelrouter.persistence.jpa.repository.PiiTokenVaultRepository;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 可逆脱敏：把 PII 换成占位符发往上游，回包时再换回来（#987）。
 *
 * <p><b>与 {@code SanitizationService} 的分工</b>：那个是单向的（换了就没了），
 * 只能用于日志与落库；本类是双向的，用同一个占位符把原文留在 vault 里，
 * 让「数据不出域但能调外部模型」成立。</p>
 *
 * <p><b>安全边界</b>：</p>
 * <ul>
 *   <li>占位符只做精确替换 —— 模型把 {@code [PII_1]} 改写成别的样子时，
 *       响应里就原样保留那段文字，<b>绝不猜测还原</b>（宁可让用户看到占位符，
 *       也不把错误的值还原成真实个人信息）。</li>
 *   <li>原文只以密文存在库里，密钥与调用历史共用同一套
 *       {@link RecordContentCipher}（AES-256-GCM）。</li>
 *   <li>映射带 TTL，过期即不可还原；清理任务见
 *       {@code PiiTokenVaultCleanupScheduler}。</li>
 *   <li>scope 隔离：不同请求的占位符编号互不可见，避免跨请求串号。</li>
 * </ul>
 */
@Service
public class ReversiblePiiService {

    private static final Logger log = LoggerFactory.getLogger(ReversiblePiiService.class);

    /** 内置规则：身份证（18 位，末位可为 X）。 */
    private static final String ID_CARD = "[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]";
    /** 内置规则：中国大陆手机号。 */
    private static final String MOBILE = "(?<!\\d)1[3-9]\\d{9}(?!\\d)";
    /** 内置规则：16–19 位银行卡号（两侧不能是数字，减少误伤长订单号以外的串）。 */
    private static final String BANK_CARD = "(?<!\\d)\\d{16,19}(?!\\d)";
    /** 内置规则：邮箱。 */
    private static final String EMAIL = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}";

    private final PiiTokenVaultRepository repository;

    private final RecordContentCipher cipher;

    private final PiiProperties properties;

    /** 编译后的规则，按名字排序保证同一段文本每次得到相同的编号顺序。 */
    private final List<CompiledRule> rules;

    public ReversiblePiiService(PiiTokenVaultRepository repository,
                                RecordContentCipher cipher,
                                PiiProperties properties) {
        this.repository = repository;
        this.cipher = cipher;
        this.properties = properties;
        this.rules = compileRules(properties);
    }

    /**
     * 开关是否打开。关闭时调用方应完全跳过本服务（连文本都不解析）。
     */
    public boolean enabled() {
        return properties.isEnabled();
    }

    /**
     * 把文本里的 PII 换成占位符，并把原文加密后写入 vault。
     *
     * @param scopeId 本次请求的隔离标识（同一次请求的 tokenize 与 detokenize 必须传同一个值）
     * @param text    待处理文本
     * @return 替换后的文本
     */
    public Mono<String> tokenize(String scopeId, String text) {
        if (!enabled() || text == null || text.isEmpty() || rules.isEmpty()) {
            return Mono.justOrEmpty(text);
        }
        return Mono.fromCallable(() -> doTokenize(scopeId, text))
                .doOnError(e -> log.warn("可逆脱敏失败，本次请求按未脱敏处理: {}", e.toString()));
    }

    /**
     * 把响应里的占位符换回原文。
     *
     * <p>只做精确的字符串替换：模型没改写占位符就能还原，改写了就原样保留。</p>
     */
    public Mono<String> detokenize(String scopeId, String text) {
        if (!enabled() || text == null || text.isEmpty()) {
            return Mono.justOrEmpty(text);
        }
        return Mono.fromCallable(() -> {
                    final List<PiiTokenVaultEntity> entries =
                            repository.findByScopeIdAndExpiresAtAfter(scopeId, LocalDateTime.now());
                    if (entries.isEmpty()) {
                        return text;
                    }
                    String result = text;
                    for (final PiiTokenVaultEntity entry : entries) {
                        if (!result.contains(entry.getPlaceholder())) {
                            continue;
                        }
                        result = result.replace(entry.getPlaceholder(), cipher.decrypt(entry.getCiphertext()));
                    }
                    return result;
                })
                .onErrorResume(e -> {
                    log.warn("还原脱敏内容失败，按占位符原样返回: {}", e.toString());
                    return Mono.just(text);
                });
    }

    /**
     * 删除过期映射，返回删除条数。
     */
    public int cleanupExpired() {
        final LocalDateTime now = LocalDateTime.now();
        return repository.deleteByExpiresAtBefore(now);
    }

    private String doTokenize(String scopeId, String text) {
        String working = text;
        int counter = 0;
        final LocalDateTime now = LocalDateTime.now();
        final List<PiiTokenVaultEntity> pending = new ArrayList<>();

        for (final CompiledRule rule : rules) {
            final Matcher matcher = rule.pattern.matcher(working);
            final StringBuilder replaced = new StringBuilder();
            boolean changed = false;
            while (matcher.find()) {
                final String matched = matcher.group();
                counter++;
                final String placeholder = "[" + properties.getPlaceholderPrefix() + "_" + counter + "]";
                matcher.appendReplacement(replaced, Matcher.quoteReplacement(placeholder));
                changed = true;
                pending.add(PiiTokenVaultEntity.builder()
                        .scopeId(scopeId)
                        .placeholder(placeholder)
                        .ciphertext(cipher.encrypt(matched))
                        .ruleName(rule.name)
                        .createdAt(now)
                        .expiresAt(now.plus(properties.getTtl()))
                        .build());
            }
            if (changed) {
                matcher.appendTail(replaced);
                working = replaced.toString();
            }
        }

        if (!pending.isEmpty()) {
            repository.saveAll(pending);
        }
        return working;
    }

    private static List<CompiledRule> compileRules(PiiProperties properties) {
        final List<CompiledRule> compiled = new ArrayList<>();
        if (properties.isBuiltinRulesEnabled()) {
            addRule(compiled, "id-card", ID_CARD);
            addRule(compiled, "mobile", MOBILE);
            addRule(compiled, "bank-card", BANK_CARD);
            addRule(compiled, "email", EMAIL);
        }
        for (final PiiProperties.Rule rule : properties.getRules()) {
            if (rule.getName() == null || rule.getPattern() == null) {
                continue;
            }
            addRule(compiled, rule.getName(), rule.getPattern());
        }
        compiled.sort(Comparator.comparing(r -> r.name));
        return compiled;
    }

    private static void addRule(List<CompiledRule> target, String name, String regex) {
        try {
            target.add(new CompiledRule(name, Pattern.compile(regex)));
        } catch (RuntimeException e) {
            log.warn("可逆脱敏规则 {} 的正则非法，已跳过: {}", name, e.toString());
        }
    }

    /** 名字 + 预编译正则。 */
    private record CompiledRule(String name, Pattern pattern) {
    }

    /**
     * 供测试与排障用的规则视图。
     */
    public List<String> ruleNames() {
        return rules.stream().map(CompiledRule::name).toList();
    }
}
