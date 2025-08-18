package org.unreal.modelrouter.security.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 安全模块自动配置类
 * 当jairouter.security.enabled=true时启用安全功能
 */
@Configuration
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true")
@EnableConfigurationProperties(SecurityProperties.class)
@Import({
    SecurityConfiguration.class
})
public class SecurityAutoConfiguration {
    
    // 基础配置类，具体的Bean配置将在后续任务中实现
}