package org.unreal.modelrouter.security.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.unreal.modelrouter.security.model.ApiKeyInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全配置属性类
 * 映射application.yml中的jairouter.security配置
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "jairouter.security" , ignoreUnknownFields  = true ,ignoreInvalidFields = true)
public class SecurityProperties {
    
    /**
     * 是否启用安全功能
     */
    private boolean enabled = false;
    
    /**
     * API Key配置
     */
    @Valid
    @NotNull
    private ApiKeyConfig apiKey = new ApiKeyConfig();
    
    /**
     * JWT配置
     */
    @Valid
    @NotNull
    private JwtConfig jwt = new JwtConfig();
    
    /**
     * 数据脱敏配置
     */
    @Valid
    @NotNull
    private SanitizationConfig sanitization = new SanitizationConfig();
    
    /**
     * 审计配置
     */
    @Valid
    @NotNull
    private AuditConfig audit = new AuditConfig();
    
    /**
     * 缓存配置
     */
    @Valid
    @NotNull
    private CacheConfig cache = new CacheConfig();
    
    /**
     * API Key配置类
     */
    @Data
    public static class ApiKeyConfig {
        /**
         * 是否启用API Key认证
         */
        private boolean enabled = true;
        
        /**
         * API Key请求头名称
         */
        @NotBlank
        @Size(min = 1, max = 100)
        private String headerName = "X-API-Key";
        
        /**
         * 预配置的API Key列表
         */
        @Valid
        private List<ApiKeyInfo> keys = new ArrayList<>();
        
        /**
         * 默认过期天数
         */
        @Min(1)
        @Max(3650)
        private long defaultExpirationDays = 365;
        
        /**
         * 是否启用缓存
         */
        private boolean cacheEnabled = true;
        
        /**
         * 缓存过期时间（秒）
         */
        @Min(60)
        @Max(86400)
        private long cacheExpirationSeconds = 3600;
    }
    
    /**
     * JWT配置类
     */
    @Data
    public static class JwtConfig {
        /**
         * 是否启用JWT认证
         */
        private boolean enabled = false;
        
        /**
         * JWT令牌请求头名称
         */
        @NotBlank
        @Size(min = 1, max = 100)
        private String jwtHeader = "Jairouter_Token";
        
        /**
         * JWT签名密钥
         */
        @Size(min = 32, message = "JWT密钥长度至少32个字符")
        private String secret;
        
        /**
         * 签名算法
         */
        @NotBlank
        @Pattern(regexp = "^(HS256|HS384|HS512|RS256|RS384|RS512)$", message = "不支持的JWT算法")
        private String algorithm = "HS256";
        
        /**
         * 令牌过期时间（分钟）
         */
        @Min(1)
        @Max(1440)
        private long expirationMinutes = 60;
        
        /**
         * 刷新令牌过期时间（天）
         */
        @Min(1)
        @Max(30)
        private long refreshExpirationDays = 7;
        
        /**
         * 令牌发行者
         */
        @NotBlank
        @Size(min = 1, max = 100)
        private String issuer = "jairouter";
        
        /**
         * 是否启用令牌黑名单
         */
        private boolean blacklistEnabled = true;
    }
    
    /**
     * 数据脱敏配置类
     */
    @Data
    public static class SanitizationConfig {
        /**
         * 请求脱敏配置
         */
        @Valid
        @NotNull
        private RequestSanitization request = new RequestSanitization();
        
        /**
         * 响应脱敏配置
         */
        @Valid
        @NotNull
        private ResponseSanitization response = new ResponseSanitization();
        
        /**
         * 请求脱敏配置
         */
        @Data
        public static class RequestSanitization {
            /**
             * 是否启用请求脱敏
             */
            private boolean enabled = true;
            
            /**
             * 敏感词列表
             */
            private List<String> sensitiveWords = new ArrayList<>();
            
            /**
             * PII数据模式列表
             */
            private List<String> piiPatterns = new ArrayList<>();
            
            /**
             * 掩码字符
             */
            @NotBlank
            @Size(min = 1, max = 5)
            private String maskingChar = "*";
            
            /**
             * 白名单用户列表
             */
            private List<String> whitelistUsers = new ArrayList<>();
            
            /**
             * 是否记录脱敏操作
             */
            private boolean logSanitization = true;
            
            /**
             * 脱敏失败时是否中断请求处理
             */
            private boolean failOnError = false;
        }
        
        /**
         * 响应脱敏配置
         */
        @Data
        public static class ResponseSanitization {
            /**
             * 是否启用响应脱敏
             */
            private boolean enabled = true;
            
            /**
             * 敏感词列表
             */
            private List<String> sensitiveWords = new ArrayList<>();
            
            /**
             * PII数据模式列表
             */
            private List<String> piiPatterns = new ArrayList<>();
            
            /**
             * 掩码字符
             */
            @NotBlank
            @Size(min = 1, max = 5)
            private String maskingChar = "*";
            
            /**
             * 是否记录脱敏操作
             */
            private boolean logSanitization = true;
            
            /**
             * 脱敏失败时是否中断响应处理
             */
            private boolean failOnError = false;
        }
    }
    
    /**
     * 审计配置类
     */
    @Data
    public static class AuditConfig {
        /**
         * 是否启用审计
         */
        private boolean enabled = true;
        
        /**
         * 日志级别
         */
        @NotBlank
        @Pattern(regexp = "^(TRACE|DEBUG|INFO|WARN|ERROR)$", message = "不支持的日志级别")
        private String logLevel = "INFO";
        
        /**
         * 是否包含请求体
         */
        private boolean includeRequestBody = false;
        
        /**
         * 是否包含响应体
         */
        private boolean includeResponseBody = false;
        
        /**
         * 审计日志保留天数
         */
        @Min(1)
        @Max(3650)
        private int retentionDays = 90;
        
        /**
         * 是否启用实时告警
         */
        private boolean alertEnabled = false;
        
        /**
         * 告警阈值配置
         */
        @Valid
        @NotNull
        private AlertThresholds alertThresholds = new AlertThresholds();
        
        /**
         * 告警阈值配置
         */
        @Data
        public static class AlertThresholds {
            /**
             * 认证失败次数阈值（每分钟）
             */
            @Min(1)
            @Max(1000)
            private int authFailuresPerMinute = 10;
            
            /**
             * 脱敏操作次数阈值（每分钟）
             */
            @Min(1)
            @Max(10000)
            private int sanitizationOperationsPerMinute = 100;
        }
    }
    
    /**
     * 缓存配置类
     */
    @Data
    public static class CacheConfig {
        /**
         * 是否启用缓存
         */
        private boolean enabled = true;
        
        /**
         * Redis配置
         */
        @Valid
        @NotNull
        private RedisConfig redis = new RedisConfig();
        
        /**
         * 内存缓存配置
         */
        @Valid
        @NotNull
        private InMemoryConfig inMemory = new InMemoryConfig();
        
        /**
         * Redis缓存配置
         */
        @Data
        public static class RedisConfig {
            /**
             * 是否启用Redis缓存
             */
            private boolean enabled = false;
            
            /**
             * Redis主机地址
             */
            @NotBlank
            private String host = "localhost";
            
            /**
             * Redis端口
             */
            @Min(1)
            @Max(65535)
            private int port = 6379;
            
            /**
             * Redis密码
             */
            private String password;
            
            /**
             * Redis数据库索引
             */
            @Min(0)
            @Max(15)
            private int database = 0;
            
            /**
             * 连接超时时间（毫秒）
             */
            @Min(100)
            @Max(30000)
            private int connectionTimeout = 2000;
            
            /**
             * 读取超时时间（毫秒）
             */
            @Min(100)
            @Max(30000)
            private int readTimeout = 2000;
            
            /**
             * 缓存键前缀
             */
            @NotBlank
            private String keyPrefix = "jairouter:security:";
            
            /**
             * 默认过期时间（秒）
             */
            @Min(60)
            @Max(86400)
            private long defaultTtlSeconds = 3600;
        }
        
        /**
         * 内存缓存配置
         */
        @Data
        public static class InMemoryConfig {
            /**
             * 最大缓存条目数
             */
            @Min(100)
            @Max(100000)
            private int maxSize = 10000;
            
            /**
             * 默认过期时间（秒）
             */
            @Min(60)
            @Max(86400)
            private long defaultTtlSeconds = 3600;
            
            /**
             * 清理过期条目的间隔时间（分钟）
             */
            @Min(1)
            @Max(60)
            private int cleanupIntervalMinutes = 5;
        }
    }
}