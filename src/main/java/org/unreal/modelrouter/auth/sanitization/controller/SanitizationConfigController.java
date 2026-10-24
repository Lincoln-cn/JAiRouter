package org.unreal.modelrouter.auth.sanitization.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import org.unreal.modelrouter.auth.sanitization.impl.DefaultSanitizationService;
import org.unreal.modelrouter.auth.sanitization.persistence.SanitizationConfigStore;
import org.unreal.modelrouter.auth.security.config.DataMaskingScope;
import org.unreal.modelrouter.auth.security.config.properties.SanitizationConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.RuleType;
import org.unreal.modelrouter.auth.security.model.SanitizationRule;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.exception.ApiException;
import org.unreal.modelrouter.common.util.SafeRegexValidator;
import org.unreal.modelrouter.common.util.SecurityUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * PII / 数据脱敏运行时管理控制器（全链路响应式，禁止 block()）.
 *
 * <p>管理面聚焦「聊天调用历史 / 日志 / 追踪记录」中的 PII：保存时先把配置快照落库
 * （{@link SanitizationConfigStore}），落库成功后同步 {@link DefaultSanitizationService}
 * 内存规则库，SUMMARY 记录链路立即生效；进程重启时按「DB 优先、无行回落 yml」装载，
 * 因此管理员保存的配置不会静默回退。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/config/sanitization")
@Tag(name = "PII脱敏配置管理", description = "聊天日志/调用历史 PII 规则查询、热改与试脱敏")
public class SanitizationConfigController {

    private final SecurityProperties securityProperties;
    private final SanitizationService sanitizationService;
    private final DefaultSanitizationService defaultSanitizationService;
    private final SanitizationConfigStore sanitizationConfigStore;

    public SanitizationConfigController(final SecurityProperties securityProperties,
                                        final SanitizationService sanitizationService,
                                        final DefaultSanitizationService defaultSanitizationService,
                                        final SanitizationConfigStore sanitizationConfigStore) {
        this.securityProperties = securityProperties;
        this.sanitizationService = sanitizationService;
        this.defaultSanitizationService = defaultSanitizationService;
        this.sanitizationConfigStore = sanitizationConfigStore;
    }

    @GetMapping
    @Operation(summary = "查询脱敏配置快照",
            description = "返回 response 子配置、当前生效规则数与记录脱敏说明")
    public Mono<ResponseEntity<RouterResponse<Map<String, Object>>>> getConfig() {
        return sanitizationService.getAllRules()
                .defaultIfEmpty(List.of())
                .map(rules -> ResponseEntity.ok(RouterResponse.success(buildSnapshot(rules))));
    }

    @PutMapping
    @Operation(summary = "热更新脱敏配置",
            description = "部分更新 response，先落库再热重建 DefaultSanitizationService 规则")
    public Mono<ResponseEntity<RouterResponse<Map<String, Object>>>> updateConfig(
            @RequestBody final SanitizationUpdateRequest request) {
        if (request == null) {
            return Mono.error(ApiException.of(ErrorCode.INVALID_REQUEST.code(), "请求体不能为空"));
        }
        if (request.response == null) {
            return Mono.error(ApiException.of(ErrorCode.INVALID_REQUEST.code(), "需要指定 response 子配置"));
        }

        // 先改副本再整体替换：校验失败时内存配置一个字段都不会被改动（旧实现会留下半更新）。
        final SanitizationConfig config = securityProperties.getSanitization();
        final SanitizationConfig.ResponseSanitization responseBefore = copyResponse(config.getResponse());
        final SanitizationConfig.ResponseSanitization responseAfter = copyResponse(responseBefore);

        final List<String> errors = new ArrayList<>();
        applySubConfig(request.response, responseAfter, "response", errors);
        if (!errors.isEmpty()) {
            return Mono.error(ApiException.of(ErrorCode.INVALID_REQUEST.code(), String.join("; ", errors)));
        }

        config.setResponse(responseAfter);

        return SecurityUtils.getCurrentUserIdReactive()
                .flatMap(actor -> persistAndRebuild(actor, responseBefore));
    }

    /**
     * 先落库、后重建规则；落库失败时把内存配置回滚到本次更新前的快照。
     *
     * <p>顺序不可颠倒：先重建规则再落库时，一旦落库失败，内存里的规则已经按新配置编译过，
     * 而重启后又回到旧配置 —— 正是本缺陷要消除的「运行态与持久态不一致」。</p>
     */
    private Mono<ResponseEntity<RouterResponse<Map<String, Object>>>> persistAndRebuild(
            final String actor,
            final SanitizationConfig.ResponseSanitization responseBefore) {
        return Mono.<Void>fromRunnable(() -> sanitizationConfigStore.persist(
                        securityProperties, actor))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> {
                    restore(securityProperties.getSanitization(), responseBefore);
                    log.error("脱敏配置持久化失败，已回滚内存配置：response=true", error);
                    return Mono.error(ApiException.of(ErrorCode.INTERNAL_ERROR.code(),
                            "脱敏配置持久化失败，内存配置已回滚"));
                })
                // 必须 defer：rebuildRulesFromProperties() 是立即执行的调用，直接当 then(...) 的实参会在
                // 组装链时就跑完 —— 那时 persist 还没执行，等于「先重建规则、后落库」，落库失败也拦不住。
                .then(Mono.defer(defaultSanitizationService::rebuildRulesFromProperties))
                .then(sanitizationService.getAllRules().defaultIfEmpty(List.of()))
                .map(rules -> {
                    log.info("脱敏配置已落库并热重建规则：response=true");
                    return ResponseEntity.ok(RouterResponse.success(buildSnapshot(rules), "脱敏配置已更新"));
                });
    }

    private static void restore(final SanitizationConfig config,
                                final SanitizationConfig.ResponseSanitization responseBefore) {
        config.setResponse(responseBefore);
    }

    private static SanitizationConfig.ResponseSanitization copyResponse(
            final SanitizationConfig.ResponseSanitization source) {
        final SanitizationConfig.ResponseSanitization copy = new SanitizationConfig.ResponseSanitization();
        copy.setEnabled(source.isEnabled());
        copy.setSensitiveWords(copyList(source.getSensitiveWords()));
        copy.setPiiPatterns(copyList(source.getPiiPatterns()));
        copy.setMaskingChar(source.getMaskingChar());
        copy.setLogSanitization(source.isLogSanitization());
        copy.setFailOnError(source.isFailOnError());
        copy.setPreserveJsonStructure(source.isPreserveJsonStructure());
        // 这三个字段不在 SanitizationSubUpdate 的可改面里，但整体替换 response 时必须原样带过去，
        // 否则任何一次热更新都会把 #560 的 scope / #594 的缓冲上限 / SSE 开关重置成默认值。
        copy.setScope(copyScope(source.getScope()));
        copy.setMaxBufferedBytes(source.getMaxBufferedBytes());
        copy.setSanitizeSse(source.isSanitizeSse());
        return copy;
    }

    private static SanitizationConfig.Scope copyScope(final SanitizationConfig.Scope source) {
        final SanitizationConfig.Scope copy = new SanitizationConfig.Scope();
        if (source != null) {
            copy.setMode(source.getMode());
            copy.setPatterns(copyList(source.getPatterns()));
        }
        return copy;
    }

    private static List<String> copyList(final List<String> source) {
        return source == null ? new ArrayList<>() : new ArrayList<>(source);
    }

    @PostMapping("/scope/preview")
    @Operation(summary = "预览路径是否在脱敏生效范围内",
            description = "按当前 response.scope 配置判定给定路径，用于控制台只读回显与排障")
    public Mono<ResponseEntity<RouterResponse<Map<String, Object>>>> previewScope(
            @RequestBody final ScopePreviewRequest request) {
        if (request == null || request.path == null || request.path.isBlank()) {
            return Mono.error(ApiException.of(ErrorCode.INVALID_REQUEST.code(), "path 不能为空"));
        }
        final String path = request.path.trim();
        final DataMaskingScope scope = DataMaskingScope.from(securityProperties.getSanitization().getResponse());
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", path);
        payload.put("mode", scope.getMode());
        payload.put("shouldSanitize", scope.shouldSanitize(path));
        payload.put("matchedPattern", scope.matchedPattern(path));
        payload.put("usingBuiltInDefaults", scope.isUsingBuiltInDefaults());
        payload.put("warning", scope.warning() == null ? "" : scope.warning());
        return Mono.just(ResponseEntity.ok(RouterResponse.success(payload)));
    }

    @GetMapping("/rules")
    @Operation(summary = "列出当前脱敏规则")
    public Mono<ResponseEntity<RouterResponse<List<Map<String, Object>>>>> getRules() {        return sanitizationService.getAllRules()
                .defaultIfEmpty(List.of())
                .map(rules -> {
                    final List<Map<String, Object>> payload = new ArrayList<>();
                    for (final SanitizationRule rule : rules) {
                        payload.add(ruleToMap(rule));
                    }
                    return ResponseEntity.ok(RouterResponse.success(payload));
                });
    }

    @PostMapping("/test")
    @Operation(summary = "试脱敏",
            description = "用当前生效规则对样例文本试跑脱敏，返回 before/after/matched 规则")
    public Mono<ResponseEntity<RouterResponse<Map<String, Object>>>> testSanitization(
            @RequestBody final SanitizationTestRequest request) {
        if (request == null || request.sample == null || request.sample.isBlank()) {
            return Mono.error(ApiException.of(ErrorCode.INVALID_REQUEST.code(), "sample 不能为空"));
        }
        final String sample = request.sample;
        final String resolvedType = resolveDryRunContentType(sample, request.contentType);

        return sanitizationService.getAllRules()
                .defaultIfEmpty(List.of())
                .flatMap(rules -> {
                    final List<String> matched = matchRules(rules, sample);
                    return sanitizationService.sanitizeForStorage(sample, resolvedType)
                            .defaultIfEmpty(sample)
                            .map(after -> {
                                final Map<String, Object> result = new LinkedHashMap<>();
                                result.put("before", sample);
                                result.put("after", after);
                                result.put("matchedRuleIds", matched);
                                result.put("contentType", resolvedType);
                                return ResponseEntity.ok(RouterResponse.success(result));
                            });
                });
    }

    /**
     * 试脱敏内容类型解析.
     *
     * <p>控制台常把聊天原文当样例粘贴，contentType 默认/误填 application/json 时，
     * preserveJson 路径不会对非 JSON 正文掩码，导致「试脱敏显示原文」。
     * 样例不像 JSON 时按 text/plain 脱敏；真 JSON 样例仍走 application/json。</p>
     */
    static String resolveDryRunContentType(final String sample, final String requested) {
        final String req = requested == null || requested.isBlank() ? null : requested.trim();
        if (req != null && !req.startsWith("application/json")) {
            return req;
        }
        return looksLikeJson(sample) ? "application/json" : "text/plain";
    }

    private static boolean looksLikeJson(final String sample) {
        if (sample == null) {
            return false;
        }
        final String s = sample.trim();
        return (s.startsWith("{") && s.endsWith("}")) || (s.startsWith("[") && s.endsWith("]"));
    }

    private static List<String> matchRules(final List<SanitizationRule> rules, final String sample) {
        final List<String> matched = new ArrayList<>();
        if (rules == null) {
            return matched;
        }
        for (final SanitizationRule rule : rules) {
            if (!rule.isEnabled() || rule.getPattern() == null || rule.getPattern().isBlank()) {
                continue;
            }
            if (rule.getType() == RuleType.PII_PATTERN || rule.getType() == RuleType.CUSTOM_REGEX) {
                final Pattern compiled = SafeRegexValidator.compileOrNull(rule.getPattern());
                if (compiled != null && compiled.matcher(sample).find()) {
                    matched.add(rule.getRuleId());
                }
            } else if (sample.toLowerCase().contains(rule.getPattern().toLowerCase())) {
                matched.add(rule.getRuleId());
            }
        }
        return matched;
    }

    private static Map<String, Object> ruleToMap(final SanitizationRule rule) {
        final Map<String, Object> item = new LinkedHashMap<>();
        item.put("ruleId", rule.getRuleId());
        item.put("name", rule.getName());
        item.put("description", rule.getDescription());
        item.put("type", rule.getType() != null ? rule.getType().name() : null);
        item.put("pattern", rule.getPattern());
        item.put("strategy", rule.getStrategy() != null ? rule.getStrategy().name() : null);
        item.put("enabled", rule.isEnabled());
        item.put("priority", rule.getPriority());
        item.put("replacementChar", rule.getReplacementChar());
        return item;
    }

    private Map<String, Object> buildSnapshot(final List<SanitizationRule> rules) {
        final SanitizationConfig config = securityProperties.getSanitization();
        final Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("response", responseToMap(config.getResponse()));
        snap.put("ruleCount", rules == null ? 0 : rules.size());
        snap.put("primaryUseCase", "chat-call-history-logs-and-user-data-records");
        snap.put("gatewayResponseFilter", "optional-default-off-management-and-ai-paths-excluded");
        return snap;
    }

    private static Map<String, Object> responseToMap(final SanitizationConfig.ResponseSanitization res) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", res.isEnabled());
        map.put("piiPatterns", res.getPiiPatterns());
        map.put("sensitiveWords", res.getSensitiveWords());
        map.put("maskingChar", res.getMaskingChar());
        map.put("logSanitization", res.isLogSanitization());
        map.put("failOnError", res.isFailOnError());
        map.put("preserveJsonStructure", res.isPreserveJsonStructure());
        final DataMaskingScope scope = DataMaskingScope.from(res);
        map.put("scope", scope.describe());
        map.put("scopeSource", scope.isUsingBuiltInDefaults() ? "builtin-defaults" : "configuration");
        return map;
    }

    private static void applySubConfig(final SanitizationSubUpdate update,
                                       final SanitizationConfig.ResponseSanitization target,
                                       final String side,
                                       final List<String> errors) {
        final String mask = update.maskingChar;
        if (mask != null && (mask.isBlank() || mask.length() > 5)) {
            errors.add(side + ".maskingChar 长度必须在 1-5");
        }
        if (update.piiPatterns != null) {
            for (final String pattern : update.piiPatterns) {
                final String validationError = SafeRegexValidator.validateUserPattern(pattern);
                if (validationError != null) {
                    errors.add(side + ".piiPatterns " + validationError
                            + (pattern != null && !pattern.isBlank() && pattern.length() <= 80
                                    ? ": " + pattern
                                    : ""));
                    continue;
                }
                SafeRegexValidator.compileUserPattern(pattern);
            }
        }

        if (update.enabled != null) {
            target.setEnabled(update.enabled);
        }
        if (update.piiPatterns != null) {
            target.setPiiPatterns(new ArrayList<>(update.piiPatterns));
        }
        if (update.sensitiveWords != null) {
            target.setSensitiveWords(new ArrayList<>(update.sensitiveWords));
        }
        if (mask != null) {
            target.setMaskingChar(mask);
        }
        if (update.logSanitization != null) {
            target.setLogSanitization(update.logSanitization);
        }
        if (update.failOnError != null) {
            target.setFailOnError(update.failOnError);
        }
        if (update.preserveJsonStructure != null) {
            target.setPreserveJsonStructure(update.preserveJsonStructure);
        }
    }

    /** 配置热更请求体（部分更新） */
    public static class SanitizationUpdateRequest {
        public SanitizationSubUpdate response;
    }

    /** response 子配置更新字段 */
    public static class SanitizationSubUpdate {
        public Boolean enabled;
        public List<String> piiPatterns;
        public List<String> sensitiveWords;
        public String maskingChar;
        public Boolean logSanitization;
        public Boolean failOnError;
        public Boolean preserveJsonStructure;
    }

    /** 试脱敏请求 */
    public static class SanitizationTestRequest {
        public String sample;
        public String contentType;
    }

    /** 生效范围预览请求 */
    public static class ScopePreviewRequest {
        public String path;
    }
}
