package org.unreal.modelrouter.config.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.unreal.modelrouter.common.filter.CachedBodyWebFilter;
import org.unreal.modelrouter.common.filter.RequestIdWebFilter;

/**
 * WebFilter 配置类
 * 
 * 负责配置和管理 WebFilter 的执行顺序，确保：
 * 1. CachedBodyWebFilter 优先级最高，在所有过滤器之前执行
 * 2. 其他过滤器按照正确的顺序执行
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class WebFilterConfiguration {
    
    /**
     * 注册缓存 body 的 WebFilter
     * 
     * 这个过滤器必须在所有其他过滤器之前执行，以确保请求体可以被重复读取。
     * 特别是在重试机制和 API-Key 验证过滤器之前。
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebFilter cachedBodyFilter() {
        log.info("注册 CachedBodyWebFilter，优先级: {}", Ordered.HIGHEST_PRECEDENCE);
        return new CachedBodyWebFilter();
    }

    /**
     * 注册请求 ID 回写过滤器（issue #724）
     *
     * <p>在响应侧统一写 {@code X-Request-Id}：优先复用入站请求头，其次复用 tracing 的 traceId，
     * 最后生成 UUID。排在 body 缓存与可写请求头之后，保证 401/400/5xx 等提前终止的响应也带上该头。</p>
     *
     * @return 请求 ID 过滤器
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    public WebFilter requestIdFilter() {
        log.info("注册 RequestIdWebFilter，优先级: {}", Ordered.HIGHEST_PRECEDENCE + 2);
        return new RequestIdWebFilter();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public WebFilter writeableHeaders() {
        log.info("注册 writeableHeaders WebFilter，优先级: {}", Ordered.HIGHEST_PRECEDENCE + 1);
        return (exchange, chain) -> {
            HttpHeaders writeableHeaders = HttpHeaders.writableHttpHeaders(
                    exchange.getRequest().getHeaders());
            ServerHttpRequestDecorator writeableRequest = new ServerHttpRequestDecorator(
                    exchange.getRequest()) {
                @Override
                public HttpHeaders getHeaders() {
                    return writeableHeaders;
                }
            };
            ServerWebExchange writeableExchange = exchange.mutate()
                .request(writeableRequest)
                .build();
            return chain.filter(writeableExchange);
        };
    }

}