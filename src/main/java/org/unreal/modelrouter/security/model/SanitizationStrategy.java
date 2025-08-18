package org.unreal.modelrouter.security.model;

/**
 * 脱敏策略枚举
 */
public enum SanitizationStrategy {
    /**
     * 掩码处理（用*替换）
     */
    MASK,
    
    /**
     * 替换处理（用指定文本替换）
     */
    REPLACE,
    
    /**
     * 删除处理（直接删除匹配内容）
     */
    REMOVE,
    
    /**
     * 哈希处理（用哈希值替换）
     */
    HASH
}