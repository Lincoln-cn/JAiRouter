package org.unreal.modelrouter.security.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.unreal.modelrouter.security.service.DataSyncService;

/**
 * JWT数据恢复配置
 * 在应用启动时自动执行数据恢复操作
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.persistence.startup-recovery.enabled", havingValue = "true", matchIfMissing = true)
public class JwtDataRecoveryConfiguration implements ApplicationRunner {
    
    private final DataSyncService dataSyncService;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Starting JWT data recovery process...");
        
        try {
            DataSyncService.SyncResult result = dataSyncService.performStartupRecovery().block();
            
            if (result != null) {
                if (result.isSuccess()) {
                    log.info("JWT data recovery completed successfully: {}", result);
                } else {
                    log.warn("JWT data recovery completed with issues: {}", result);
                }
            } else {
                log.warn("JWT data recovery returned null result");
            }
            
        } catch (Exception e) {
            log.error("JWT data recovery failed during startup: {}", e.getMessage(), e);
            // 不抛出异常，避免影响应用启动
        }
    }
}