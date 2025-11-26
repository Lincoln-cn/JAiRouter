package org.unreal.modelrouter.security.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.security.service.JwtAccountService;

/**
 * JWT账户配置初始化器
 * 在应用启动时加载持久化的JWT账户配置
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DependsOn("storeManager")
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true")
public class JwtAccountInitializer {

    private final JwtAccountService jwtAccountService;

    @PostConstruct
    public void initJwtAccounts() {
        log.info("开始初始化JWT账户配置...");

        try {
            // 检查是否存在持久化配置
            if (jwtAccountService.hasPersistedAccountConfig()) {
                // 加载最新的持久化配置
                jwtAccountService.loadLatestJwtAccountConfig();
                log.info("JWT账户配置初始化完成 - 已加载持久化配置");
            } else {
                // 首次启动，将YAML配置保存为版本1
                jwtAccountService.initializeJwtAccountFromYaml();
                log.info("JWT账户配置初始化完成 - 已从YAML创建初始版本");
            }
        } catch (Exception e) {
            log.error("JWT账户配置初始化失败", e);
            // 不抛出异常，允许应用继续启动，使用YAML配置
        }
    }
}
