package org.unreal.modelrouter.sanitization.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.exception.SanitizationException;
import org.unreal.modelrouter.sanitization.SanitizationRuleEngine;
import org.unreal.modelrouter.security.model.SanitizationRule;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 默认脱敏规则引擎实现
 * 负责执行具体的脱敏逻辑，包括正则表达式匹配和替换策略
 */
@Slf4j
@Component
public class DefaultSanitizationRuleEngine implements SanitizationRuleEngine {
    
    /**
     * 编译后的正则表达式缓存
     */
    private final Map<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();
    
    /**
     * 规则匹配统计
     */
    private final Map<String, Long> ruleMatchCounts = new ConcurrentHashMap<>();
    
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
        if (content == null) {
            return Mono.empty();
        }
        
        if (content.isEmpty()) {
            return Mono.just(content);
        }
        
        if (rules == null || rules.isEmpty()) {
            return Mono.just(content);
        }
        
        return Mono.fromCallable(() -> {
            String sanitizedContent = content;
            
            // 按优先级排序规则
            List<SanitizationRule> sortedRules = rules.stream()
                    .filter(SanitizationRule::isEnabled)
                    .filter(rule -> rule.isApplicableToContentType(contentType))
                    .sorted(Comparator.comparingInt(SanitizationRule::getPriority))
                    .toList();
            
            // 依次应用每个规则
            for (SanitizationRule rule : sortedRules) {
                try {
                    sanitizedContent = applyRule(sanitizedContent, rule);
                } catch (Exception e) {
                    log.error("应用脱敏规则失败: ruleId={}, error={}", rule.getRuleId(), e.getMessage(), e);
                    // 继续处理其他规则，不因单个规则失败而中断整个流程
                }
            }
            
            return sanitizedContent;
        })
        .onErrorMap(throwable -> new SanitizationException("脱敏处理失败", throwable, SanitizationException.SANITIZATION_FAILED));
    }
    
    /**
     * 应用单个脱敏规则
     */
    private String applyRule(String content, SanitizationRule rule) {
        Pattern pattern = getCompiledPattern(rule);
        if (pattern == null) {
            log.warn("无法获取规则的编译模式: ruleId={}", rule.getRuleId());
            return content;
        }
        
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            return content;
        }
        
        // 记录匹配统计
        incrementMatchCount(rule.getRuleId());
        
        // 根据策略进行替换
        return switch (rule.getStrategy()) {
            case MASK -> applyMaskStrategy(content, matcher, rule);
            case REPLACE -> applyReplaceStrategy(content, matcher, rule);
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
     * 获取编译后的正则表达式模式
     */
    private Pattern getCompiledPattern(SanitizationRule rule) {
        return compiledPatterns.computeIfAbsent(rule.getRuleId(), k -> {
            try {
                String pattern = buildPattern(rule);
                return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException e) {
                log.error("编译正则表达式失败: ruleId={}, pattern={}, error={}", 
                         rule.getRuleId(), rule.getPattern(), e.getMessage());
                return null;
            }
        });
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
        return Mono.fromRunnable(() -> {
            if (rules == null || rules.isEmpty()) {
                return;
            }
            
            log.info("开始编译脱敏规则，规则数量: {}", rules.size());
            int successCount = 0;
            int failureCount = 0;
            
            for (SanitizationRule rule : rules) {
                try {
                    String pattern = buildPattern(rule);
                    Pattern compiledPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                    compiledPatterns.put(rule.getRuleId(), compiledPattern);
                    successCount++;
                } catch (PatternSyntaxException e) {
                    log.error("编译规则失败: ruleId={}, pattern={}, error={}", 
                             rule.getRuleId(), rule.getPattern(), e.getMessage());
                    failureCount++;
                }
            }
            
            log.info("脱敏规则编译完成，成功: {}, 失败: {}", successCount, failureCount);
        });
    }
    
    @Override
    public Mono<Long> getRuleMatchCount(String ruleId) {
        return Mono.fromCallable(() -> ruleMatchCounts.getOrDefault(ruleId, 0L));
    }
    
    /**
     * 清除编译缓存
     */
    public void clearCompiledPatterns() {
        compiledPatterns.clear();
        log.info("已清除编译后的正则表达式缓存");
    }
    
    /**
     * 重置匹配统计
     */
    public void resetMatchCounts() {
        ruleMatchCounts.clear();
        log.info("已重置规则匹配统计");
    }
    
    /**
     * 获取所有规则匹配统计
     */
    public Map<String, Long> getAllMatchCounts() {
        return Map.copyOf(ruleMatchCounts);
    }
}