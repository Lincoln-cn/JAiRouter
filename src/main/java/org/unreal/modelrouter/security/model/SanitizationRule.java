package org.unreal.modelrouter.security.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 数据脱敏规则数据模型
 */
@Data
@Builder
public class SanitizationRule {
    
    /**
     * 规则唯一标识符
     */
    private String ruleId;
    
    /**
     * 规则名称
     */
    private String name;
    
    /**
     * 规则描述
     */
    private String description;
    
    /**
     * 规则类型
     */
    private RuleType type;
    
    /**
     * 匹配模式（正则表达式或关键词）
     */
    private String pattern;
    
    /**
     * 脱敏策略
     */
    private SanitizationStrategy strategy;
    
    /**
     * 是否启用
     */
    private boolean enabled;
    
    /**
     * 规则优先级（数值越小优先级越高）
     */
    private int priority;
    
    /**
     * 适用的内容类型列表
     */
    private List<String> applicableContentTypes;
    
    /**
     * 替换字符（用于MASK和REPLACE策略）
     */
    private String replacementChar;
    
    /**
     * 替换文本（用于REPLACE策略）
     */
    private String replacementText;
    
    /**
     * 检查规则是否适用于指定内容类型
     * @param contentType 内容类型
     * @return 是否适用
     */
    public boolean isApplicableToContentType(String contentType) {
        return applicableContentTypes == null || 
               applicableContentTypes.isEmpty() || 
               applicableContentTypes.contains(contentType);
    }
}