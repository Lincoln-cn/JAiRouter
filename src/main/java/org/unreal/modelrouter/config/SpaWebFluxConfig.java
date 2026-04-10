package org.unreal.modelrouter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * SPA (单页应用) 资源配置
 *
 * 将 /admin/** 路径下的所有请求转发到静态资源或 index.html
 * 让 Vue Router 处理前端路由
 *
 * 使用高优先级 Order(-1) 确保在 Spring Security 之前处理请求
 */
@Configuration
public class SpaWebFluxConfig {

    /**
     * 配置 SPA 资源路由
     *
     * 处理 /admin 路径的请求：
     * 1. 如果对应的静态资源文件存在，返回该文件
     * 2. 否则返回 index.html（让 Vue Router 处理前端路由）
     *
     * Order(-1) 确保这个路由在 Spring Security 过滤器之前执行
     */
    @Bean
    @Order(-1)
    public RouterFunction<ServerResponse> spaRouterFunction() {
        ClassPathResource adminRoot = new ClassPathResource("static/admin/");
        
        return RouterFunctions.route()
                .GET("/admin", request -> 
                    ServerResponse.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(Mono.just(new ClassPathResource("static/admin/index.html")), ClassPathResource.class)
                )
                .GET("/admin/**", request -> {
                    String path = request.path();
                    
                    // 尝试加载对应的静态资源
                    ClassPathResource resource = new ClassPathResource("static" + path);
                    if (resource.exists() && resource.isReadable()) {
                        // 静态资源存在，根据类型返回
                        MediaType contentType = getContentType(path);
                        return ServerResponse.ok()
                                .contentType(contentType)
                                .body(Mono.just(resource), ClassPathResource.class);
                    }
                    
                    // 静态资源不存在，返回 index.html（SPA fallback）
                    ClassPathResource indexHtml = new ClassPathResource("static/admin/index.html");
                    if (!indexHtml.exists()) {
                        return ServerResponse.notFound().build();
                    }
                    return ServerResponse.ok()
                            .contentType(MediaType.TEXT_HTML)
                            .body(Mono.just(indexHtml), ClassPathResource.class);
                })
                .build();
    }

    /**
     * 根据文件扩展名获取 Content-Type
     */
    private MediaType getContentType(String path) {
        String lowerPath = path.toLowerCase();
        
        if (lowerPath.endsWith(".js") || lowerPath.endsWith(".mjs")) {
            return MediaType.parseMediaType("application/javascript");
        }
        if (lowerPath.endsWith(".css")) {
            return MediaType.parseMediaType("text/css");
        }
        if (lowerPath.endsWith(".html")) {
            return MediaType.TEXT_HTML;
        }
        if (lowerPath.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        }
        if (lowerPath.endsWith(".xml")) {
            return MediaType.APPLICATION_XML;
        }
        if (lowerPath.endsWith(".svg")) {
            return MediaType.parseMediaType("image/svg+xml");
        }
        if (lowerPath.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lowerPath.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (lowerPath.endsWith(".ico")) {
            return MediaType.parseMediaType("image/x-icon");
        }
        if (lowerPath.endsWith(".woff") || lowerPath.endsWith(".woff2")) {
            return MediaType.parseMediaType("font/woff2");
        }
        if (lowerPath.endsWith(".ttf")) {
            return MediaType.parseMediaType("font/ttf");
        }
        
        // 默认返回 HTML
        return MediaType.TEXT_HTML;
    }
}