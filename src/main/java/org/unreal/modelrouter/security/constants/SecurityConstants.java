package org.unreal.modelrouter.security.constants;

/**
 * 安全模块常量定义
 */
public final class SecurityConstants {
    
    private SecurityConstants() {
        // 工具类，禁止实例化
    }
    
    /**
     * 认证相关常量
     */
    public static final class Authentication {
        public static final String DEFAULT_API_KEY_HEADER = "X-API-Key";
        public static final String JWT_TOKEN_HEADER = "Authorization";
        public static final String JWT_TOKEN_PREFIX = "Bearer ";
        public static final String SECURITY_CONTEXT_KEY = "security.context";
        public static final String USER_ID_ATTRIBUTE = "security.userId";
        public static final String API_KEY_ID_ATTRIBUTE = "security.apiKeyId";
        public static final String PERMISSIONS_ATTRIBUTE = "security.permissions";
    }
    
    // 请求属性常量
    public static final String API_KEY_INFO_ATTRIBUTE = "security.apiKeyInfo";
    public static final String AUTHENTICATED_USER_ID = "security.authenticatedUserId";
    public static final String USER_PERMISSIONS = "security.userPermissions";
    
    /**
     * 审计事件类型常量
     */
    public static final class AuditEventTypes {
        public static final String AUTHENTICATION_SUCCESS = "AUTHENTICATION_SUCCESS";
        public static final String AUTHENTICATION_FAILURE = "AUTHENTICATION_FAILURE";
        public static final String AUTHORIZATION_SUCCESS = "AUTHORIZATION_SUCCESS";
        public static final String AUTHORIZATION_FAILURE = "AUTHORIZATION_FAILURE";
        public static final String SANITIZATION_APPLIED = "SANITIZATION_APPLIED";
        public static final String CONFIGURATION_CHANGED = "CONFIGURATION_CHANGED";
        public static final String API_KEY_CREATED = "API_KEY_CREATED";
        public static final String API_KEY_UPDATED = "API_KEY_UPDATED";
        public static final String API_KEY_DELETED = "API_KEY_DELETED";
        public static final String RULE_CREATED = "RULE_CREATED";
        public static final String RULE_UPDATED = "RULE_UPDATED";
        public static final String RULE_DELETED = "RULE_DELETED";
    }
    
    /**
     * 脱敏相关常量
     */
    public static final class Sanitization {
        public static final String DEFAULT_MASK_CHAR = "*";
        public static final String PHONE_PATTERN = "\\d{11}";
        public static final String ID_CARD_PATTERN = "\\d{18}";
        public static final String EMAIL_PATTERN = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}";
        public static final String CREDIT_CARD_PATTERN = "\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}";
        public static final int DEFAULT_VISIBLE_CHARS = 2;
    }
    
    /**
     * 缓存相关常量
     */
    public static final class Cache {
        public static final String API_KEY_CACHE_NAME = "security:apikeys";
        public static final String JWT_BLACKLIST_CACHE_NAME = "security:jwt:blacklist";
        public static final String SANITIZATION_RULES_CACHE_NAME = "security:sanitization:rules";
        public static final long DEFAULT_CACHE_TTL_SECONDS = 3600L;
    }
    
    /**
     * 配置相关常量
     */
    public static final class Configuration {
        public static final String SECURITY_CONFIG_PREFIX = "jairouter.security";
        public static final String API_KEY_CONFIG_PREFIX = "jairouter.security.api-key";
        public static final String JWT_CONFIG_PREFIX = "jairouter.security.jwt";
        public static final String SANITIZATION_CONFIG_PREFIX = "jairouter.security.sanitization";
        public static final String AUDIT_CONFIG_PREFIX = "jairouter.security.audit";
    }
    
    /**
     * 权限相关常量
     */
    public static final class Permissions {
        public static final String ADMIN = "admin";
        public static final String READ = "read";
        public static final String WRITE = "write";
        public static final String DELETE = "delete";
        public static final String CONFIG_MANAGE = "config:manage";
        public static final String AUDIT_VIEW = "audit:view";
    }
    
    /**
     * HTTP相关常量
     */
    public static final class Http {
        public static final String CONTENT_TYPE_JSON = "application/json";
        public static final String CONTENT_TYPE_TEXT = "text/plain";
        public static final String CONTENT_TYPE_XML = "application/xml";
        public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    }
    
    /**
     * 过滤器顺序常量
     */
    public static final class FilterOrder {
        public static final int API_KEY_FILTER_ORDER = -100;
        public static final int JWT_FILTER_ORDER = -99;
        public static final int SANITIZATION_FILTER_ORDER = -50;
    }
    
    /**
     * 过滤器顺序常量（向后兼容）
     */
    public static final int API_KEY_FILTER_ORDER = FilterOrder.API_KEY_FILTER_ORDER;
    public static final int JWT_FILTER_ORDER = FilterOrder.JWT_FILTER_ORDER;
    public static final int SANITIZATION_FILTER_ORDER = FilterOrder.SANITIZATION_FILTER_ORDER;
    
    /**
     * 默认配置值
     */
    public static final class Defaults {
        public static final long API_KEY_DEFAULT_EXPIRATION_DAYS = 365L;
        public static final long JWT_DEFAULT_EXPIRATION_MINUTES = 60L;
        public static final long JWT_REFRESH_DEFAULT_EXPIRATION_DAYS = 7L;
        public static final int AUDIT_DEFAULT_RETENTION_DAYS = 90;
        public static final int DEFAULT_ALERT_THRESHOLD_AUTH_FAILURES = 10;
        public static final int DEFAULT_ALERT_THRESHOLD_SANITIZATION_OPS = 100;
    }
}