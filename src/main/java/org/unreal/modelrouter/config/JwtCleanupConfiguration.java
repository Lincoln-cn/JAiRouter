package org.unreal.modelrouter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * JWT清理服务配置类
 * 配置清理任务所需的调度器和相关组件
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "jairouter.security.jwt.persistence.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class JwtCleanupConfiguration {
    
    /**
     * 配置JWT清理任务的专用调度器
     * 使用独立的线程池避免与其他定时任务冲突
     */
    @Bean("jwtCleanupTaskScheduler")
    public TaskScheduler jwtCleanupTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2); // 设置较小的线程池，清理任务不需要太多并发
        scheduler.setThreadNamePrefix("jwt-cleanup-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
        log.info("Configured JWT cleanup task scheduler with pool size: {}", scheduler.getPoolSize());
        return scheduler;
    }
    
    /**
     * 清理任务配置属性
     */
    @Bean
    @ConditionalOnProperty(name = "jairouter.security.jwt.persistence.cleanup.enabled", havingValue = "true")
    public JwtCleanupProperties jwtCleanupProperties() {
        return new JwtCleanupProperties();
    }
    
    /**
     * JWT清理配置属性类
     */
    public static class JwtCleanupProperties {
        private boolean enabled = true;
        private String schedule = "0 0 2 * * ?"; // 每天凌晨2点
        private int retentionDays = 30;
        private int batchSize = 1000;
        private int maxRetries = 3;
        private long retryDelayMs = 1000;
        
        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getSchedule() { return schedule; }
        public void setSchedule(String schedule) { this.schedule = schedule; }
        
        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
        
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        
        public long getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    }
}