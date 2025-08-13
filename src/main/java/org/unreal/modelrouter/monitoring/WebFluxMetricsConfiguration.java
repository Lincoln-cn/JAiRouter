package org.unreal.modelrouter.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.filter.OrderedWebFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFlux指标配置类
 * 负责配置和注册WebFlux指标拦截器
 */
@Configuration
@Conditional(MonitoringEnabledCondition.class)
public class WebFluxMetricsConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(WebFluxMetricsConfiguration.class);

    /**
     * 注册WebFlux指标过滤器
     * 使用OrderedWebFilter确保过滤器在正确的顺序执行
     */
    @Bean
    public OrderedWebFilter webFluxMetricsFilter(WebFluxMetricsInterceptor interceptor) {
        logger.info("Registering WebFlux metrics filter");
        
        return new OrderedWebFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
                return interceptor.filter(exchange, chain);
            }

            @Override
            public int getOrder() {
                // 设置高优先级，确保在业务逻辑之前执行
                return Ordered.HIGHEST_PRECEDENCE + 1;
            }
        };
    }
}