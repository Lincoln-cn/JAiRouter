package org.unreal.modelrouter.sanitization;

import org.unreal.modelrouter.security.model.SanitizationRule;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 脱敏规则引擎接口
 * 负责执行具体的脱敏逻辑
 */
public interface SanitizationRuleEngine {
    
    /**
     * 应用脱敏规则到内容
     * @param content 原始内容
     * @param rules 脱敏规则列表
     * @param contentType 内容类型
     * @return 脱敏后的内容
     */
    Mono<String> applySanitizationRules(String content, List<SanitizationRule> rules, String contentType);
    
    /**
     * 验证脱敏规则的有效性
     * @param rule 脱敏规则
     * @return 验证结果
     */
    Mono<Boolean> validateRule(SanitizationRule rule);
    
    /**
     * 编译正则表达式规则（用于性能优化）
     * @param rules 脱敏规则列表
     * @return 编译操作结果
     */
    Mono<Void> compileRules(List<SanitizationRule> rules);
    
    /**
     * 获取规则匹配统计信息
     * @param ruleId 规则ID
     * @return 匹配次数
     */
    Mono<Long> getRuleMatchCount(String ruleId);
}