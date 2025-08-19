package org.unreal.modelrouter.security.sanitization.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.security.exception.SanitizationException;
import org.unreal.modelrouter.security.model.RuleType;
import org.unreal.modelrouter.security.model.SanitizationRule;
import org.unreal.modelrouter.security.model.SanitizationStrategy;
import org.unreal.modelrouter.security.sanitization.SanitizationRuleEngine;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * 优化的脱敏规则引擎实现
 * 提供高性能的脱敏处理，包括：
 * 1. 编译后正则表达式的缓存
 * 2. 脱敏规则的优先级排序
 * 3. 大文件的流式脱敏处理
 * 4. 并行处理支持
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jairouter.security.sanitization.optimized", havingValue = "true", matchIfMissing = true)
public class OptimizedSanitizationRuleEngine implements SanitizationRuleEngine {
    
    /**
     * 编译后的正则表达式缓存
     */
    private final Map<String, CompiledRule> compiledRules = new ConcurrentHashMap<>();
    
    /**
     * 规则匹配统计
     */
    private final Map<String, Long> ruleMatchCounts = new ConcurrentHashMap<>();
    
    /**
     * 按内容类型分组的规则缓存
     */
    private final Map<String, List<CompiledRule>> rulesByContentType = new ConcurrentHashMap<>();
    
    /**
     * 大文件处理的阈值（字节）
     */
    private static final int LARGE_CONTENT_THRESHOLD = 1024 * 1024; // 1MB
    
    /**
     * 流式处理的缓冲区大小
     */
    private static final int STREAM_BUFFER_SIZE = 8192; // 8KB
    
    /**
     * 默认掩码字符
     */
    private static final String DEFAULT_MASK_CHAR = "*";
    
    /**
     * 默认哈希算法
     */
    private static final String DEFAULT_HASH_ALGORITHM = "SHA-256";
    
    @Override
    public Mono<String> applySanitizationRules(String content, List<SanitizationRule> rules, String contentType) {
        if (content == null || content.isEmpty()) {
            return Mono.justOrEmpty(content);
        }
        
        if (rules == null || rules.isEmpty()) {
            return Mono.just(content);
        }
        
        // 检查是否需要流式处理
        if (content.length() > LARGE_CONTENT_THRESHOLD) {
            return processLargeContentStreaming(content, rules, contentType);
        }
        
        return processContentInMemory(content, rules, contentType);
    }
    
    /**
     * 内存中处理内容
     */
    private Mono<String> processContentInMemory(String content, List<SanitizationRule> rules, String contentType) {
        return Mono.fromCallable(() -> {
            List<CompiledRule> applicableRules = getApplicableRules(rules, contentType);
            
            String sanitizedContent = content;
            for (CompiledRule compiledRule : applicableRules) {
                try {
                    sanitizedContent = applyCompiledRule(sanitizedContent, compiledRule);
                } catch (Exception e) {
                    log.error("应用脱敏规则失败: ruleId={}, error={}", 
                             compiledRule.getRule().getRuleId(), e.getMessage(), e);
                }
            }
            
            return sanitizedContent;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(throwable -> new SanitizationException("脱敏处理失败", throwable, SanitizationException.SANITIZATION_FAILED));
    }
    
    /**
     * 流式处理大文件内容
     */
    private Mono<String> processLargeContentStreaming(String content, List<SanitizationRule> rules, String contentType) {
        return Mono.fromCallable(() -> {
            List<CompiledRule> applicableRules = getApplicableRules(rules, contentType);
            
            if (applicableRules.isEmpty()) {
                return content;
            }
            
            StringBuilder result = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
                char[] buffer = new char[STREAM_BUFFER_SIZE];
                int charsRead;
                
                while ((charsRead = reader.read(buffer)) != -1) {
                    String chunk = new String(buffer, 0, charsRead);
                    
                    // 对每个块应用脱敏规则
                    for (CompiledRule compiledRule : applicableRules) {
                        chunk = applyCompiledRule(chunk, compiledRule);
                    }
                    
                    result.append(chunk);
                }
            }
            
            return result.toString();
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(throwable -> new SanitizationException("流式脱敏处理失败", throwable, SanitizationException.SANITIZATION_FAILED));
    }
    
    /**
     * 获取适用的编译规则列表
     */
    private List<CompiledRule> getApplicableRules(List<SanitizationRule> rules, String contentType) {
        String cacheKey = contentType + ":" + rules.stream()
                .map(SanitizationRule::getRuleId)
                .sorted()
                .collect(Collectors.joining(","));
        
        return rulesByContentType.computeIfAbsent(cacheKey, k -> {
            return rules.stream()
                    .filter(SanitizationRule::isEnabled)
                    .filter(rule -> rule.isApplicableToContentType(contentType))
                    .sorted(Comparator.comparingInt(SanitizationRule::getPriority))
                    .map(this::getOrCompileRule)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        });
    }
    
    /**
     * 获取或编译规则
     */
    private CompiledRule getOrCompileRule(SanitizationRule rule) {
        return compiledRules.computeIfAbsent(rule.getRuleId(), k -> {
            try {
                String pattern = buildPattern(rule);
                Pattern compiledPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                return new CompiledRule(rule, compiledPattern);
            } catch (PatternSyntaxException e) {
                log.error("编译正则表达式失败: ruleId={}, pattern={}, error={}", 
                         rule.getRuleId(), rule.getPattern(), e.getMessage());
                return null;
            }
        });
    }
    
    /**
     * 应用编译后的规则
     */
    private String applyCompiledRule(String content, CompiledRule compiledRule) {
        java.util.regex.Matcher matcher = compiledRule.getPattern().matcher(content);
        if (!matcher.find()) {
            return content;
        }
        
        // 记录匹配统计
        incrementMatchCount(compiledRule.getRule().getRuleId());
        
        // 根据策略进行替换
        return switch (compiledRule.getRule().getStrategy()) {
            case MASK -> applyMaskStrategy(content, matcher, compiledRule.getRule());
            case REPLACE -> applyReplaceStrategy(content, matcher, compiledRule.getRule());
            case REMOVE -> applyRemoveStrategy(content, matcher);
            case HASH -> applyHashStrategy(content, matcher);
        };
    }
    
    /**
     * 应用掩码策略
     */
    private String applyMaskStrategy(String content, java.util.regex.Matcher matcher, SanitizationRule rule) {
        String maskChar = rule.getReplacementChar() != null ? rule.getReplacementChar() : DEFAULT_MASK_CHAR;
        
        StringBuffer result = new StringBuffer();
        matcher.reset();
        
        while (matcher.find()) {
            String matched = matcher.group();
            String masked = maskChar.repeat(matched.length());
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(masked));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * 应用替换策略
     */
    private String applyReplaceStrategy(String content, java.util.regex.Matcher matcher, SanitizationRule rule) {
        String replacement = rule.getReplacementText() != null ? rule.getReplacementText() : "[REDACTED]";
        return matcher.replaceAll(java.util.regex.Matcher.quoteReplacement(replacement));
    }
    
    /**
     * 应用删除策略
     */
    private String applyRemoveStrategy(String content, java.util.regex.Matcher matcher) {
        return matcher.replaceAll("");
    }
    
    /**
     * 应用哈希策略
     */
    private String applyHashStrategy(String content, java.util.regex.Matcher matcher) {
        StringBuffer result = new StringBuffer();
        matcher.reset();
        
        while (matcher.find()) {
            String matched = matcher.group();
            String hashed = generateHash(matched);
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(hashed));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * 生成哈希值
     */
    private String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DEFAULT_HASH_ALGORITHM);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            // 返回前8位作为简短哈希
            return "[HASH:" + hexString.substring(0, 8).toUpperCase() + "]";
        } catch (NoSuchAlgorithmException e) {
            log.error("哈希算法不可用: {}", DEFAULT_HASH_ALGORITHM, e);
            return "[HASH:ERROR]";
        }
    }
    
    /**
     * 根据规则类型构建正则表达式模式
     */
    private String buildPattern(SanitizationRule rule) {
        return switch (rule.getType()) {
            case SENSITIVE_WORD -> "\\b" + Pattern.quote(rule.getPattern()) + "\\b";
            case PII_PATTERN, CUSTOM_REGEX -> rule.getPattern();
        };
    }
    
    /**
     * 增加匹配计数
     */
    private void incrementMatchCount(String ruleId) {
        ruleMatchCounts.merge(ruleId, 1L, Long::sum);
    }
    
    @Override
    public Mono<Boolean> validateRule(SanitizationRule rule) {
        return Mono.fromCallable(() -> {
            if (rule == null) {
                return false;
            }
            
            if (rule.getRuleId() == null || rule.getRuleId().trim().isEmpty()) {
                return false;
            }
            
            if (rule.getPattern() == null || rule.getPattern().trim().isEmpty()) {
                return false;
            }
            
            if (rule.getStrategy() == null) {
                return false;
            }
            
            // 验证正则表达式语法
            try {
                String pattern = buildPattern(rule);
                Pattern.compile(pattern);
                return true;
            } catch (PatternSyntaxException e) {
                log.warn("规则验证失败，正则表达式语法错误: ruleId={}, pattern={}, error={}", 
                        rule.getRuleId(), rule.getPattern(), e.getMessage());
                return false;
            }
        });
    }
    
    @Override
    public Mono<Void> compileRules(List<SanitizationRule> rules) {
        return Flux.fromIterable(rules)
                .parallel()
                .runOn(Schedulers.parallel())
                .map(this::compileRule)
                .filter(Objects::nonNull)
                .sequential()
                .collectList()
                .doOnNext(compiledRulesList -> {
                    log.info("脱敏规则编译完成，成功编译 {} 个规则", compiledRulesList.size());
                    // 清除内容类型缓存，强制重新计算
                    rulesByContentType.clear();
                })
                .then();
    }
    
    /**
     * 编译单个规则
     */
    private CompiledRule compileRule(SanitizationRule rule) {
        try {
            String pattern = buildPattern(rule);
            Pattern compiledPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            CompiledRule compiledRule = new CompiledRule(rule, compiledPattern);
            compiledRules.put(rule.getRuleId(), compiledRule);
            return compiledRule;
        } catch (PatternSyntaxException e) {
            log.error("编译规则失败: ruleId={}, pattern={}, error={}", 
                     rule.getRuleId(), rule.getPattern(), e.getMessage());
            return null;
        }
    }
    
    @Override
    public Mono<Long> getRuleMatchCount(String ruleId) {
        return Mono.fromCallable(() -> ruleMatchCounts.getOrDefault(ruleId, 0L));
    }
    
    /**
     * 批量处理多个内容
     */
    public Flux<String> applySanitizationRulesBatch(List<String> contents, List<SanitizationRule> rules, String contentType) {
        return Flux.fromIterable(contents)
                .parallel()
                .runOn(Schedulers.parallel())
                .flatMap(content -> applySanitizationRules(content, rules, contentType))
                .sequential();
    }
    
    /**
     * 清除所有缓存
     */
    public void clearAllCaches() {
        compiledRules.clear();
        rulesByContentType.clear();
        ruleMatchCounts.clear();
        log.info("已清除所有脱敏规则缓存");
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStatistics getCacheStatistics() {
        return new CacheStatistics(
                compiledRules.size(),
                rulesByContentType.size(),
                ruleMatchCounts.values().stream().mapToLong(Long::longValue).sum()
        );
    }
    
    /**
     * 编译后的规则包装类
     */
    private static class CompiledRule {
        private final SanitizationRule rule;
        private final Pattern pattern;
        
        public CompiledRule(SanitizationRule rule, Pattern pattern) {
            this.rule = rule;
            this.pattern = pattern;
        }
        
        public SanitizationRule getRule() {
            return rule;
        }
        
        public Pattern getPattern() {
            return pattern;
        }
    }
    
    /**
     * 缓存统计信息
     */
    public static class CacheStatistics {
        private final int compiledRulesCount;
        private final int contentTypeCacheCount;
        private final long totalMatches;
        
        public CacheStatistics(int compiledRulesCount, int contentTypeCacheCount, long totalMatches) {
            this.compiledRulesCount = compiledRulesCount;
            this.contentTypeCacheCount = contentTypeCacheCount;
            this.totalMatches = totalMatches;
        }
        
        public int getCompiledRulesCount() { return compiledRulesCount; }
        public int getContentTypeCacheCount() { return contentTypeCacheCount; }
        public long getTotalMatches() { return totalMatches; }
        
        @Override
        public String toString() {
            return String.format("CacheStatistics{compiledRules=%d, contentTypeCache=%d, totalMatches=%d}", 
                    compiledRulesCount, contentTypeCacheCount, totalMatches);
        }
    }
}