package org.unreal.modelrouter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.resource.PathResourceResolver;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Web管理界面静态资源配置
 * 配置前端静态资源的访问路径和缓存策略
 */
@Slf4j
@Configuration
public class WebAdminResourceConfig implements WebFluxConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("配置Web管理界面静态资源处理器");
        
        // 配置管理界面静态资源
        registry.addResourceHandler("/admin/**")
                .addResourceLocations("classpath:/static/admin/")
                .setCacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofHours(1)))
                .resourceChain(true)
                .addResolver(new SpaPathResourceResolver());
        
        // 配置管理界面资源文件（CSS、JS、图片等）
        registry.addResourceHandler("/admin/assets/**")
                .addResourceLocations("classpath:/static/admin/assets/")
                .setCacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofHours(24)));
        
        log.info("Web管理界面静态资源配置完成");
    }
    
    /**
     * SPA路径解析器
     * 处理前端路由，将所有未匹配的路径重定向到index.html
     */
    private static class SpaPathResourceResolver extends PathResourceResolver {
        
        @Override
        protected Mono<Resource> getResource(String resourcePath, Resource location) {
            return super.getResource(resourcePath, location)
                    .switchIfEmpty(super.getResource("index.html", location));
        }
    }
}