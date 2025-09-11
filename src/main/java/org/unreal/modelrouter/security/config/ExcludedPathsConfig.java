package org.unreal.modelrouter.security.config;

import org.springframework.util.AntPathMatcher;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.List;

/**
 * 统一管理所有需要排除认证和数据脱敏的路径配置
 */
public class ExcludedPathsConfig {
    
    /**
     * 需要排除认证的路径集合
     */
    public static final Set<String> AUTH_EXCLUDED_PATHS;
    
    /**
     * 需要排除数据脱敏的路径集合
     */
    public static final Set<String> DATA_MASKING_EXCLUDED_PATHS;
    
    /**
     * 需要排除认证的Ant风格路径模式列表
     */
    public static final List<String> AUTH_EXCLUDED_PATTERNS;

    /**
     * 需要排除数据脱敏的Ant风格路径模式列表
     */
    public static final List<String> DATA_MASKING_EXCLUDED_PATTERNS;

    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    static {
        // 认证排除路径 - 使用Set.of创建不可变集合（Java 9+）
        AUTH_EXCLUDED_PATHS = Set.of(
            "/actuator/",
            "/health",
            "/metrics",
            "/swagger-ui/",
            "/v3/api-docs",
            "/webjars/",
            "/api/auth/jwt/login",
            "/admin",
            "/favicon.ico",
            "/.well-known"
        );

        // 认证排除路径模式
        AUTH_EXCLUDED_PATTERNS = List.of(
            "/actuator/**",
            "/api/auth/jwt/login"
        );
        
        // 数据脱敏排除路径
        DATA_MASKING_EXCLUDED_PATHS = Set.of(
            "/actuator/",
            "/health",
            "/metrics",
            "/swagger-ui/",
            "/v3/api-docs",
            "/favicon.ico",
            "/.well-known",
            "/static/",
            "/css/",
            "/js/",
            "/images/",
            // 排除AI模型接口路径的数据脱敏（但仍需要认证！）
            "/v1/chat/",
            "/v1/embeddings",
            "/v1/rerank",
            "/v1/audio/",
            "/v1/images/",
            "/v1/debug/",
            "/admin",
            // 排除认证端点
            "/api/auth/jwt/login"
        );

        // 数据脱敏排除路径模式
        DATA_MASKING_EXCLUDED_PATTERNS = List.of(
            "/actuator/**",
            "/api/auth/jwt/login",
            "/api/security/jwt/accounts/**"
        );
    }
    
    /**
     * 检查路径是否在认证排除列表中
     * 
     * @param path 要检查的路径
     * @return 如果路径应排除认证则返回true，否则返回false
     */
    public static boolean isAuthExcluded(String path) {
        // 检查精确匹配和前缀匹配
        if (AUTH_EXCLUDED_PATHS.stream().anyMatch(excludedPath ->
            path.equals(excludedPath) || path.startsWith(excludedPath))) {
            return true;
        }

        // 检查Ant路径模式匹配
        return AUTH_EXCLUDED_PATTERNS.stream().anyMatch(pattern ->
            pathMatcher.match(pattern, path));
    }
    
    /**
     * 检查路径是否在数据脱敏排除列表中
     * 
     * @param path 要检查的路径
     * @return 如果路径应排除数据脱敏则返回true，否则返回false
     */
    public static boolean isDataMaskExcluded(String path) {
        // 检查精确匹配和前缀匹配
        if (DATA_MASKING_EXCLUDED_PATHS.stream().anyMatch(excludedPath ->
            path.equals(excludedPath) || path.startsWith(excludedPath))) {
            return true;
        }

        // 检查Ant路径模式匹配
        return DATA_MASKING_EXCLUDED_PATTERNS.stream().anyMatch(pattern ->
            pathMatcher.match(pattern, path));
    }
}
