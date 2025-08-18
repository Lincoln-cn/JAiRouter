package org.unreal.modelrouter.security.model;

/**
 * 规则类型枚举
 */
public enum RuleType {
    /**
     * 敏感词汇
     */
    SENSITIVE_WORD,
    
    /**
     * 个人身份信息模式
     */
    PII_PATTERN,
    
    /**
     * 自定义正则表达式
     */
    CUSTOM_REGEX
}