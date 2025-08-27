package org.unreal.modelrouter.tracing.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.unreal.modelrouter.sanitization.SanitizationService;
import org.unreal.modelrouter.tracing.encryption.TracingEncryptionService;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;
import org.unreal.modelrouter.tracing.sanitization.TracingSanitizationService;
import org.unreal.modelrouter.tracing.security.TracingSecurityManager;

import jakarta.annotation.PostConstruct;

/**
 * 追踪安全功能自动配置类
 * 
 * 自动配置分布式追踪系统的安全功能，包括：
 * - 追踪数据脱敏服务
 * - 追踪安全管理器
 * - 追踪数据加密服务
 * - 安全功能的协调和初始化
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(TracingConfiguration.class)
@ConditionalOnProperty(name = "jairouter.tracing.security.enabled", havingValue = "true", matchIfMissing = true)
@Import({TracingSecurityConfiguration.class})
public class TracingSecurityAutoConfiguration {
    
    /**
     * 配置追踪数据脱敏服务
     */
    @Bean
    @ConditionalOnProperty(name = "jairouter.tracing.security.sanitization.enabled", havingValue = "true", matchIfMissing = true)
    public TracingSanitizationService tracingSanitizationService(
            SanitizationService sanitizationService,
            TracingConfiguration tracingConfiguration,
            StructuredLogger structuredLogger) {
        
        TracingSanitizationService service = new TracingSanitizationService(
                sanitizationService, tracingConfiguration, structuredLogger);
        
        log.info("配置追踪数据脱敏服务");
        return service;
    }
    
    /**
     * 配置追踪安全管理器
     */
    @Bean
    @ConditionalOnProperty(name = "jairouter.tracing.security.access-control.enabled", havingValue = "true", matchIfMissing = true)
    public TracingSecurityManager tracingSecurityManager(
            TracingConfiguration tracingConfiguration,
            StructuredLogger structuredLogger) {
        
        TracingSecurityManager manager = new TracingSecurityManager(tracingConfiguration, structuredLogger);
        
        log.info("配置追踪安全管理器");
        return manager;
    }
    
    /**
     * 配置追踪数据加密服务
     */
    @Bean
    @ConditionalOnProperty(name = "jairouter.tracing.security.encryption.enabled", havingValue = "true", matchIfMissing = true)
    public TracingEncryptionService tracingEncryptionService(
            TracingConfiguration tracingConfiguration,
            StructuredLogger structuredLogger) {
        
        TracingEncryptionService service = new TracingEncryptionService(tracingConfiguration, structuredLogger);
        
        log.info("配置追踪数据加密服务");
        return service;
    }
    
    /**
     * 配置后初始化
     */
    @PostConstruct
    public void initializeSecurityFeatures() {
        log.info("追踪安全功能自动配置完成");
        log.info("已启用的安全功能: 数据脱敏、访问控制、数据加密");
    }
}