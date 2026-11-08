package org.unreal.modelrouter.config.core;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * SPA (单页应用) 资源配置
 *
 * <p>将 /admin/** 路径下的所有请求转发到静态资源或 index.html，让 Vue Router 处理前端路由。
 * 使用高优先级 Order(-1) 确保在 Spring Security 之前处理请求。</p>
 *
 * <p>缓存策略（v3.8.x）：构建产物 {@code /admin/assets/**} 的文件名带内容哈希，内容变则名变，
 * 因此给一年期 immutable 缓存；{@code index.html} 与 SPA 兜底响应必须每次回源校验，
 * 否则发版后浏览器仍按旧 index.html 引用已删除的 chunk。此前全部资源都走框架默认的
 * {@code no-store}，带哈希的 3 个超 1MB chunk 每次刷新都要全量重下。</p>
 *
 * <p>构建产物缺失时返回 404 而不是回落 index.html：浏览器拿到 {@code text/html} 的 .js
 * 会因 MIME 不符拒绝执行，页面白屏且控制台只留一条含糊的报错。</p>
 */
@Configuration
public class SpaWebFluxConfig {

    // v3.1: 原 apiPathForwardFilter（/v1/** -> /api/v1/** 路径改写）已移除。
    // 该改写会把 OpenAI 标准路径转发给 UniversalController，但改写后请求体在解码阶段已为空
    // （日志: DecodingException "No content to map due to end-of-input"），导致 /v1/chat/completions
    // 恒返回 400；同时它也使新增的 OpenAiNativeController 无法被路由到。
    // 现在 /v1/** 由 OpenAiNativeController 直接提供服务（原生 JSON/SSE，无 RouterResponse 包裹），
    // 安全策略仍由 SecurityConfiguration 的 /v1/** authenticated() 规则约束。

    /** 构建产物目录：其中文件名均带 Vite 生成的内容哈希. */
    private static final String BUILD_ASSET_PREFIX = "/admin/assets/";

    /** 带哈希产物的缓存期（与主流 SPA 部署口径一致）. */
    private static final Duration ASSET_CACHE_TTL = Duration.ofDays(365);

    /**
     * 配置 SPA 资源路由
     *
     * <p>处理 /admin 路径的请求：静态资源存在则按扩展名返回，否则回落 index.html
     * （让 Vue Router 处理前端路由）；但 /admin/assets/** 下缺失的资源直接 404。</p>
     *
     * @return SPA 路由
     */
    @Bean
    @Order(-1)
    public RouterFunction<ServerResponse> spaRouterFunction() {
        return RouterFunctions.route()
                // 处理 favicon.ico 请求，返回 204 No Content 避免认证异常
                .GET("/favicon.ico", request -> ServerResponse.noContent().build())
                .GET("/admin", request -> serveIndexHtml())
                .GET("/admin/**", request -> {
                    final String path = request.path();

                    // 尝试加载对应的静态资源
                    final ClassPathResource resource = new ClassPathResource("static" + path);
                    if (resource.exists() && resource.isReadable()) {
                        return ServerResponse.ok()
                                .contentType(getContentType(path))
                                .cacheControl(cacheControlFor(path))
                                .body(Mono.just(resource), ClassPathResource.class);
                    }

                    // 构建产物缺失时不能伪装成 index.html（MIME 不符会让脚本被拒绝执行）
                    if (isBuildAsset(path)) {
                        return ServerResponse.notFound().build();
                    }

                    return serveIndexHtml();
                })
                .build();
    }

    /**
     * 返回 SPA 入口页面（必须每次回源校验缓存）.
     *
     * @return index.html，缺失时为 404
     */
    private Mono<ServerResponse> serveIndexHtml() {
        final ClassPathResource indexHtml = new ClassPathResource("static/admin/index.html");
        if (!indexHtml.exists()) {
            return ServerResponse.notFound().build();
        }
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .body(Mono.just(indexHtml), ClassPathResource.class);
    }

    /**
     * 判断是否为带内容哈希的构建产物.
     *
     * @param path 请求路径
     * @return {@code true} 表示位于构建产物目录
     */
    private boolean isBuildAsset(final String path) {
        return path.startsWith(BUILD_ASSET_PREFIX);
    }

    /**
     * 按路径给出缓存策略.
     *
     * @param path 请求路径
     * @return 构建产物为一年期 immutable，其余为 no-store
     */
    private CacheControl cacheControlFor(final String path) {
        return isBuildAsset(path)
                ? CacheControl.maxAge(ASSET_CACHE_TTL).cachePublic().immutable()
                : CacheControl.noStore();
    }

    /**
     * 根据文件扩展名获取 Content-Type
     *
     * @param path 请求路径
     * @return 对应媒体类型，未知扩展名按 HTML 处理（仅 SPA 兜底路径）
     */
    private MediaType getContentType(final String path) {
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
