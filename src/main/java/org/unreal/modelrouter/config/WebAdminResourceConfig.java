package org.unreal.modelrouter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.resource.PathResourceResolver;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Web 管理界面静态资源配置
 * 配置前端静态资源的访问路径和缓存策略
 */
@Slf4j
@Configuration
public class WebAdminResourceConfig implements WebFluxConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("配置 Web 管理界面静态资源处理器");

        // 配置管理界面静态资源 - 包含 index.html 和 assets
        registry.addResourceHandler("/admin/**")
                .addResourceLocations("classpath:/static/admin/")
                .setCacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofHours(1)))
                .resourceChain(true)
                .addResolver(new SpaPathResourceResolver());

        log.info("Web 管理界面静态资源配置完成");
    }

    /**
     * SPA 路径解析器
     * 处理前端路由，将所有未匹配的路径重定向到 index.html
     */
    private static class SpaPathResourceResolver extends PathResourceResolver {

        @Override
        protected Mono<Resource> getResource(String resourcePath, Resource location) {
            // 首先尝试直接获取资源
            try {
                Resource requestedResource = location.createRelative(resourcePath);
                if (requestedResource.exists() && requestedResource.isReadable()) {
                    return Mono.just(requestedResource);
                }
            } catch (Exception e) {
                log.debug("无法创建资源路径：{}", resourcePath, e);
            }
            
            // 如果资源不存在，返回 index.html（用于 SPA 路由）
            ClassPathResource indexHtml = new ClassPathResource("static/admin/index.html");
            if (indexHtml.exists()) {
                log.debug("SPA fallback to index.html for path: {}", resourcePath);
                return Mono.just(indexHtml);
            }
            
            return Mono.empty();
        }
    }
}
