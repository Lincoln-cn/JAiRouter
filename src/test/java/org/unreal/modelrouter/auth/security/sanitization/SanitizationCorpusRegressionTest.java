package org.unreal.modelrouter.auth.security.sanitization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.core.io.ClassPathResource;
import org.unreal.modelrouter.auth.sanitization.SanitizationRuleEngine;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import org.unreal.modelrouter.auth.sanitization.impl.DefaultSanitizationRuleEngine;
import org.unreal.modelrouter.auth.sanitization.impl.DefaultSanitizationService;
import org.unreal.modelrouter.auth.sanitization.impl.OptimizedSanitizationRuleEngine;
import org.unreal.modelrouter.auth.security.config.properties.SanitizationConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.SanitizationRule;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 脱敏回归语料测试（TA1）.
 *
 * <p>用<b>真实</b>的 {@link DefaultSanitizationRuleEngine} + {@link DefaultSanitizationService}
 * 跑 {@code src/test/resources/sanitization/corpus.json} 里的每一条语料，覆盖三类结论：</p>
 * <ul>
 *   <li><b>命中</b>（{@code mustBeMasked}）：敏感值必须不再出现；</li>
 *   <li><b>保留</b>（{@code mustRemain}）：JSON 键、结构、非敏感上下文必须原样；</li>
 *   <li><b>已知缺口</b>（{@code knownGaps}）：当前实现<b>确实漏掉/误伤</b>的值必须仍在 ——
 *       这一栏故意断言「坏的现状」，将来修好时本测试会红，从而强制同步更新语料与对应 issue。</li>
 * </ul>
 *
 * <p>为什么需要它：脱敏规则改动没有任何端到端断言（此前的单元测试全部 mock 掉规则引擎，
 * 只验证「调用了服务」）。语料是唯一的「输入 → 输出」事实基线。</p>
 *
 * @since 3.4.0
 */
@DisplayName("脱敏回归语料（TA1）")
class SanitizationCorpusRegressionTest {

    private static final String CORPUS_RESOURCE = "sanitization/corpus.json";

    private static Corpus corpus;
    private static SanitizationService service;
    private static SanitizationService optimizedService;

    @BeforeAll
    static void loadCorpusAndService() throws Exception {
        try (InputStream in = new ClassPathResource(CORPUS_RESOURCE).getInputStream()) {
            corpus = new ObjectMapper().readValue(in, Corpus.class);
        }
        assertNotNull(corpus, "语料文件解析失败: " + CORPUS_RESOURCE);
        assertFalse(corpus.cases.isEmpty(), "语料不得为空");
        service = newService(corpus.config, new DefaultSanitizationRuleEngine());
        optimizedService = newService(corpus.config, new OptimizedSanitizationRuleEngine());
    }

    @Test
    @DisplayName("每条语料：命中项必须被打码、保留项必须原样、已知缺口必须仍在")
    void everyCorpusCaseHolds() {
        final List<Executable> assertions = new ArrayList<>();
        for (final Case c : corpus.cases) {
            assertNotNull(c.id, "语料缺少 id");
            assertTrue("request".equals(c.direction) || "response".equals(c.direction),
                    c.id + " 的 direction 只能是 request / response，实际=" + c.direction);

            final String actual = sanitize(c);
            for (final String masked : c.mustBeMasked) {
                assertions.add(() -> assertFalse(actual.contains(masked),
                        c.id + " 期望被打码但仍出现【" + masked + "】；实际输出=" + actual));
            }
            for (final String remain : c.mustRemain) {
                assertions.add(() -> assertTrue(actual.contains(remain),
                        c.id + " 期望保留【" + remain + "】但已消失；实际输出=" + actual));
            }
            for (final String gap : c.knownGaps) {
                assertions.add(() -> assertTrue(actual.contains(gap),
                        c.id + " 的已知缺口【" + gap + "】已不存在 —— 若本次改动修好了它，"
                                + "请更新 corpus.json 的 knownGaps 与对应 issue；实际输出=" + actual));
            }
        }
        org.junit.jupiter.api.Assertions.assertAll(assertions);
    }

    @Test
    @DisplayName("同一份语料在生产实际装配的 Optimized 引擎上同样成立")
    void everyCorpusCaseHoldsOnOptimizedEngine() {
        final List<Executable> assertions = new ArrayList<>();
        for (final Case c : corpus.cases) {
            final String actual = sanitize(optimizedService, c);
            for (final String masked : c.mustBeMasked) {
                assertions.add(() -> assertFalse(actual.contains(masked),
                        c.id + " [Optimized] 期望被打码但仍出现【" + masked + "】；实际输出=" + actual));
            }
            for (final String remain : c.mustRemain) {
                assertions.add(() -> assertTrue(actual.contains(remain),
                        c.id + " [Optimized] 期望保留【" + remain + "】但已消失；实际输出=" + actual));
            }
        }
        org.junit.jupiter.api.Assertions.assertAll(assertions);
    }

    @Test
    @DisplayName("装配自检：语料确实被真实规则引擎执行（防止「全量透传 ⇒ 全部断言空转」）")
    void corpusIsActuallyExercised() {
        final List<SanitizationRule> rules = service.getAllRules().block(Duration.ofSeconds(5));
        assertNotNull(rules, "规则列表读取失败");
        assertEquals(corpus.config.sensitiveWords.size() + corpus.config.piiPatterns.size(), rules.size(),
                "响应方向应加载「敏感词 + PII 模式」各一条规则；语料 config 与实现不一致");

        // 金丝雀：任意一条通路若退化成透传，这里先红。
        assertEquals("{\"msg\":\"********\"}",
                service.sanitizeResponse("{\"msg\":\"password\"}", "application/json")
                        .block(Duration.ofSeconds(5)),
                "敏感词 password 未被替换 ⇒ 服务/引擎装配有问题，语料结论不可信");
        assertEquals("phones: ***********",
                service.sanitizeResponse("phones: 13800138000", "text/plain")
                        .block(Duration.ofSeconds(5)),
                "手机号未被替换 ⇒ 服务/引擎装配有问题，语料结论不可信");
    }

    @Test
    @DisplayName("语料配置与仓库默认配置一致（config/auth/sanitization.yml）")
    @SuppressWarnings("unchecked")
    void corpusConfigMatchesShippedDefaults() throws Exception {
        final Map<String, Object> root;
        try (InputStream in = new ClassPathResource("config/auth/sanitization.yml").getInputStream()) {
            root = new Yaml().load(in);
        }
        final Map<String, Object> response = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>)
                ((Map<String, Object>) root.get("jairouter")).get("security")).get("sanitization")).get("response");

        assertEquals(response.get("pii-patterns"), corpus.config.piiPatterns,
                "语料的 PII 模式必须与 config/auth/sanitization.yml 完全一致（顺序也一致：#561 起为"
                        + "身份证 → 银行卡 → 手机号 → 邮箱，先长后短）");
        assertEquals(response.get("masking-char"), corpus.config.maskingChar, "掩码字符不一致");
        assertEquals(Boolean.TRUE, response.get("preserve-json-structure"), "JSON 结构保留开关应为 true");
        assertTrue(corpus.config.sensitiveWords.containsAll((List<String>) response.get("sensitive-words")),
                "语料的敏感词必须覆盖 config/auth/sanitization.yml 的全部词条");
    }

    @Test
    @DisplayName("PII 规则的 priority 严格跟随语料/yml 的列表顺序（「先长后短」的执行基础）")
    void piiRulePriorityFollowsListOrder() {
        final List<SanitizationRule> rules = service.getAllRules().block(Duration.ofSeconds(5));
        assertNotNull(rules, "规则列表读取失败");
        final List<String> byPriority = rules.stream()
                .filter(rule -> rule.getRuleId().startsWith("response-pii-pattern-"))
                .sorted(java.util.Comparator.comparingInt(SanitizationRule::getPriority))
                .map(SanitizationRule::getPattern)
                .toList();
        assertEquals(corpus.config.piiPatterns, byPriority,
                "引擎按 priority 升序逐条替换，priority 必须随列表索引递增 —— 同组共用同一 priority 时"
                        + "执行顺序取决于 HashMap 迭代顺序，#561 的「身份证 → 银行卡 → 手机号」无法保证");
    }

    @Test
    @DisplayName("掩码不改变长度：替换字符数等于命中字符数")
    void maskingPreservesLength() {
        final String input = "{\"phone\":\"13800138000\"}";
        final String actual = service.sanitizeResponse(input, "application/json").block(Duration.ofSeconds(5));
        assertNotNull(actual);
        assertEquals(input.length(), actual.length(), "掩码必须逐字符替换（长度守恒），实际输出=" + actual);
    }

    // ============ 夹具 ============

    private static String sanitize(final Case c) {
        return sanitize(service, c);
    }

    private static String sanitize(final SanitizationService target, final Case c) {
        final Mono<String> result = "request".equals(c.direction)
                ? target.sanitizeRequest(c.input, c.contentType, "corpus-tester")
                : target.sanitizeResponse(c.input, c.contentType);
        final String actual = result.block(Duration.ofSeconds(5));
        if (actual == null) {
            fail(c.id + " 返回了空结果");
        }
        return actual;
    }

    /**
     * 按仓库默认配置装配真实服务.
     *
     * <p>request 侧刻意留空 —— 生产配置（{@code config/auth/sanitization.yml}）只有 response 块，
     * 这样语料里的 request 用例才能反映真实行为。</p>
     */
    private static SanitizationService newService(final Config cfg, final SanitizationRuleEngine engine) {
        final SecurityProperties properties = new SecurityProperties();

        final SanitizationConfig.ResponseSanitization response = properties.getSanitization().getResponse();
        response.setEnabled(true);
        response.setSensitiveWords(new ArrayList<>(cfg.sensitiveWords));
        response.setPiiPatterns(new ArrayList<>(cfg.piiPatterns));
        response.setMaskingChar(cfg.maskingChar);
        response.setPreserveJsonStructure(cfg.preserveJsonStructure);
        response.setLogSanitization(false);
        response.setFailOnError(false);

        final SanitizationConfig.RequestSanitization request = properties.getSanitization().getRequest();
        request.setSensitiveWords(new ArrayList<>());
        request.setPiiPatterns(new ArrayList<>());
        request.setWhitelistUsers(new ArrayList<>());

        final DefaultSanitizationService sanitizationService =
                new DefaultSanitizationService(engine, properties);
        sanitizationService.initializeRules();
        return sanitizationService;
    }

    // ============ 语料模型 ============

    /** 语料文件根节点. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class Corpus {
        public Config config;
        public List<Case> cases = new ArrayList<>();
    }

    /** 生产默认规则（与 config/auth/sanitization.yml 对齐，由 corpusConfigMatchesShippedDefaults 守着）. */
    static final class Config {
        public List<String> sensitiveWords = new ArrayList<>();
        public List<String> piiPatterns = new ArrayList<>();
        public String maskingChar = "*";
        public boolean preserveJsonStructure = true;
    }

    /** 单条语料. */
    static final class Case {
        public String id;
        public String kind;
        public String direction;
        public String contentType;
        public String input;
        public List<String> mustBeMasked = new ArrayList<>();
        public List<String> mustRemain = new ArrayList<>();
        public List<String> knownGaps = new ArrayList<>();
        public String note;
    }
}
