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
     * 根据请求路径判断服务名称
     *
     * @param request HTTP 请求
     * @return 服务名称
     */
    public String resolveServiceName(final ServerHttpRequest request) {
        String path = request.getPath().value();

        // 前端静态资源路径判断（包括 favicon.ico）
        if (path.startsWith("/admin/assets/")
            || (path.startsWith("/admin/") && isStaticResource(path))
            || path.endsWith(".js") || path.endsWith(".css")
            || path.endsWith(".html") || path.endsWith(".ico")
            || path.endsWith(".png") || path.endsWith(".jpg")
            || path.endsWith(".svg") || path.endsWith(".woff")
            || path.endsWith(".woff2") || path.endsWith(".ttf")) {
            return "front";
        }

        // Admin API 请求判断（优先于 SPA 路由判断）
        if (path.startsWith("/admin/api/")) {
            return "server";
        }

        // 前端页面路由请求（SPA 路由，如 /admin/tracing/search）
        // 这些请求实际上返回 index.html，属于前端页面
        if (path.startsWith("/admin/") && !path.startsWith("/admin/api/")) {
            return "front";
        }

        // 后端 API 请求判断
        if (path.startsWith("/api/") || path.startsWith("/actuator/")) {
            return "server";
        }

        // 其他请求使用默认服务名称
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