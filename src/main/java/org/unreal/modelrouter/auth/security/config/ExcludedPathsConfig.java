package org.unreal.modelrouter.auth.security.config;

import java.util.List;
import java.util.Set;

import org.springframework.util.AntPathMatcher;

/**
 * 统一管理所有需要排除认证和数据脱敏的路径配置
 */
public class ExcludedPathsConfig {
    /** Private constructor to prevent instantiation. */
    private ExcludedPathsConfig() {}
    
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
            "/api/auth/jwt/validate",
            "/favicon.ico",
            "/.well-known"
        );

        // 认证排除路径模式
        // P1：/api/health-status/** 与 /ws/** 均不排除认证（query/header token + Spring Security）
        AUTH_EXCLUDED_PATTERNS = List.of(
            "/actuator/**",
            "/admin/**"
        );
        
        // 数据脱敏排除路径（ResponseSanitizationFilter 网关响应过滤器）
        //
        // 设计裁决（v3.1.x）：PII 主战场是「聊天调用历史 / 日志 / 追踪记录」中的用户数据，
        // 由 SanitizationService.sanitizeForStorage 在 SUMMARY 记录链路处理，不经本过滤器。
        // 本过滤器只作用于「未被排除的 HTTP JSON 响应」，因此：
        //  1) AI 实时推理路径（/api/v1/**）排除 —— 不改写客户端模型输出；
        //  2) 管理台 /api/** 全部排除 —— 管理接口返回配置/密钥元数据时不得被掩码破坏；
        //  3) 静态资源与运维端点排除。
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
            "/admin",
            // 管理台与配置 API：管理控制台需要看到真实配置值
            "/api/",
            // AI 实时推理（覆盖 /api/v1/chat 等；若未来有非 /api 的 AI 入口再单独登记）
            "/v1/"
        );

        // 数据脱敏排除路径模式（Ant 风格，补充前缀集合未覆盖的形态）
        DATA_MASKING_EXCLUDED_PATTERNS = List.of(
            "/actuator/**",
            "/api/**",
            "/v1/**",
            "/admin/**"
        );
    }
    
    /**
     * 检查路径是否在认证排除列表中
     * 
     * @param path 要检查的路径
     * @return 如果路径应排除认证则返回true，否则返回false
     */
    public static boolean isAuthExcluded(final String path) {
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
    public static boolean isDataMaskExcluded(final String path) {
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