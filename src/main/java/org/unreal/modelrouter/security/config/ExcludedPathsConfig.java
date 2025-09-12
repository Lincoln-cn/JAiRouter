package org.unreal.modelrouter.security.config;

import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

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
    
    static {
        // 认证排除路径
        Set<String> authPaths = new HashSet<>();
        authPaths.add("/actuator/");
        authPaths.add("/health");
        authPaths.add("/metrics");
        authPaths.add("/swagger-ui/");
        authPaths.add("/v3/api-docs");
        authPaths.add("/webjars/");
        authPaths.add("/api/auth/jwt/login");
        authPaths.add("/admin/");
        authPaths.add("/favicon.ico");
        authPaths.add("/.well-known");
        AUTH_EXCLUDED_PATHS = Collections.unmodifiableSet(authPaths);
        
        // 数据脱敏排除路径
        Set<String> securityPaths = new HashSet<>();
        securityPaths.add("/actuator/");
        securityPaths.add("/health");
        securityPaths.add("/metrics");
        securityPaths.add("/swagger-ui/");
        securityPaths.add("/v3/api-docs");
        securityPaths.add("/favicon.ico");
        securityPaths.add("/.well-known");
        securityPaths.add("/static/");
        securityPaths.add("/css/");
        securityPaths.add("/js/");
        securityPaths.add("/images/");
        // 排除AI模型接口路径的数据脱敏（但仍需要认证！）
        // 这些接口不进行数据脱敏是因为AI模型的输入输出不应被修改
        // 但这些接口仍然需要通过API Key或JWT进行认证
        securityPaths.add("/v1/chat/");
        securityPaths.add("/v1/embeddings");
        securityPaths.add("/v1/rerank");
        securityPaths.add("/v1/audio/");
        securityPaths.add("/v1/images/");
        securityPaths.add("/v1/debug/");
        securityPaths.add("/admin/");
        // 排除认证端点
        securityPaths.add("/api/auth/jwt/login");
        // 排除JWT账户管理端点
        securityPaths.add("/api/security/jwt/accounts");
        securityPaths.add("/api/security/jwt/accounts/");
        DATA_MASKING_EXCLUDED_PATHS = Collections.unmodifiableSet(securityPaths);
    }
    
    /**
     * 检查路径是否在认证排除列表中
     * 
     * @param path 要检查的路径
     * @return 如果路径应排除认证则返回true，否则返回false
     */
    public static boolean isAuthExcluded(String path) {
        return AUTH_EXCLUDED_PATHS.stream()
                .anyMatch(path::startsWith) || 
                AUTH_EXCLUDED_PATHS.contains(path);
    }
    
    /**
     * 检查路径是否在数据脱敏排除列表中
     * 
     * @param path 要检查的路径
     * @return 如果路径应排除数据脱敏则返回true，否则返回false
     */
    public static boolean isDataMaskExcluded(String path) {
        return DATA_MASKING_EXCLUDED_PATHS.stream()
                .anyMatch(path::startsWith) || 
                DATA_MASKING_EXCLUDED_PATHS.contains(path);
    }
}