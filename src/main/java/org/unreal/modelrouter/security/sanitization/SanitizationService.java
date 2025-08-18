package org.unreal.modelrouter.security.sanitization;

import org.unreal.modelrouter.security.model.SanitizationRule;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 数据脱敏服务接口
 * 提供请求和响应数据的脱敏处理功能
 */
public interface SanitizationService {
    
    /**
     * 对请求内容进行脱敏处理
     * @param content 原始内容
     * @param contentType 内容类型
     * @param userId 用户ID（用于白名单检查）
     * @return 脱敏后的内容
     */
    Mono<String> sanitizeRequest(String content, String contentType, String userId);
    
    /**
     * 对响应内容进行脱敏处理
     * @param content 原始内容
     * @param contentType 内容类型
     * @return 脱敏后的内容
     */
    Mono<String> sanitizeResponse(String content, String contentType);
    
    /**
     * 检查用户是否在白名单中
     * @param userId 用户ID
     * @return 是否在白名单中
     */
    Mono<Boolean> isUserWhitelisted(String userId);
    
    /**
     * 获取所有脱敏规则
     * @return 脱敏规则列表
     */
    Mono<List<SanitizationRule>> getAllRules();
    
    /**
     * 更新脱敏规则
     * @param rules 新的脱敏规则列表
     * @return 更新操作结果
     */
    Mono<Void> updateRules(List<SanitizationRule> rules);
    
    /**
     * 添加脱敏规则
     * @param rule 脱敏规则
     * @return 添加操作结果
     */
    Mono<Void> addRule(SanitizationRule rule);
    
    /**
     * 删除脱敏规则
     * @param ruleId 规则ID
     * @return 删除操作结果
     */
    Mono<Void> removeRule(String ruleId);
}