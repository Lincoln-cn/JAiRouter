package org.unreal.modelrouter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.unreal.modelrouter.filter.CachedBodyWebFilter;

/**
 * 过滤器顺序配置
 * 确保各个过滤器按正确的顺序执行
 */
@Slf4j
@Configuration
public class FilterOrderConfiguration {

    /**
     * 注册改进的缓存Body过滤器
     * 确保它在所有其他过滤器之前执行
     */
    @Bean
    public CachedBodyWebFilter cachedBodyWebFilter() {
        log.info("注册CachedBodyWebFilter，优先级: {}", Ordered.HIGHEST_PRECEDENCE);
        return new CachedBodyWebFilter();
    }

    /**
     * 如果需要，可以在这里定义其他过滤器的顺序
     */

    // Spring Security Authentication Filter 的优先级通常在 HIGHEST_PRECEDENCE + 1000 左右
    // Tracing Filter 的优先级通常在 HIGHEST_PRECEDENCE + 100 左右

    /**
     * 打印过滤器执行顺序信息
     */
    public void printFilterOrder() {
        log.info("过滤器执行顺序:");
        log.info("1. ImprovedCachedBodyWebFilter (优先级: {})", Ordered.HIGHEST_PRECEDENCE);
        log.info("2. TracingWebFilter (优先级: ~{})", Ordered.HIGHEST_PRECEDENCE + 100);
        log.info("3. SpringSecurityAuthenticationFilter (优先级: ~{})", Ordered.HIGHEST_PRECEDENCE + 1000);
        log.info("4. 其他业务过滤器...");
    }
}