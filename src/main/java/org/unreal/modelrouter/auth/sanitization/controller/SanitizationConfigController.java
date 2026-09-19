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
import org.unreal.modelrouter.auth.security.config.properties.SanitizationConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.RuleType;
import org.unreal.modelrouter.auth.security.model.SanitizationRule;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * PII / 数据脱敏运行时管理控制器（全链路响应式，禁止 block()）.
 *
 * <p>管理面聚焦「聊天调用历史 / 日志 / 追踪记录」中的 PII：规则热改后同步到
 * {@link DefaultSanitizationService} 内存规则库，SUMMARY 记录链路立即生效。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/config/sanitization")
@Tag(name = "PII脱敏配置管理", description = "聊天日志/调用历史 PII 规则查询、热改与试脱敏")
public class SanitizationConfigController {

    private final SecurityProperties securityProperties;
    private final SanitizationService sanitizationService;
    private final DefaultSanitizationService defaultSanitizationService;

    public SanitizationConfigController(final SecurityProperties securityProperties,
                                        final SanitizationService sanitizationService,
                                        final DefaultSanitizationService defaultSanitizationService) {
        this.securityProperties = securityProperties;
        this.sanitizationService = sanitizationService;
        this.defaultSanitizationService = defaultSanitizationService;
    }

    @GetMapping
    @Operation(summary = "查询脱敏配置快照",
            description = "返回 request/response 子配置、当前生效规则数与记录脱敏说明")
    public Mono<ResponseEntity<RouterResponse<Map<String, Object>>>> getConfig() {
        return sanitizationService.getAllRules()
                .defaultIfEmpty(List.of())
                .map(rules -> ResponseEntity.ok(RouterResponse.success(buildSnapshot(rules))));
    }

    @PutMapping
    @Operation(summary = "热更新脱敏配置",
            description = "部分更新 request/response，并热重建 DefaultSanitizationService 规则")
    public Mono<ResponseEntity<RouterResponse<Map<String, Object>>>> updateConfig(
            @RequestBody final SanitizationUpdateRequest request) {
        if (request == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(RouterResponse.error("请求体不能为空", "INVALID_REQUEST")));
        }
        if (request.request == null && request.response == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(RouterResponse.error("至少需要指定 request 或 response 子配置", "INVALID_REQUEST")));
        }

        final List<String> errors = new ArrayList<>();
        if (request.request != null) {
            applySubConfig(request.request, securityProperties.getSanitization().getRequest(), "request", errors);
        }
        if (request.response != null) {
            applySubConfig(request.response, securityProperties.getSanitization().getResponse(), "response", errors);
        }
        if (!errors.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(RouterResponse.error(String.join("; ", errors), "INVALID_REQUEST")));
        }

        return defaultSanitizationService.rebuildRulesFromProperties()
                .then(sanitizationService.getAllRules().defaultIfEmpty(List.of()))
                .map(rules -> {
                    log.info("脱敏配置已热更新并重建规则");
                    return ResponseEntity.ok(RouterResponse.success(buildSnapshot(rules), "脱敏配置已更新"));
                });
    }

    @GetMapping("/rules")
    @Operation(summary = "列出当前脱敏规则")
    public Mono<ResponseEntity<RouterResponse<List<Map<String, Object>>>>> getRules() {
        return sanitizationService.getAllRules()
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
            return Mono.just(ResponseEntity.badRequest()
                    .body(RouterResponse.error("sample 不能为空", "INVALID_REQUEST")));
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
                try {
                    if (Pattern.compile(rule.getPattern()).matcher(sample).find()) {
                        matched.add(rule.getRuleId());
                    }
                } catch (PatternSyntaxException ignored) {
                    // 无效正则在试跑中忽略
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
        snap.put("request", subToMap(config.getRequest()));
        snap.put("response", responseToMap(config.getResponse()));
        snap.put("ruleCount", rules == null ? 0 : rules.size());
        snap.put("primaryUseCase", "chat-call-history-logs-and-user-data-records");
        snap.put("gatewayResponseFilter", "optional-default-off-management-and-ai-paths-excluded");
        return snap;
    }

    private static Map<String, Object> subToMap(final SanitizationConfig.RequestSanitization req) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", req.isEnabled());
        map.put("piiPatterns", req.getPiiPatterns());
        map.put("sensitiveWords", req.getSensitiveWords());
        map.put("maskingChar", req.getMaskingChar());
        map.put("logSanitization", req.isLogSanitization());
        map.put("failOnError", req.isFailOnError());
        map.put("whitelistUsers", req.getWhitelistUsers());
        return map;
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
        return map;
    }

    private static void applySubConfig(final SanitizationSubUpdate update,
                                       final Object target,
                                       final String side,
                                       final List<String> errors) {
        final String mask = update.maskingChar;
        if (mask != null && (mask.isBlank() || mask.length() > 5)) {
            errors.add(side + ".maskingChar 长度必须在 1-5");
        }
        if (update.piiPatterns != null) {
            for (final String pattern : update.piiPatterns) {
                if (pattern == null || pattern.isBlank()) {
                    errors.add(side + ".piiPatterns 存在空模式");
                    continue;
                }
                try {
                    Pattern.compile(pattern);
                } catch (PatternSyntaxException e) {
                    errors.add(side + ".piiPatterns 非法正则: " + pattern);
                }
            }
        }

        if (target instanceof SanitizationConfig.RequestSanitization req) {
            if (update.enabled != null) {
                req.setEnabled(update.enabled);
            }
            if (update.piiPatterns != null) {
                req.setPiiPatterns(new ArrayList<>(update.piiPatterns));
            }
            if (update.sensitiveWords != null) {
                req.setSensitiveWords(new ArrayList<>(update.sensitiveWords));
            }
            if (mask != null) {
                req.setMaskingChar(mask);
            }
            if (update.logSanitization != null) {
                req.setLogSanitization(update.logSanitization);
            }
            if (update.failOnError != null) {
                req.setFailOnError(update.failOnError);
            }
            if (update.whitelistUsers != null) {
                req.setWhitelistUsers(new ArrayList<>(update.whitelistUsers));
            }
        } else if (target instanceof SanitizationConfig.ResponseSanitization res) {
            if (update.enabled != null) {
                res.setEnabled(update.enabled);
            }
            if (update.piiPatterns != null) {
                res.setPiiPatterns(new ArrayList<>(update.piiPatterns));
            }
            if (update.sensitiveWords != null) {
                res.setSensitiveWords(new ArrayList<>(update.sensitiveWords));
            }
            if (mask != null) {
                res.setMaskingChar(mask);
            }
            if (update.logSanitization != null) {
                res.setLogSanitization(update.logSanitization);
            }
            if (update.failOnError != null) {
                res.setFailOnError(update.failOnError);
            }
            if (update.preserveJsonStructure != null) {
                res.setPreserveJsonStructure(update.preserveJsonStructure);
            }
        }
    }

    /** 配置热更请求体（部分更新） */
    public static class SanitizationUpdateRequest {
        public SanitizationSubUpdate request;
        public SanitizationSubUpdate response;
    }

    /** request/response 子配置更新字段 */
    public static class SanitizationSubUpdate {
        public Boolean enabled;
        public List<String> piiPatterns;
        public List<String> sensitiveWords;
        public String maskingChar;
        public Boolean logSanitization;
        public Boolean failOnError;
        public Boolean preserveJsonStructure;
        public List<String> whitelistUsers;
    }

    /** 试脱敏请求 */
    public static class SanitizationTestRequest {
        public String sample;
        public String contentType;
    }
}
