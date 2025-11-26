package org.unreal.modelrouter.security.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.security.service.H2JwtBlacklistService;

/**
 * JWT 黑名单初始化器
 * 在应用启动时从 H2 数据库恢复黑名单到本地缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(H2JwtBlacklistService.class)
@ConditionalOnProperty(name = "jairouter.security.jwt.blacklist-enabled", havingValue = "true", matchIfMissing = true)
public class JwtBlacklistInitializer implements ApplicationRunner {
    
    private final H2JwtBlacklistService blacklistService;
    
    @Override
    public void run(ApplicationArguments args) {
        log.info("开始从 H2 数据库恢复 JWT 黑名单...");
        
        blacklistService.loadBlacklistFromH2()
                .doOnSuccess(v -> log.info("JWT 黑名单恢复完成"))
                .doOnError(e -> log.error("JWT 黑名单恢复失败: {}", e.getMessage(), e))
                .subscribe();
    }
}
