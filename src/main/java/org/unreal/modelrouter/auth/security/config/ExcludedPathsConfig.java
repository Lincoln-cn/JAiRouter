package org.unreal.modelrouter.auth.security.config;

import java.util.List;
import java.util.Set;

import org.springframework.util.AntPathMatcher;

/**
 * 统一管理所有需要排除认证和数据脱敏的路径配置。
 *
 * <p>数据脱敏侧的排除面见 {@link #DATA_MASKING_EXCLUDED_PATHS} 上方的注释：自 issue #560
 * （方案 C 第一阶段）起 {@code /v1/**} 的<b>非流式 JSON</b> 响应纳入脱敏，SSE
 * （{@code text/event-stream}）仍由内容类型判定跳过、留待 #560 后续期；管理台 {@code /api/**}
 * （含 {@code /api/v1/**}）与运维端点继续排除。</p>
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

    /**
     * R3-P1：API 文档路径是否排除认证（由 SecurityConfiguration 按 docs-public 设置）。
     * 默认 false —— 文档端点需要认证。
     */
    private static volatile boolean apiDocsAuthExcluded = false;

    /**
     * 设置 API 文档是否免认证（Swagger / OpenAPI）。
     */
    public static void setApiDocsAuthExcluded(final boolean excluded) {
        apiDocsAuthExcluded = excluded;
    }

    static {
        // 认证排除路径 - 使用Set.of创建不可变集合（Java 9+）
        // R3-P1：Swagger / api-docs 不再默认排除，由 docs-public 开关控制
        //
        // issue #340：此前这里还有 "/actuator/"，而 AUTH_EXCLUDED_PATTERNS 里还有 "/actuator/**"。
        // 两处一起把整个 /actuator/** 从**认证链**里摘掉，可 SecurityConfiguration 又要求
        // hasRole("ADMIN") ⇒ **带 ADMIN JWT 也进不去**（响应体是 AUTH_MISSING，即「认证没建立」）。
        // 现在只排除**匿名放行清单**（与 AnonymousEndpointPaths.HEALTH_AND_OPS 同一份：健康探针 /
        // info / prometheus），其余 /actuator/** 走正常认证链，于是管理面 JWT 能真正满足 ROLE_ADMIN。
        AUTH_EXCLUDED_PATHS = Set.of(
            "/health",
            "/metrics",
            "/api/auth/jwt/login",
            "/api/auth/jwt/validate",
            "/favicon.ico",
            "/.well-known"
        );

        // 认证排除路径模式：匿名放行清单（健康探针 / info / prometheus） + 控制台静态资源
        AUTH_EXCLUDED_PATTERNS = java.util.stream.Stream
            .concat(AnonymousEndpointPaths.HEALTH_AND_OPS.stream(), java.util.stream.Stream.of("/admin/**"))
            .toList();
        
        // 数据脱敏排除路径（ResponseSanitizationFilter 网关响应过滤器）
        //
        // PII 主战场仍是「聊天调用历史 / 日志 / 追踪记录」中的用户数据，由
        // SanitizationService.sanitizeForStorage 在 SUMMARY 记录链路处理，不经本过滤器。
        // 本过滤器只作用于「未被排除的 HTTP JSON 响应」。
        //
        // issue #560（方案 C 第一阶段）：排除面按「是否改写协议输出」重新划线，取代 v3.1.x
        // 「AI 实时推理路径整体排除」的旧裁决：
        //  1) /v1/**（AI 入口）的**非流式 JSON** 响应纳入脱敏 —— 旧口径下明文 PII 直接到达客户端；
        //  2) /v1/** 的 SSE（text/event-stream）响应仍不脱敏 —— 由 ResponseSanitizationFilter
        //     .shouldSanitizeContentType 只认 application/json 天然跳过，流式分支本期不动，
        //     SSE 覆盖留待 issue #560 的后续阶段；
        //  3) 管理台 /api/** 全部排除 —— 管理接口返回配置/密钥元数据时不得被掩码破坏（原裁决不变）；
        //  4) 运维端点（/actuator/**、/health、/metrics）、API 文档与静态资源继续排除。
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
            // 管理台与配置 API：管理控制台需要看到真实配置值（/api/v1/** 也由本前缀覆盖）
            "/api/"
        );

        // 数据脱敏排除路径模式（Ant 风格，补充前缀集合未覆盖的形态）
        // /v1/** 自 issue #560 起不再排除：非流式 JSON 需脱敏，SSE 由内容类型判定跳过。
        DATA_MASKING_EXCLUDED_PATTERNS = List.of(
            "/actuator/**",
            "/api/**",
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
        // R3-P1：文档路径仅在 docs-public=true 时免认证
        if (apiDocsAuthExcluded && isApiDocsPath(path)) {
            return true;
        }
        // 检查精确匹配和前缀匹配
        if (AUTH_EXCLUDED_PATHS.stream().anyMatch(excludedPath ->
            path.equals(excludedPath) || path.startsWith(excludedPath))) {
            return true;
        }

        // 检查Ant路径模式匹配
        return AUTH_EXCLUDED_PATTERNS.stream().anyMatch(pattern ->
            pathMatcher.match(pattern, path));
    }

    private static boolean isApiDocsPath(final String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")
                || path.startsWith("/webjars/") || path.startsWith("/webjars");
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