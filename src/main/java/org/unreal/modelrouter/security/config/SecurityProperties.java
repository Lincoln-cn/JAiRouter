package org.unreal.modelrouter.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.security.model.ApiKeyInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全配置属性类
 * 映射application.yml中的jairouter.security配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "jairouter.security")
public class SecurityProperties {
    
    /**
     * 是否启用安全功能
     */
    private boolean enabled = false;
    
    /**
     * API Key配置
     */
    private ApiKeyConfig apiKey = new ApiKeyConfig();
    
    /**
     * JWT配置
     */
    private JwtConfig jwt = new JwtConfig();
    
    /**
     * 数据脱敏配置
     */
    private SanitizationConfig sanitization = new SanitizationConfig();
    
    /**
     * 审计配置
     */
    private AuditConfig audit = new AuditConfig();
    
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
        private String headerName = "X-API-Key";
        
        /**
         * 预配置的API Key列表
         */
        private List<ApiKeyInfo> keys = new ArrayList<>();
        
        /**
         * 默认过期天数
         */
        private long defaultExpirationDays = 365;
        
        /**
         * 是否启用缓存
         */
        private boolean cacheEnabled = true;
        
        /**
         * 缓存过期时间（秒）
         */
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
         * JWT签名密钥
         */
        private String secret;
        
        /**
         * 签名算法
         */
        private String algorithm = "HS256";
        
        /**
         * 令牌过期时间（分钟）
         */
        private long expirationMinutes = 60;
        
        /**
         * 刷新令牌过期时间（天）
         */
        private long refreshExpirationDays = 7;
        
        /**
         * 令牌发行者
         */
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
        private RequestSanitization request = new RequestSanitization();
        
        /**
         * 响应脱敏配置
         */
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
            private String maskingChar = "*";
            
            /**
             * 白名单用户列表
             */
            private List<String> whitelistUsers = new ArrayList<>();
            
            /**
             * 是否记录脱敏操作
             */
            private boolean logSanitization = true;
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
            private String maskingChar = "*";
            
            /**
             * 是否记录脱敏操作
             */
            private boolean logSanitization = true;
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
        private int retentionDays = 90;
        
        /**
         * 是否启用实时告警
         */
        private boolean alertEnabled = false;
        
        /**
         * 告警阈值配置
         */
        private AlertThresholds alertThresholds = new AlertThresholds();
        
        /**
         * 告警阈值配置
         */
        @Data
        public static class AlertThresholds {
            /**
             * 认证失败次数阈值（每分钟）
             */
            private int authFailuresPerMinute = 10;
            
            /**
             * 脱敏操作次数阈值（每分钟）
             */
            private int sanitizationOperationsPerMinute = 100;
        }
    }
}