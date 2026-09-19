package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R3-P1：Swagger/API docs 默认不得匿名开放。
 */
@DisplayName("API Docs 访问策略")
class ApiDocsAccessPolicyTest {

    @AfterEach
    void reset() {
        ExcludedPathsConfig.setApiDocsAuthExcluded(false);
    }

    @Test
    @DisplayName("docs-public 默认 false → Swagger 需认证")
    void docsPublic_defaultsToFalse() {
        SecurityProperties properties = new SecurityProperties();
        assertFalse(properties.isDocsPublic());
        assertFalse(ApiDocsAccessPolicy.isPublic(properties.isDocsPublic()));
    }

    @Test
    @DisplayName("docs-public=true → 允许匿名访问文档")
    void docsPublic_true_allowsAnonymous() {
        assertTrue(ApiDocsAccessPolicy.isPublic(true));
    }

    @Test
    @DisplayName("默认：ExcludedPaths 不排除 swagger / api-docs")
    void excludedPaths_defaultDoesNotSkipSwaggerAuth() {
        ExcludedPathsConfig.setApiDocsAuthExcluded(false);
        assertFalse(ExcludedPathsConfig.isAuthExcluded("/swagger-ui/index.html"));
        assertFalse(ExcludedPathsConfig.isAuthExcluded("/v3/api-docs"));
        assertFalse(ExcludedPathsConfig.isAuthExcluded("/webjars/swagger-ui/index.js"));
    }

    @Test
    @DisplayName("docs-public 开启后：ExcludedPaths 排除文档路径")
    void excludedPaths_whenPublic_skipsSwaggerAuth() {
        ExcludedPathsConfig.setApiDocsAuthExcluded(true);
        assertTrue(ExcludedPathsConfig.isAuthExcluded("/swagger-ui/index.html"));
        assertTrue(ExcludedPathsConfig.isAuthExcluded("/v3/api-docs/swagger-config"));
        assertTrue(ExcludedPathsConfig.isAuthExcluded("/webjars/something.js"));
        // 非文档路径不受影响
        assertFalse(ExcludedPathsConfig.isAuthExcluded("/api/config/instance/chat"));
    }
}
