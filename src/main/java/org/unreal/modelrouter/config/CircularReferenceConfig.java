package org.unreal.modelrouter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 循环依赖配置类
 * 
 * 用于解决应用启动时的循环依赖问题
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Configuration
public class CircularReferenceConfig {
    
    /**
     * 配置允许循环依赖
     * 
     * 注意：这只是临时解决方案，应该通过重构代码来消除循环依赖
     */
    @Bean
    public boolean allowCircularReferences() {
        // 这个Bean不会被使用，只是为了让Spring知道我们需要处理循环依赖
        System.setProperty("spring.main.allow-circular-references", "true");
        return true;
    }
}