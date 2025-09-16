package org.unreal.modelrouter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * 过滤器顺序配置
 * 确保各个过滤器按正确的顺序执行
 * 
 * 注意：CachedBodyWebFilter的bean定义已移至WebFilterConfiguration类中
 */
@Slf4j
@Configuration
public class FilterOrderConfiguration {

    /**
     * 打印过滤器执行顺序信息
     */
    public void printFilterOrder() {
        log.info("过滤器执行顺序:");
        log.info("1. CachedBodyWebFilter (优先级: {})", Integer.MIN_VALUE);
        log.info("2. writeableHeaders WebFilter (优先级: {})", Integer.MIN_VALUE + 1);
        log.info("3. TracingWebFilter (优先级: ~{})", Integer.MIN_VALUE + 100);
        log.info("4. SpringSecurityAuthenticationFilter (优先级: ~{})", Integer.MIN_VALUE + 1000);
        log.info("5. 其他业务过滤器...");
    }
}