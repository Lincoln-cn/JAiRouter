package org.unreal.modelrouter.persistence.jpa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA 配置类
 * v1.5.1: 替代 R2DBC 的配置
 */
@Configuration
@EnableJpaRepositories(basePackages = {"org.unreal.modelrouter.persistence.jpa.repository", "org.unreal.modelrouter.audit"})
@EnableTransactionManagement
public class JpaConfig {
    // JPA 配置通过 application.yml 完成
    // 此配置类主要用于启用 JPA Repository 扫描
}
