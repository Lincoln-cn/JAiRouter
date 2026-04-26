package org.unreal.modelrouter.store.persistence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 状态持久化配置属性
 * 
 * v2.4.4: 三层退坡策略配置
 * 
 * 配置示例:
 * jairouter:
 *   persistence:
 *     redis:
 *       enabled: false
 *       host: localhost
 *       port: 6379
 *     h2:
 *       enabled: true
 *     file:
 *       enabled: true
 *       path: ./data/state
 *     recovery:
 *       enabled: true
 *       auto-on-startup: true
 *     fallback:
 *       auto-detect: true
 *       health-check-interval: 30s
 *       retry-interval: 10s
 * 
 * @author JAiRouter Team
 * @since 2.4.4
 */
@Component
@ConfigurationProperties(prefix = "jairouter.persistence")
public class StatePersistenceProperties {

    /**
     * Redis 存储配置
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * H2 数据库配置
     */
    private H2Config h2 = new H2Config();

    /**
     * 文件存储配置
     */
    private FileConfig file = new FileConfig();

    /**
     * 状态恢复配置
     */
    private RecoveryConfig recovery = new RecoveryConfig();

    /**
     * 退坡策略配置
     */
    private FallbackConfig fallback = new FallbackConfig();

    // Getters and Setters

    public RedisConfig getRedis() {
        return redis;
    }

    public void setRedis(RedisConfig redis) {
        this.redis = redis;
    }

    public H2Config getH2() {
        return h2;
    }

    public void setH2(H2Config h2) {
        this.h2 = h2;
    }

    public FileConfig getFile() {
        return file;
    }

    public void setFile(FileConfig file) {
        this.file = file;
    }

    public RecoveryConfig getRecovery() {
        return recovery;
    }

    public void setRecovery(RecoveryConfig recovery) {
        this.recovery = recovery;
    }

    public FallbackConfig getFallback() {
        return fallback;
    }

    public void setFallback(FallbackConfig fallback) {
        this.fallback = fallback;
    }

    /**
     * Redis 存储配置
     */
    public static class RedisConfig {
        /**
         * 是否启用 Redis 存储
         */
        private boolean enabled = false;

        /**
         * Redis 主机地址
         */
        private String host = "localhost";

        /**
         * Redis 端口
         */
        private int port = 6379;

        /**
         * Redis 数据库编号
         */
        private int database = 0;

        /**
         * Redis 密码
         */
        private String password = "";

        /**
         * 连接超时时间 (毫秒)
         */
        private int timeout = 3000;

        /**
         * 状态数据 TTL (天)
         */
        private int ttlDays = 7;

        // Getters and Setters

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = database;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public int getTtlDays() {
            return ttlDays;
        }

        public void setTtlDays(int ttlDays) {
            this.ttlDays = ttlDays;
        }
    }

    /**
     * H2 数据库配置
     */
    public static class H2Config {
        /**
         * 是否启用 H2 存储 (默认启用)
         */
        private boolean enabled = true;

        /**
         * 数据目录
         */
        private String dataDir = "./data";

        /**
         * 数据库名称
         */
        private String database = "jairouter_state";

        // Getters and Setters

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDataDir() {
            return dataDir;
        }

        public void setDataDir(String dataDir) {
            this.dataDir = dataDir;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }
    }

    /**
     * 文件存储配置
     */
    public static class FileConfig {
        /**
         * 是否启用文件存储 (默认启用作为兜底)
         */
        private boolean enabled = true;

        /**
         * 文件存储路径
         */
        private String path = "./data/state";

        /**
         * 是否压缩存储
         */
        private boolean compress = false;

        // Getters and Setters

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isCompress() {
            return compress;
        }

        public void setCompress(boolean compress) {
            this.compress = compress;
        }
    }

    /**
     * 状态恢复配置
     */
    public static class RecoveryConfig {
        /**
         * 是否启用状态恢复
         */
        private boolean enabled = true;

        /**
         * 启动时自动恢复
         */
        private boolean autoOnStartup = true;

        /**
         * 恢复超时时间 (毫秒)
         */
        private int timeout = 10000;

        /**
         * 是否并行恢复
         */
        private boolean parallel = false;

        // Getters and Setters

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAutoOnStartup() {
            return autoOnStartup;
        }

        public void setAutoOnStartup(boolean autoOnStartup) {
            this.autoOnStartup = autoOnStartup;
        }

        public int getTimeout() {
            return timeout;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public boolean isParallel() {
            return parallel;
        }

        public void setParallel(boolean parallel) {
            this.parallel = parallel;
        }
    }

    /**
     * 退坡策略配置
     */
    public static class FallbackConfig {
        /**
         * 是否自动检测存储层可用性
         */
        private boolean autoDetect = true;

        /**
         * 健康检查间隔 (秒)
         */
        private int healthCheckInterval = 30;

        /**
         * 重试间隔 (秒)
         */
        private int retryInterval = 10;

        /**
         * 最大重试次数
         */
        private int maxRetries = 3;

        /**
         * Redis 恢复后是否同步数据
         */
        private boolean syncOnRecovery = true;

        // Getters and Setters

        public boolean isAutoDetect() {
            return autoDetect;
        }

        public void setAutoDetect(boolean autoDetect) {
            this.autoDetect = autoDetect;
        }

        public int getHealthCheckInterval() {
            return healthCheckInterval;
        }

        public void setHealthCheckInterval(int healthCheckInterval) {
            this.healthCheckInterval = healthCheckInterval;
        }

        public int getRetryInterval() {
            return retryInterval;
        }

        public void setRetryInterval(int retryInterval) {
            this.retryInterval = retryInterval;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public boolean isSyncOnRecovery() {
            return syncOnRecovery;
        }

        public void setSyncOnRecovery(boolean syncOnRecovery) {
            this.syncOnRecovery = syncOnRecovery;
        }
    }
}