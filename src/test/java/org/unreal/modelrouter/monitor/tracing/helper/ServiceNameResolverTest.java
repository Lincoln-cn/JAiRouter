package org.unreal.modelrouter.monitor.tracing.helper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.RequestPath;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * ServiceNameResolver 单元测试
 *
 * @author JAiRouter Team
 * @since 2.27.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceNameResolver 单元测试")
class ServiceNameResolverTest {

    private ServiceNameResolver serviceNameResolver;

    @Mock
    private TracingConfiguration tracingConfiguration;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private RequestPath requestPath;

    @BeforeEach
    void setUp() {
        lenient().when(tracingConfiguration.getServiceName()).thenReturn("jairouter");
        serviceNameResolver = new ServiceNameResolver(tracingConfiguration);
    }

    private void setupRequestPath(String path) {
        when(requestPath.value()).thenReturn(path);
        when(request.getPath()).thenReturn(requestPath);
    }

    @Test
    @DisplayName("解析服务名称 - 前端静态资源 JS")
    void resolveServiceName_shouldReturnFrontForJsFiles() {
        // Given
        setupRequestPath("/admin/assets/index.js");

        // When
        String serviceName = serviceNameResolver.resolveServiceName(request);

        // Then
        assertEquals("front", serviceName);
    }

    @Test
    @DisplayName("解析服务名称 - 前端静态资源 CSS")
    void resolveServiceName_shouldReturnFrontForCssFiles() {
        // Given
        setupRequestPath("/admin/styles/main.css");

        // When
        String serviceName = serviceNameResolver.resolveServiceName(request);

        // Then
        assertEquals("front", serviceName);
    }

    @Test
    @DisplayName("解析服务名称 - 前端图片资源")
    void resolveServiceName_shouldReturnFrontForImageFiles() {
        // Given
        setupRequestPath("/admin/images/logo.png");

        // When
        String serviceName = serviceNameResolver.resolveServiceName(request);

        // Then
        assertEquals("front", serviceName);
    }

    @Test
    @DisplayName("解析服务名称 - 前端 SVG")
    void resolveServiceName_shouldReturnFrontForSvgFiles() {
        // Given
        setupRequestPath("/admin/icons/icon.svg");

        // When
        String serviceName = serviceNameResolver.resolveServiceName(request);

        // Then
        assertEquals("front", serviceName);
    }

    @Test
    @DisplayName("解析服务名称 - 前端字体文件")
    void resolveServiceName_shouldReturnFrontForFontFiles() {
        // Given
        setupRequestPath("/admin/fonts/roboto.woff2");

        // When
        String serviceName = serviceNameResolver.resolveServiceName(request);

        // Then
        assertEquals("front", serviceName);
    }

    @Test
    @DisplayName("解析服务名称 - 前端 SPA 路由")
    void resolveServiceName_shouldReturnFrontForSpaRoutes() {
        // Given
        setupRequestPath("/admin/tracing/search");

        // When
        String serviceName = serviceNameResolver.resolveServiceName(request);

        // Then
        assertEquals("front", serviceName);
    }

    @Test
    @DisplayName("解析服务名称 - 前端 favicon")
    void resolveServiceName_shouldReturnFrontForFavicon() {
        // Given
        setupRequestPath("/favicon.ico");

        // When
        String serviceName = serviceNameResolver.resolveServiceName(request);

        // Then
        assertEquals("front", serviceName); // favicon.ico 是静态资源
    }

    @Test
    @DisplayName("解析服务名称 - 后端 API 请求")
    void resolveServiceName_shouldReturnServerForApiRequests() {
        // Given
        setupRequestPath("/api/v1/models");

        // When
        String serviceName = serviceNameResolver.resolveServiceName(request);

        // Then
        assertEquals("server", serviceName);
    }

    @Test
    @DisplayName("解析服务名称 - Actuator 端点")
    void resolveServiceName_shouldReturnServerForActuator() {
        // Given
        setupRequestPath("/actuator/health");

        // When
        String serviceName = serviceNameResolver.resolveServiceName(request);

        // Then
        assertEquals("server", serviceName);
    }

    @Test
    @DisplayName("解析服务名称 - Admin API")
    void resolveServiceName_shouldReturnServerForAdminApi() {
        // Given
        setupRequestPath("/admin/api/services");

        // When
        String serviceName = serviceNameResolver.resolveServiceName(request);

        // Then
        assertEquals("server", serviceName);
    }

    @Test
    @DisplayName("解析服务名称 - 其他路径使用默认值")
    void resolveServiceName_shouldUseDefaultForOtherPaths() {
        // Given
        setupRequestPath("/other/path");

        // When
        String serviceName = serviceNameResolver.resolveServiceName(request);

        // Then
        assertEquals("jairouter", serviceName);
    }

    @Test
    @DisplayName("判断静态资源 - JS 文件")
    void isStaticResource_shouldReturnTrueForJs() {
        assertTrue(serviceNameResolver.isStaticResource("/path/to/file.js"));
    }

    @Test
    @DisplayName("判断静态资源 - CSS 文件")
    void isStaticResource_shouldReturnTrueForCss() {
        assertTrue(serviceNameResolver.isStaticResource("/styles/main.css"));
    }

    @Test
    @DisplayName("判断静态资源 - HTML 文件")
    void isStaticResource_shouldReturnTrueForHtml() {
        assertTrue(serviceNameResolver.isStaticResource("/index.html"));
    }

    @Test
    @DisplayName("判断静态资源 - 图片文件")
    void isStaticResource_shouldReturnTrueForImages() {
        assertTrue(serviceNameResolver.isStaticResource("/logo.png"));
        assertTrue(serviceNameResolver.isStaticResource("/banner.jpg"));
        assertTrue(serviceNameResolver.isStaticResource("/icon.svg"));
    }

    @Test
    @DisplayName("判断静态资源 - ICO 文件")
    void isStaticResource_shouldReturnTrueForIco() {
        assertTrue(serviceNameResolver.isStaticResource("/favicon.ico"));
    }

    @Test
    @DisplayName("判断静态资源 - Map 文件")
    void isStaticResource_shouldReturnTrueForMap() {
        assertTrue(serviceNameResolver.isStaticResource("/bundle.js.map"));
    }

    @Test
    @DisplayName("判断静态资源 - Assets 目录")
    void isStaticResource_shouldReturnTrueForAssetsPath() {
        assertTrue(serviceNameResolver.isStaticResource("/admin/assets/logo.png"));
    }

    @Test
    @DisplayName("判断静态资源 - API 路径返回 false")
    void isStaticResource_shouldReturnFalseForApiPath() {
        assertFalse(serviceNameResolver.isStaticResource("/api/v1/models"));
    }

    @Test
    @DisplayName("构建操作名称 - GET 请求")
    void buildOperationName_shouldBuildCorrectNameForGet() {
        // Given
        setupRequestPath("/api/v1/models");
        when(request.getMethod()).thenReturn(HttpMethod.GET);

        // When
        String operationName = serviceNameResolver.buildOperationName(request);

        // Then
        assertEquals("GET /api/v1/models", operationName);
    }

    @Test
    @DisplayName("构建操作名称 - POST 请求")
    void buildOperationName_shouldBuildCorrectNameForPost() {
        // Given
        setupRequestPath("/api/v1/chat/completions");
        when(request.getMethod()).thenReturn(HttpMethod.POST);

        // When
        String operationName = serviceNameResolver.buildOperationName(request);

        // Then
        assertEquals("POST /api/v1/chat/completions", operationName);
    }

    @Test
    @DisplayName("构建操作名称 - 带查询参数")
    void buildOperationName_shouldRemoveQueryParams() {
        // Given
        setupRequestPath("/api/v1/models?filter=active&limit=10");
        when(request.getMethod()).thenReturn(HttpMethod.GET);

        // When
        String operationName = serviceNameResolver.buildOperationName(request);

        // Then
        assertEquals("GET /api/v1/models", operationName);
    }

    @Test
    @DisplayName("构建操作名称 - null 方法")
    void buildOperationName_shouldHandleNullMethod() {
        // Given
        setupRequestPath("/test/path");
        when(request.getMethod()).thenReturn(null);

        // When
        String operationName = serviceNameResolver.buildOperationName(request);

        // Then
        assertEquals("UNKNOWN /test/path", operationName);
    }
}