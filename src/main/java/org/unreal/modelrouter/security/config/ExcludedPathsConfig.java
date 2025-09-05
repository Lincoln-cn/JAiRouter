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
        authPaths.add("/favicon.ico");
        AUTH_EXCLUDED_PATHS = Collections.unmodifiableSet(authPaths);
        
        // 数据脱敏排除路径
        Set<String> securityPaths = new HashSet<>();
        securityPaths.add("/actuator/");
        securityPaths.add("/health");
        securityPaths.add("/metrics");
        securityPaths.add("/swagger-ui/");
        securityPaths.add("/v3/api-docs");
        securityPaths.add("/favicon.ico");
        securityPaths.add("/static/");
        securityPaths.add("/css/");
        securityPaths.add("/js/");
        securityPaths.add("/images/");
        // 排除所有AI模型接口路径，避免对AI模型输入输出进行脱敏
        securityPaths.add("/v1/chat/");
        securityPaths.add("/v1/embeddings");
        securityPaths.add("/v1/rerank");
        securityPaths.add("/v1/audio/");
        securityPaths.add("/v1/images/");
        securityPaths.add("/v1/debug/");
        // 排除认证端点
        securityPaths.add("/api/auth/jwt/login");
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