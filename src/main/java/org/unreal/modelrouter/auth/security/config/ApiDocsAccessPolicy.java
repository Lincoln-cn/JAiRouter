package org.unreal.modelrouter.auth.security.config;

/**
 * API 文档（Swagger / OpenAPI）匿名访问策略（R3-P1）。
 *
 * <p>默认关闭：生产/默认配置下文档端点需认证，避免暴露内部路由与模型细节。
 * 仅当 {@code jairouter.security.docs-public=true} 时允许匿名。</p>
 */
public final class ApiDocsAccessPolicy {

    /** 配置键：jairouter.security.docs-public */
    public static final String PROPERTY = "jairouter.security.docs-public";

    private ApiDocsAccessPolicy() {
    }

    /**
     * 文档是否允许匿名访问。
     *
     * @param docsPublic 配置值，默认 false
     * @return true = permitAll；false = 需认证
     */
    public static boolean isPublic(final boolean docsPublic) {
        return docsPublic;
    }
}
