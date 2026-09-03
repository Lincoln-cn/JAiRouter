package org.unreal.modelrouter.auth.security.permission;

import java.util.List;

/**
 * RBAC 权限码常量（v2.9.8 数据驱动权限体系）
 *
 * <p>权限码格式：{@code module:resource:action}，其中 action ∈ read / write / manage。
 * 清单见开发计划2026 L1132（42 权限码，v2.9.10 扩展至 44），本文按该清单逐条枚举。
 *
 * <p>用途：
 * <ul>
 *   <li>{@link RolePermissionService} 的 ADMIN 短路返回全量权限码</li>
 *   <li>{@link PermissionRuleRegistry} 登记 URL 规则时引用权限码</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
public final class PermissionCodes {

    private PermissionCodes() {
    }

    // ==================== overview（概览） ====================

    /** 概览仪表盘 */
    public static final String OVERVIEW_DASHBOARD_READ = "overview:dashboard:read";

    // ==================== config（配置，10 个资源 × read/write） ====================

    /** 服务配置-读 */
    public static final String CONFIG_SERVICES_READ = "config:services:read";
    /** 服务配置-写 */
    public static final String CONFIG_SERVICES_WRITE = "config:services:write";
    /** 实例配置-读 */
    public static final String CONFIG_INSTANCES_READ = "config:instances:read";
    /** 实例配置-写 */
    public static final String CONFIG_INSTANCES_WRITE = "config:instances:write";
    /** 配置版本-读 */
    public static final String CONFIG_VERSIONS_READ = "config:versions:read";
    /** 配置版本-写 */
    public static final String CONFIG_VERSIONS_WRITE = "config:versions:write";
    /** 状态持久化-读 */
    public static final String CONFIG_PERSISTENCE_READ = "config:persistence:read";
    /** 状态持久化-写 */
    public static final String CONFIG_PERSISTENCE_WRITE = "config:persistence:write";
    /** 适配器配置-读 */
    public static final String CONFIG_ADAPTERS_READ = "config:adapters:read";
    /** 适配器配置-写 */
    public static final String CONFIG_ADAPTERS_WRITE = "config:adapters:write";
    /** 规则配置-读 */
    public static final String CONFIG_RULES_READ = "config:rules:read";
    /** 规则配置-写 */
    public static final String CONFIG_RULES_WRITE = "config:rules:write";
    /** 资源池配置-读 */
    public static final String CONFIG_POOLS_READ = "config:pools:read";
    /** 资源池配置-写 */
    public static final String CONFIG_POOLS_WRITE = "config:pools:write";
    /** 熔断器配置-读 */
    public static final String CONFIG_CIRCUITBREAKER_READ = "config:circuitbreaker:read";
    /** 熔断器配置-写 */
    public static final String CONFIG_CIRCUITBREAKER_WRITE = "config:circuitbreaker:write";
    /** 调用历史配置-读 */
    public static final String CONFIG_CALLHISTORY_READ = "config:callhistory:read";
    /** 调用历史配置-写 */
    public static final String CONFIG_CALLHISTORY_WRITE = "config:callhistory:write";
    /** 响应缓存配置-写 */
    public static final String CONFIG_CACHE_WRITE = "config:cache:write";
    /** 配置校验-读 */
    public static final String CONFIG_VALIDATION_READ = "config:validation:read";
    /** 配置校验-写 */
    public static final String CONFIG_VALIDATION_WRITE = "config:validation:write";

    // ==================== lb / cb / rl（流量治理） ====================

    /** 负载均衡监控-读 */
    public static final String LB_MONITORING_READ = "lb:monitoring:read";
    /** 负载均衡策略配置-写 */
    public static final String LB_CONFIG_WRITE = "lb:config:write";
    /** 熔断器监控-读 */
    public static final String CB_MONITORING_READ = "cb:monitoring:read";
    /** 熔断器历史-读 */
    public static final String CB_HISTORY_READ = "cb:history:read";
    /** 限流监控-读 */
    public static final String RL_MONITORING_READ = "rl:monitoring:read";

    // ==================== callhistory（调用历史） ====================

    /** 调用历史查看 */
    public static final String CALLHISTORY_VIEW = "callhistory:view";

    // ==================== monitoring（监控，5 个资源 × read） ====================

    /** 指标监控-读 */
    public static final String MONITORING_METRICS_READ = "monitoring:metrics:read";
    /** 慢查询分析-读 */
    public static final String MONITORING_SLOWQUERY_READ = "monitoring:slowquery:read";
    /** Token 用量-读 */
    public static final String MONITORING_TOKENUSAGE_READ = "monitoring:tokenusage:read";
    /** 模型统计-读 */
    public static final String MONITORING_MODELSTATS_READ = "monitoring:modelstats:read";
    /** 路由监控-读 */
    public static final String MONITORING_ROUTING_READ = "monitoring:routing:read";

    // ==================== tracing（链路追踪） ====================

    /** 追踪仪表盘-读 */
    public static final String TRACING_DASHBOARD_READ = "tracing:dashboard:read";
    /** 追踪检索-读 */
    public static final String TRACING_SEARCH_READ = "tracing:search:read";
    /** 追踪配置-管理 */
    public static final String TRACING_CONFIG_MANAGE = "tracing:config:manage";

    // ==================== security（安全管理） ====================

    /** API Key 管理 */
    public static final String SECURITY_APIKEYS_MANAGE = "security:apikeys:manage";
    /** JWT 令牌管理 */
    public static final String SECURITY_JWTTOKENS_MANAGE = "security:jwttokens:manage";
    /** 黑名单管理 */
    public static final String SECURITY_BLACKLIST_MANAGE = "security:blacklist:manage";
    /** 安全审计-读 */
    public static final String SECURITY_AUDIT_READ = "security:audit:read";

    // ==================== system（系统管理） ====================

    /** 账户管理 */
    public static final String SYSTEM_ACCOUNTS_MANAGE = "system:accounts:manage";
    /** 权限管理 */
    public static final String SYSTEM_PERMISSIONS_MANAGE = "system:permissions:manage";

    // ==================== ai（AI 试验场） ====================

    /** AI 试验场使用 */
    public static final String AI_PLAYGROUND_USE = "ai:playground:use";

    // ==================== actuator（基础设施） ====================

    /** Actuator 管理 */
    public static final String ACTUATOR_ADMIN_MANAGE = "actuator:admin:manage";

    /**
     * 全量权限码（ADMIN 角色超集）。
     * 按开发计划2026 L1132 逐条枚举，ADMIN 短路时返回本列表。
     */
    public static final List<String> ALL_PERMISSION_CODES = List.of(
            OVERVIEW_DASHBOARD_READ,
            CONFIG_SERVICES_READ, CONFIG_SERVICES_WRITE,
            CONFIG_INSTANCES_READ, CONFIG_INSTANCES_WRITE,
            CONFIG_VERSIONS_READ, CONFIG_VERSIONS_WRITE,
            CONFIG_PERSISTENCE_READ, CONFIG_PERSISTENCE_WRITE,
            CONFIG_ADAPTERS_READ, CONFIG_ADAPTERS_WRITE,
            CONFIG_RULES_READ, CONFIG_RULES_WRITE,
            CONFIG_POOLS_READ, CONFIG_POOLS_WRITE,
            CONFIG_CIRCUITBREAKER_READ, CONFIG_CIRCUITBREAKER_WRITE,
            CONFIG_CALLHISTORY_READ, CONFIG_CALLHISTORY_WRITE,
            CONFIG_CACHE_WRITE,
            CONFIG_VALIDATION_READ, CONFIG_VALIDATION_WRITE,
            LB_MONITORING_READ, LB_CONFIG_WRITE,
            CB_MONITORING_READ, CB_HISTORY_READ,
            RL_MONITORING_READ,
            CALLHISTORY_VIEW,
            MONITORING_METRICS_READ, MONITORING_SLOWQUERY_READ,
            MONITORING_TOKENUSAGE_READ, MONITORING_MODELSTATS_READ,
            MONITORING_ROUTING_READ,
            TRACING_DASHBOARD_READ, TRACING_SEARCH_READ, TRACING_CONFIG_MANAGE,
            SECURITY_APIKEYS_MANAGE, SECURITY_JWTTOKENS_MANAGE,
            SECURITY_BLACKLIST_MANAGE, SECURITY_AUDIT_READ,
            SYSTEM_ACCOUNTS_MANAGE, SYSTEM_PERMISSIONS_MANAGE,
            AI_PLAYGROUND_USE,
            ACTUATOR_ADMIN_MANAGE
    );
}
