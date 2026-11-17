package org.unreal.modelrouter.auth.security.permission;

import java.util.List;

/**
 * RBAC 权限码常量（v2.9.8 数据驱动权限体系）
 *
 * <p>权限码格式：{@code module:resource:action}，其中 action ∈ read / write / manage。
 * 以 {@link #ALL_PERMISSION_CODES} 为权威全集（v2.9.10 起持续扩展，含 PII
 * {@code security:sanitization:manage} 等）。
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

    // ==================== config（配置，12 个资源 × read/write） ====================

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
    /** 响应缓存配置-读 */
    public static final String CONFIG_CACHE_READ = "config:cache:read";
    /** 响应缓存配置-写 */
    public static final String CONFIG_CACHE_WRITE = "config:cache:write";
    /** 配置校验-读 */
    public static final String CONFIG_VALIDATION_READ = "config:validation:read";
    /** 配置校验-写 */
    public static final String CONFIG_VALIDATION_WRITE = "config:validation:write";
    /** 配额配置-读 */
    public static final String CONFIG_QUOTA_READ = "config:quota:read";
    /** 配额配置-写 */
    public static final String CONFIG_QUOTA_WRITE = "config:quota:write";
    /** 限流配置-读（API Key 级 / 终端用户级限流的开关与阈值） */
    public static final String CONFIG_RATELIMIT_READ = "config:ratelimit:read";
    /** 限流配置-写 */
    public static final String CONFIG_RATELIMIT_WRITE = "config:ratelimit:write";
    /** 集群配置-读（跨副本配置同步、成员发现、上游健康共享的开关与阈值） */
    public static final String CONFIG_CLUSTER_READ = "config:cluster:read";
    /** 集群配置-写 */
    public static final String CONFIG_CLUSTER_WRITE = "config:cluster:write";
    /** 健康探针配置-读（就绪探针依赖聚合、JWT Redis 故障判定） */
    public static final String CONFIG_HEALTH_READ = "config:health:read";
    /** 健康探针配置-写 */
    public static final String CONFIG_HEALTH_WRITE = "config:health:write";

    // ==================== lb / cb / rl（流量治理） ====================

    /** 负载均衡监控-读 */
    public static final String LB_MONITORING_READ = "lb:monitoring:read";
    /** 负载均衡策略配置-读（#567：此前查看策略配置只能靠写码） */
    public static final String LB_CONFIG_READ = "lb:config:read";
    /** 负载均衡策略配置-写 */
    public static final String LB_CONFIG_WRITE = "lb:config:write";
    /** 熔断器监控-读 */
    public static final String CB_MONITORING_READ = "cb:monitoring:read";
    /** 熔断器历史-读 */
    public static final String CB_HISTORY_READ = "cb:history:read";
    /** 限流监控-读 */
    public static final String RL_MONITORING_READ = "rl:monitoring:read";

    // ==================== callhistory（调用历史） ====================

    /** 调用历史查看（列表 / 统计 / 链路 / 错误 / 慢调用；不含正文解密） */
    public static final String CALLHISTORY_READ = "callhistory:read";

    /**
     * 调用历史正文查看（解密 FULL 级别记录的请求 / 响应正文）。
     *
     * <p>只含正文的「打开」权限，不负责列表访问：<b>必须同时持有 {@link #CALLHISTORY_READ}</b>
     * 才能进入该前缀（URL 规则只认 {@code callhistory:read}）。当前只授 ADMIN（运维可通过
     * 记录治理配置记录级别，但不应读到正文）。每次解密都记
     * {@code AuditEventType.FULL_CONTENT_ACCESS} 审计事件。</p>
     */
    public static final String CALLHISTORY_FULL_CONTENT = "callhistory:full-content";

    // ==================== monitoring（监控，5 个资源 × read） ====================

    /** 指标监控-读 */
    public static final String MONITORING_METRICS_READ = "monitoring:metrics:read";
    /** 指标注册/更新/删除-写（issue #454：读与写此前共用"无写规则"，写操作只靠默认 DENY_ALL 兜住） */
    public static final String MONITORING_METRICS_WRITE = "monitoring:metrics:write";
    /** 慢查询分析-读 */
    public static final String MONITORING_SLOWQUERY_READ = "monitoring:slowquery:read";
    /** 慢查询统计重置-写（issue #468：该前缀此前只登记了 GET 规则，重置操作只靠默认 DENY_ALL 兜住） */
    public static final String MONITORING_SLOWQUERY_WRITE = "monitoring:slowquery:write";
    /** Token 用量-读 */
    public static final String MONITORING_TOKENUSAGE_READ = "monitoring:tokenusage:read";
    /** 模型统计-读 */
    public static final String MONITORING_MODELSTATS_READ = "monitoring:modelstats:read";
    /** 路由监控-读 */
    public static final String MONITORING_ROUTING_READ = "monitoring:routing:read";
    /** 配额监控-读 */
    public static final String MONITORING_QUOTA_READ = "monitoring:quota:read";
    /** 异常事件-读（R2-P1-03） */
    public static final String MONITORING_EXCEPTIONS_READ = "monitoring:exceptions:read";

    /** 控制台管理操作-读（#1154：系统 → 管理操作页） */
    public static final String MONITORING_CONSOLE_READ = "monitoring:console:read";
    /** 异常事件-写/清理（R2-P1-03） */
    public static final String MONITORING_EXCEPTIONS_WRITE = "monitoring:exceptions:write";
    /**
     * 监控配置-读（#1173：系统 → 监控配置页面的页面门槛，以及应急操作的影响面预览）。
     *
     * <p>为什么单独拆出读码：页面本身与应急操作都只对运维/管理员开放，而「能看到页面」与
     * 「能改配置」是两件事 —— 用写码当页面门槛会让只读的运维账号连配置现状都看不到。</p>
     */
    public static final String MONITORING_CONFIG_READ = "monitoring:config:read";
    /** 监控配置/运维操作-写（#116：开关指标、降级、缓存清理、熔断强制操作等） */
    public static final String MONITORING_CONFIG_WRITE = "monitoring:config:write";

    // ==================== tracing（链路追踪） ====================

    /** 追踪仪表盘-读 */
    public static final String TRACING_DASHBOARD_READ = "tracing:dashboard:read";
    /** 追踪检索-读 */
    public static final String TRACING_SEARCH_READ = "tracing:search:read";
    /** 追踪配置-读（#567：此前查看追踪配置只能靠管理码） */
    public static final String TRACING_CONFIG_READ = "tracing:config:read";
    /** 追踪配置-管理 */
    public static final String TRACING_CONFIG_MANAGE = "tracing:config:manage";

    // ==================== security（安全管理） ====================

    /** API Key 查看-读（#567：此前列表/详情只能靠管理码） */
    public static final String SECURITY_APIKEYS_READ = "security:apikeys:read";
    /** API Key 管理 */
    public static final String SECURITY_APIKEYS_MANAGE = "security:apikeys:manage";
    /** JWT 令牌查看-读（#567：此前令牌列表/详情只能靠管理码） */
    public static final String SECURITY_JWTTOKENS_READ = "security:jwttokens:read";
    /** JWT 令牌管理 */
    public static final String SECURITY_JWTTOKENS_MANAGE = "security:jwttokens:manage";
    /** 黑名单查看-读（#567：此前黑名单列表/统计只能靠管理码） */
    public static final String SECURITY_BLACKLIST_READ = "security:blacklist:read";
    /** 黑名单管理 */
    public static final String SECURITY_BLACKLIST_MANAGE = "security:blacklist:manage";
    /** 安全审计-读 */
    public static final String SECURITY_AUDIT_READ = "security:audit:read";
    /** 安全审计-写（#116：扩展审计查询/批量写入/测试数据） */
    public static final String SECURITY_AUDIT_WRITE = "security:audit:write";
    /** PII / 数据脱敏配置-读（#567：此前查看脱敏配置只能靠管理码） */
    public static final String SECURITY_SANITIZATION_READ = "security:sanitization:read";
    /** PII / 数据脱敏配置管理 */
    public static final String SECURITY_SANITIZATION_MANAGE = "security:sanitization:manage";
    /** 登录保护配置-读（#1176：查看锁定阈值与当前被锁主体） */
    public static final String SECURITY_LOGINGUARD_READ = "security:loginguard:read";
    /** 登录保护配置-管理（改阈值、手动解除登录锁定） */
    public static final String SECURITY_LOGINGUARD_MANAGE = "security:loginguard:manage";

    // ==================== system（系统管理） ====================

    /** 账户查看-读（#567：此前账户列表只能靠管理码） */
    public static final String SYSTEM_ACCOUNTS_READ = "system:accounts:read";
    /** 账户管理 */
    public static final String SYSTEM_ACCOUNTS_MANAGE = "system:accounts:manage";
    /** 权限查看-读（#567：此前权限矩阵只能靠管理码） */
    public static final String SYSTEM_PERMISSIONS_READ = "system:permissions:read";
    /** 权限管理 */
    public static final String SYSTEM_PERMISSIONS_MANAGE = "system:permissions:manage";

    // ==================== system-sla（SLA 接入，issue #1174） ====================

    /**
     * 查看 SLA 接入账号与凭据（只读）。
     *
     * <p>SLA 是外部巡检工具的运行主体：它需要读观测面（指标、追踪、调用记录、配置），
     * 但不该碰用户/权限管理与审计写。这里把「看得到接入配置」与「能签发/吊销凭据」
     * 拆成两个码，理由同监控配置的 read/write —— 只读值班人员也要能查「谁在什么时候
     * 换过凭据」。
     */
    public static final String SYSTEM_SLA_READ = "system:sla:read";

    /** 管理 SLA 接入账号与凭据（签发一次性接入令牌、吊销、轮换） */
    public static final String SYSTEM_SLA_MANAGE = "system:sla:manage";

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
            CONFIG_CACHE_READ, CONFIG_CACHE_WRITE,
            CONFIG_VALIDATION_READ, CONFIG_VALIDATION_WRITE,
            CONFIG_QUOTA_READ, CONFIG_QUOTA_WRITE,
            CONFIG_RATELIMIT_READ, CONFIG_RATELIMIT_WRITE,
            CONFIG_CLUSTER_READ, CONFIG_CLUSTER_WRITE,
            CONFIG_HEALTH_READ, CONFIG_HEALTH_WRITE,
            LB_MONITORING_READ, LB_CONFIG_READ, LB_CONFIG_WRITE,
            CB_MONITORING_READ, CB_HISTORY_READ,
            RL_MONITORING_READ,
            CALLHISTORY_READ, CALLHISTORY_FULL_CONTENT,
            MONITORING_METRICS_READ, MONITORING_METRICS_WRITE, MONITORING_SLOWQUERY_READ,
            MONITORING_SLOWQUERY_WRITE,
            MONITORING_TOKENUSAGE_READ, MONITORING_MODELSTATS_READ,
            MONITORING_ROUTING_READ,
            MONITORING_QUOTA_READ,
            MONITORING_EXCEPTIONS_READ, MONITORING_EXCEPTIONS_WRITE,
            MONITORING_CONSOLE_READ,
            MONITORING_CONFIG_READ, MONITORING_CONFIG_WRITE,
            TRACING_DASHBOARD_READ, TRACING_SEARCH_READ, TRACING_CONFIG_READ, TRACING_CONFIG_MANAGE,
            SECURITY_APIKEYS_READ, SECURITY_APIKEYS_MANAGE,
            SECURITY_JWTTOKENS_READ, SECURITY_JWTTOKENS_MANAGE,
            SECURITY_BLACKLIST_READ, SECURITY_BLACKLIST_MANAGE, SECURITY_AUDIT_READ, SECURITY_AUDIT_WRITE,
            SECURITY_SANITIZATION_READ, SECURITY_SANITIZATION_MANAGE,
            SECURITY_LOGINGUARD_READ, SECURITY_LOGINGUARD_MANAGE,
            SYSTEM_ACCOUNTS_READ, SYSTEM_ACCOUNTS_MANAGE, SYSTEM_PERMISSIONS_READ, SYSTEM_PERMISSIONS_MANAGE,
            SYSTEM_SLA_READ, SYSTEM_SLA_MANAGE,
            AI_PLAYGROUND_USE,
            ACTUATOR_ADMIN_MANAGE
    );
}
