/**
 * 实例 ID 值对象
 * 确保实例 ID 的有效性和一致性
 */
package org.unreal.modelrouter.common.vo;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 实例 ID 值对象
 */
@Getter
@EqualsAndHashCode
public class InstanceId {
    
    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final int MAX_LENGTH = 64;
    
    private final String value;
    
    /**
     * 私有构造函数，强制使用工厂方法
     */
    private InstanceId(final String value) {
        this.value = value;
    }
    
    /**
     * 从字符串创建实例 ID
     */
    public static InstanceId of(final String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("实例 ID 不能为空");
        }
        
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("实例 ID 长度不能超过 " + MAX_LENGTH + " 个字符");
        }
        
        if (!VALID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("实例 ID 只能包含字母、数字、下划线和连字符");
        }
        
        return new InstanceId(trimmed);
    }
    
    /**
     * 生成新的随机实例 ID
     */
    public static InstanceId generate() {
        return new InstanceId(UUID.randomUUID().toString().replace("-", ""));
    }
    
    @Override
    public String toString() {
        return value;
    }
}
