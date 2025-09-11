package org.unreal.modelrouter.security.constants;

/**
 * 安全模块常量定义
 */
public final class SecurityConstants {
    
    private SecurityConstants() {
        // 工具类，禁止实例化
    }

    
    // 请求属性常量
    public static final String API_KEY_INFO_ATTRIBUTE = "security.apiKeyInfo";
    public static final String AUTHENTICATED_USER_ID = "security.authenticatedUserId";
    public static final String USER_PERMISSIONS = "security.userPermissions";
    
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

}