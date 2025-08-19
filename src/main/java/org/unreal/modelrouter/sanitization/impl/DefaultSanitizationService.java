package org.unreal.modelrouter.sanitization.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.exception.exception.SanitizationException;
import org.unreal.modelrouter.security.model.RuleType;
import org.unreal.modelrouter.security.model.SanitizationRule;
import org.unreal.modelrouter.security.model.SanitizationStrategy;
import org.unreal.modelrouter.sanitization.SanitizationRuleEngine;
import org.unreal.modelrouter.sanitization.SanitizationService;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认脱敏服务实现
 * 提供请求和响应数据的脱敏处理功能，支持动态规则更新和白名单管理
 */
@Slf4j
@Service
public class DefaultSanitizationService implements SanitizationService {
    
    private final SanitizationRuleEngine ruleEngine;
    private final SecurityProperties securityProperties;
    
    /**
     * 脱敏规则存储
     */
    private final ConcurrentMap<String, SanitizationRule> rules = new ConcurrentHashMap<>();
    
    /**
     * 规则ID生成器
     */
    private final AtomicInteger ruleIdGenerator = new AtomicInteger(1);
    
    @Autowired
    public DefaultSanitizationService(SanitizationRuleEngine ruleEngine, SecurityProperties securityProperties) {
        this.ruleEngine = ruleEngine;
        this.securityProperties = securityProperties;
    }
    
    @PostConstruct
    public void initializeRules() {
        log.info("初始化脱敏规则");
        
        // 从配置中加载请求脱敏规则
        loadRequestRules();
        
        // 从配置中加载响应脱敏规则
        loadResponseRules();
        
        // 编译所有规则
        List<SanitizationRule> allRules = new ArrayList<>(rules.values());
        ruleEngine.compileRules(allRules).subscribe(
                unused -> log.info("脱敏规则初始化完成，共加载 {} 条规则", allRules.size()),
                error -> log.error("脱敏规则编译失败", error)
        );
    }
    
    /**
     * 加载请求脱敏规则
     */
    private void loadRequestRules() {
        SecurityProperties.SanitizationConfig.RequestSanitization requestConfig = 
                securityProperties.getSanitization().getRequest();
        
        // 加载敏感词规则
        for (String sensitiveWord : requestConfig.getSensitiveWords()) {
            SanitizationRule rule = SanitizationRule.builder()
                    .ruleId("request-sensitive-word-" + ruleIdGenerator.getAndIncrement())
                    .name("请求敏感词: " + sensitiveWord)
                    .description("请求中的敏感词脱敏")
                    .type(RuleType.SENSITIVE_WORD)
                    .pattern(sensitiveWord)
                    .strategy(SanitizationStrategy.MASK)
                    .enabled(true)
                    .priority(1)
                    .applicableContentTypes(List.of("application/json", "application/xml", "text/plain"))
                    .replacementChar(requestConfig.getMaskingChar())
                    .build();
            
            rules.put(rule.getRuleId(), rule);
        }
        
        // 加载PII模式规则
        for (String piiPattern : requestConfig.getPiiPatterns()) {
            SanitizationRule rule = SanitizationRule.builder()
                    .ruleId("request-pii-pattern-" + ruleIdGenerator.getAndIncrement())
                    .name("请求PII模式: " + piiPattern)
                    .description("请求中的PII数据脱敏")
                    .type(RuleType.PII_PATTERN)
                    .pattern(piiPattern)
                    .strategy(SanitizationStrategy.MASK)
                    .enabled(true)
                    .priority(2)
                    .applicableContentTypes(List.of("application/json", "application/xml", "text/plain"))
                    .replacementChar(requestConfig.getMaskingChar())
                    .build();
            
            rules.put(rule.getRuleId(), rule);
        }
    }
    
    /**
     * 加载响应脱敏规则
     */
    private void loadResponseRules() {
        SecurityProperties.SanitizationConfig.ResponseSanitization responseConfig = 
                securityProperties.getSanitization().getResponse();
        
        // 加载敏感词规则
        for (String sensitiveWord : responseConfig.getSensitiveWords()) {
            SanitizationRule rule = SanitizationRule.builder()
                    .ruleId("response-sensitive-word-" + ruleIdGenerator.getAndIncrement())
                    .name("响应敏感词: " + sensitiveWord)
                    .description("响应中的敏感词脱敏")
                    .type(RuleType.SENSITIVE_WORD)
                    .pattern(sensitiveWord)
                    .strategy(SanitizationStrategy.MASK)
                    .enabled(true)
                    .priority(1)
                    .applicableContentTypes(List.of("application/json", "application/xml", "text/plain"))
                    .replacementChar(responseConfig.getMaskingChar())
                    .build();
            
            rules.put(rule.getRuleId(), rule);
        }
        
        // 加载PII模式规则
        for (String piiPattern : responseConfig.getPiiPatterns()) {
            SanitizationRule rule = SanitizationRule.builder()
                    .ruleId("response-pii-pattern-" + ruleIdGenerator.getAndIncrement())
                    .name("响应PII模式: " + piiPattern)
                    .description("响应中的PII数据脱敏")
                    .type(RuleType.PII_PATTERN)
                    .pattern(piiPattern)
                    .strategy(SanitizationStrategy.MASK)
                    .enabled(true)
                    .priority(2)
                    .applicableContentTypes(List.of("application/json", "application/xml", "text/plain"))
                    .replacementChar(responseConfig.getMaskingChar())
                    .build();
            
            rules.put(rule.getRuleId(), rule);
        }
    }
    
    @Override
    public Mono<String> sanitizeRequest(String content, String contentType, String userId) {
        if (content == null || content.isEmpty()) {
            return Mono.justOrEmpty(content);
        }
        
        // 检查白名单
        if (userId != null) {
            return isUserWhitelisted(userId)
                    .flatMap(isWhitelisted -> {
                        if (isWhitelisted) {
                            log.debug("用户在白名单中，跳过请求脱敏: userId={}", userId);
                            return Mono.just(content);
                        }
                        return performSanitization(content, contentType, "request");
                    });
        }
        
        return performSanitization(content, contentType, "request");
    }
    
    @Override
    public Mono<String> sanitizeResponse(String content, String contentType) {
        if (content == null || content.isEmpty()) {
            return Mono.justOrEmpty(content);
        }
        
        return performSanitization(content, contentType, "response");
    }
    
    /**
     * 执行脱敏处理
     */
    private Mono<String> performSanitization(String content, String contentType, String type) {
        // 获取适用的规则
        List<SanitizationRule> applicableRules = rules.values().stream()
                .filter(rule -> rule.getRuleId().startsWith(type))
                .filter(SanitizationRule::isEnabled)
                .filter(rule -> rule.isApplicableToContentType(contentType))
                .toList();
        
        if (applicableRules.isEmpty()) {
            return Mono.just(content);
        }
        
        return ruleEngine.applySanitizationRules(content, applicableRules, contentType)
                .onErrorMap(throwable -> new SanitizationException(
                        String.format("%s脱敏处理失败", type.equals("request") ? "请求" : "响应"), 
                        throwable, 
                        SanitizationException.SANITIZATION_FAILED));
    }
    
    @Override
    public Mono<Boolean> isUserWhitelisted(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Mono.just(false);
        }
        
        List<String> whitelistUsers = securityProperties.getSanitization().getRequest().getWhitelistUsers();
        boolean isWhitelisted = whitelistUsers.contains(userId);
        
        log.debug("检查用户白名单: userId={}, isWhitelisted={}", userId, isWhitelisted);
        return Mono.just(isWhitelisted);
    }
    
    @Override
    public Mono<List<SanitizationRule>> getAllRules() {
        return Mono.just(new ArrayList<>(rules.values()));
    }
    
    @Override
    public Mono<Void> updateRules(List<SanitizationRule> newRules) {
        return Mono.fromRunnable(() -> {
            log.info("更新脱敏规则，新规则数量: {}", newRules.size());
            
            // 验证所有规则
            for (SanitizationRule rule : newRules) {
                if (rule.getRuleId() == null || rule.getRuleId().trim().isEmpty()) {
                    throw new SanitizationException("规则ID不能为空", SanitizationException.INVALID_SANITIZATION_RULE);
                }
                if (rule.getPattern() == null || rule.getPattern().trim().isEmpty()) {
                    throw new SanitizationException("规则模式不能为空", SanitizationException.INVALID_SANITIZATION_RULE);
                }
                if (rule.getStrategy() == null) {
                    throw new SanitizationException("规则策略不能为空", SanitizationException.INVALID_SANITIZATION_RULE);
                }
            }
            
            // 清除现有规则
            rules.clear();
            
            // 添加新规则
            for (SanitizationRule rule : newRules) {
                rules.put(rule.getRuleId(), rule);
            }
            
            // 重新编译规则
            ruleEngine.compileRules(newRules).subscribe(
                    unused -> log.info("脱敏规则更新完成"),
                    error -> log.error("脱敏规则编译失败", error)
            );
        });
    }
    
    @Override
    public Mono<Void> addRule(SanitizationRule rule) {
        return Mono.fromRunnable(() -> {
            if (rule == null) {
                throw new SanitizationException("规则不能为空", SanitizationException.INVALID_SANITIZATION_RULE);
            }
            
            if (rule.getRuleId() == null || rule.getRuleId().trim().isEmpty()) {
                throw new SanitizationException("规则ID不能为空", SanitizationException.INVALID_SANITIZATION_RULE);
            }
            
            if (rules.containsKey(rule.getRuleId())) {
                throw new SanitizationException("规则ID已存在: " + rule.getRuleId(), 
                        SanitizationException.INVALID_SANITIZATION_RULE);
            }
            
            // 验证规则
            ruleEngine.validateRule(rule).subscribe(isValid -> {
                if (!isValid) {
                    throw new SanitizationException("无效的脱敏规则: " + rule.getRuleId(), 
                            SanitizationException.INVALID_SANITIZATION_RULE);
                }
                
                rules.put(rule.getRuleId(), rule);
                log.info("添加脱敏规则: {}", rule.getRuleId());
                
                // 编译新规则
                ruleEngine.compileRules(List.of(rule)).subscribe(
                        unused -> log.debug("新规则编译完成: {}", rule.getRuleId()),
                        error -> log.error("新规则编译失败: {}", rule.getRuleId(), error)
                );
            });
        });
    }
    
    @Override
    public Mono<Void> removeRule(String ruleId) {
        return Mono.fromRunnable(() -> {
            if (ruleId == null || ruleId.trim().isEmpty()) {
                throw new SanitizationException("规则ID不能为空", SanitizationException.INVALID_SANITIZATION_RULE);
            }
            
            SanitizationRule removedRule = rules.remove(ruleId);
            if (removedRule == null) {
                log.warn("尝试删除不存在的规则: {}", ruleId);
            } else {
                log.info("删除脱敏规则: {}", ruleId);
            }
        });
    }
    
    /**
     * 重新加载配置中的规则
     */
    public Mono<Void> reloadConfigurationRules() {
        return Mono.fromRunnable(() -> {
            log.info("重新加载配置中的脱敏规则");
            
            // 清除现有的配置规则（保留手动添加的规则）
            rules.entrySet().removeIf(entry -> 
                    entry.getKey().startsWith("request-") || entry.getKey().startsWith("response-"));
            
            // 重新加载配置规则
            loadRequestRules();
            loadResponseRules();
            
            // 重新编译所有规则
            List<SanitizationRule> allRules = new ArrayList<>(rules.values());
            ruleEngine.compileRules(allRules).subscribe(
                    unused -> log.info("脱敏规则重新加载完成，共 {} 条规则", allRules.size()),
                    error -> log.error("脱敏规则重新编译失败", error)
            );
        });
    }
    
    /**
     * 获取规则统计信息
     */
    public Mono<RuleStatistics> getRuleStatistics() {
        return Mono.fromCallable(() -> {
            long totalRules = rules.size();
            long enabledRules = rules.values().stream().filter(SanitizationRule::isEnabled).count();
            long requestRules = rules.keySet().stream().filter(id -> id.startsWith("request-")).count();
            long responseRules = rules.keySet().stream().filter(id -> id.startsWith("response-")).count();
            
            return new RuleStatistics(totalRules, enabledRules, requestRules, responseRules);
        });
    }
    
    /**
     * 规则统计信息
     */
    public static class RuleStatistics {
        private final long totalRules;
        private final long enabledRules;
        private final long requestRules;
        private final long responseRules;
        
        public RuleStatistics(long totalRules, long enabledRules, long requestRules, long responseRules) {
            this.totalRules = totalRules;
            this.enabledRules = enabledRules;
            this.requestRules = requestRules;
            this.responseRules = responseRules;
        }
        
        public long getTotalRules() { return totalRules; }
        public long getEnabledRules() { return enabledRules; }
        public long getRequestRules() { return requestRules; }
        public long getResponseRules() { return responseRules; }
        
        @Override
        public String toString() {
            return String.format("RuleStatistics{total=%d, enabled=%d, request=%d, response=%d}", 
                    totalRules, enabledRules, requestRules, responseRules);
        }
    }
}