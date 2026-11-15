package org.unreal.modelrouter.monitor.tracing.helper;

import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;

/**
 * 服务名称解析器
 *
 * 根据请求路径解析服务名称，用于追踪数据分类：
 * - 前端静态资源请求返回 "front"
 * - 后端 API 请求返回 "server"
 * - 其他请求返回配置的默认服务名称
 *
 * @author JAiRouter Team
 * @since 2.27.0
 */
@Component
@RequiredArgsConstructor
public class ServiceNameResolver {

    private final TracingConfiguration tracingConfiguration;

    /**
     * 服务名常量：三个词覆盖整个系统。
     *
     * <p>{@code gateway} —— 业务转发（{@code /v1/**}），链路里包含选实例与上游调用；
     * {@code console} —— 控制台与后端管理 API；{@code web} —— 前端静态资源。</p>
     *
     * <p>刻意不用带产品名的前缀：日志里 {@code [gateway]} 比 {@code [jairouter-gateway]} 更容易扫。</p>
     */
    public static final String SERVICE_GATEWAY = "gateway";
    public static final String SERVICE_CONSOLE = "console";
    public static final String SERVICE_WEB = "web";

    /**
     * 根据请求路径判断服务名称
     *
     * @param request HTTP 请求
     * @return 服务名称
     */
    public String resolveServiceName(final ServerHttpRequest request) {
        String path = request.getPath().value();

        // 前端静态资源（含 favicon 与 SPA 页面路由返回的 index.html）
        if (path.startsWith("/admin/assets/")
            || (path.startsWith("/admin/") && isStaticResource(path))
            || path.endsWith(".js") || path.endsWith(".css")
            || path.endsWith(".html") || path.endsWith(".ico")
            || path.endsWith(".png") || path.endsWith(".jpg")
            || path.endsWith(".svg") || path.endsWith(".woff")
            || path.endsWith(".woff2") || path.endsWith(".ttf")) {
            return SERVICE_WEB;
        }

        // 控制台后端 API（优先于 SPA 路由判断）
        if (path.startsWith("/admin/api/")) {
            return SERVICE_CONSOLE;
        }

        // SPA 路由（如 /admin/tracing/search），实际返回 index.html
        if (path.startsWith("/admin/") && !path.startsWith("/admin/api/")) {
            return SERVICE_WEB;
        }

        // 后端管理 API 与运维端点
        if (path.startsWith("/api/") || path.startsWith("/actuator/")) {
            return SERVICE_CONSOLE;
        }

        // 其余（主要是 /v1/** 业务转发）使用配置的服务名，默认 gateway
        return tracingConfiguration.getServiceName();
    }

    /**
     * 判断是否为静态资源
     *
     * @param path 请求路径
     * @return 是否为静态资源
     */
    public boolean isStaticResource(final String path) {
        return path.endsWith(".js") || path.endsWith(".css")
               || path.endsWith(".html") || path.endsWith(".ico")
               || path.endsWith(".png") || path.endsWith(".jpg")
               || path.endsWith(".svg") || path.endsWith(".map")
               || path.contains("/assets/");
    }

    /**
     * 构建操作名称
     *
     * @param request HTTP 请求
     * @return 操作名称，格式为 "METHOD /path"
     */
    public String buildOperationName(final ServerHttpRequest request) {
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getPath().value();

        // 简化路径，移除查询参数
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf("?"));
        }

        return method + " " + path;
    }
}