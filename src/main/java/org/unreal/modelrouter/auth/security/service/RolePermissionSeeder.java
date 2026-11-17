package org.unreal.modelrouter.auth.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.auth.security.permission.PermissionCodes;
import org.unreal.modelrouter.auth.security.permission.PermissionRule;
import org.unreal.modelrouter.auth.security.permission.PermissionRuleRegistry;
import org.unreal.modelrouter.persistence.jpa.entity.RolePermissionEntity;
import org.unreal.modelrouter.persistence.jpa.repository.RolePermissionRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色模板种子（v2.9.8 RBAC，Phase 2）+ 增量收敛（#144）
 *
 * <p>应用启动完成后执行（仿启动期 ApplicationRunner 模式）：
 * <ul>
 *   <li>表为空：种入 4 个角色模板（ADMIN / OPERATOR / USER / VIEWER）——全新安装路径，内容不变。</li>
 *   <li>表非空：执行「只增不删」的增量收敛，而不是跳过。仅对「未被手工定制」的角色补种
 *   {@code 模板 ∩ 规则所需 − 已持有} 的权限码；角色持有模板外权限码则视为手工定制、整个角色跳过；
 *   无任何权限行的角色同样跳过。</li>
 * </ul>
 *
 * <p>规则所需权限码取自 {@link PermissionRuleRegistry#getRules()} 的
 * {@link PermissionRule#permissionCode()}，使后续版本新增权限码并登记 URL 规则后，
 * 已初始化部署不再因种子跳过而静默失去访问（#144）。
 *
 * <p>角色模板（权限码共 76 个，以实现 {@link PermissionCodes} 为准）：
 * <ul>
 *   <li>ADMIN：全量权限码（超集，兼容现有 ADMIN）</li>
 *   <li>OPERATOR：所有 :read + :write（排除 system:* / security:*:manage / actuator:*）</li>
 *   <li>USER：dashboard + config:*:read + lb/cb/rl + monitoring:*:read（含 exceptions:read） +
 *   tracing dashboard+search + ai:playground:use</li>
 *   <li>VIEWER：仅所有 :read（#567 的 9 个「管理面 read」与 callhistory:read 除外，见
 *   {@link #MANAGE_PAIRED_READ_CODES} 与 {@link #viewerCodes()}）</li>
 * </ul>
 *
 * <p><b>调用历史双码</b>：{@code callhistory:read} 是运维面（看记录元数据与摘要），
 * {@code callhistory:full-content} 才允许解密 FULL 级别正文。前者随「所有 :read」落入 OPERATOR，
 * 由 VIEWER 显式排除；后者当前只授 ADMIN（`:full-content` 既不匹配 :read 也不匹配 :write）。
 *
 * <p><b>#567 / B11 不变式</b>：任何模板只要持有某个 {@code x:write} 或 {@code x:manage}，
 * 就必须同时持有与之配对的 {@code x:read}（若该 read 码存在）。这样「读一眼」不再需要写权限，
 * 且升级后没有任何角色的可见面会变小；对不持写码的只读角色（VIEWER）则反向成立 ——
 * 不凭空补发管理面的 read 码，避免造出「菜单可见、路由按 manage 码重定向」的死入口。
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@Slf4j
@Component
public class RolePermissionSeeder implements ApplicationRunner {

    /**
     * #567：10 个「管理面 read」权限码 → 与之配对的写/管理码。
     *
     * <p>引入这 8 个码是为了让「读」不再被「写」权限绑架（B11）：<b>凡是持有右侧写/管理码的
     * 角色，都必须同时持有左侧 read 码</b>，保证升级后没有任何角色的可见面变小。反方向不成立
     * —— 只读角色（VIEWER）不持任何写码，因此不会被配发这 8 个码，否则会把账户/权限/密钥等
     * 管理页暴露成「菜单可见、但路由守卫按 manage 码重定向」的死入口，等于凭空扩权。
     *
     * <p>{@link #operatorCodes()} 用同一张表做一致性判定：OPERATOR 的口径是「全部 :read + :write」，
     * 其中 6 个新 read 码配对的是 OPERATOR 按设计不持有的管理码，必须按配对关系排除，
     * 否则 OPERATOR 会凭空获得密钥/脱敏/令牌/黑名单/追踪配置的读面。
     *
     * <p><b>声明顺序不能后移</b>：{@link #DEFAULT_ROLE_TEMPLATES} 的静态初始化会调用
     * {@link #operatorCodes()} 与 {@link #viewerCodes()}，若本字段在其后声明，读到的会是
     * {@code null}（静态初始化顺序陷阱）。
     */
    private static final Map<String, String> MANAGE_PAIRED_READ_TO_WRITE = Map.ofEntries(
            Map.entry(PermissionCodes.LB_CONFIG_READ, PermissionCodes.LB_CONFIG_WRITE),
            Map.entry(PermissionCodes.TRACING_CONFIG_READ, PermissionCodes.TRACING_CONFIG_MANAGE),
            Map.entry(PermissionCodes.SECURITY_APIKEYS_READ, PermissionCodes.SECURITY_APIKEYS_MANAGE),
            Map.entry(PermissionCodes.SECURITY_JWTTOKENS_READ, PermissionCodes.SECURITY_JWTTOKENS_MANAGE),
            Map.entry(PermissionCodes.SECURITY_BLACKLIST_READ, PermissionCodes.SECURITY_BLACKLIST_MANAGE),
            Map.entry(PermissionCodes.SECURITY_SANITIZATION_READ, PermissionCodes.SECURITY_SANITIZATION_MANAGE),
            Map.entry(PermissionCodes.SYSTEM_ACCOUNTS_READ, PermissionCodes.SYSTEM_ACCOUNTS_MANAGE),
            Map.entry(PermissionCodes.SYSTEM_PERMISSIONS_READ, PermissionCodes.SYSTEM_PERMISSIONS_MANAGE),
            Map.entry(PermissionCodes.MONITORING_CONFIG_READ, PermissionCodes.MONITORING_CONFIG_WRITE),
            Map.entry(PermissionCodes.SYSTEM_SLA_READ, PermissionCodes.SYSTEM_SLA_MANAGE),
            Map.entry(PermissionCodes.SECURITY_LOGINGUARD_READ, PermissionCodes.SECURITY_LOGINGUARD_MANAGE));

    /**
     * 上述 11 个 read 码的集合（{@link #MANAGE_PAIRED_READ_TO_WRITE} 的键集），只读角色需整体排除。
     */
    private static final Set<String> MANAGE_PAIRED_READ_CODES = MANAGE_PAIRED_READ_TO_WRITE.keySet();

    /**
     * 默认角色模板：角色名（大写）→ 权限码列表（顺序同 {@link PermissionCodes#ALL_PERMISSION_CODES}）。
     * 供种子写入与权限管理 API 校验角色名合法性使用。
     */
    public static final Map<String, List<String>> DEFAULT_ROLE_TEMPLATES = buildDefaultRoleTemplates();

    private final RolePermissionRepository rolePermissionRepository;

    private final PermissionRuleRegistry permissionRuleRegistry;

    private final RolePermissionService rolePermissionService;

    public RolePermissionSeeder(final RolePermissionRepository rolePermissionRepository,
                                final PermissionRuleRegistry permissionRuleRegistry,
                                final RolePermissionService rolePermissionService) {
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRuleRegistry = permissionRuleRegistry;
        this.rolePermissionService = rolePermissionService;
    }

    @Override
    @Transactional
    public void run(final ApplicationArguments args) {
        seedIfEmpty();
    }

    /**
     * 表空则种入全部角色模板（当前 6 个）；表非空则增量收敛（只增不删，见类注释）。
     *
     * <p>「表非空」时**仍然**会为「库里一行都没有」的角色播种完整模板（#1174 运行态实测补的漏）：
     * 新增内置角色（如 SLA_READONLY / SLA_OPERATOR）只改了代码，存量库的 {@code role_permissions}
     * 里没有这些行，若不补种，该角色的权限集合就是空的 —— 表现是登录成功、每个接口都 403，
     * 单测（直接调 service）完全看不出来。这与「管理员手工清空某角色权限」在数据上不可区分，
     * 但一个零权限内置角色没有存在意义，故按模板播种，并在日志里写明是「首次播种」。
     *
     * <p>收敛或播种写入行之后会清空 {@link RolePermissionService} 权限缓存，
     * 避免启动窗口内已签发 JWT 时缓存的旧权限集合继续生效。
     */
    public void seedIfEmpty() {
        if (rolePermissionRepository.count() == 0) {
            List<RolePermissionEntity> entities = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : DEFAULT_ROLE_TEMPLATES.entrySet()) {
                for (String code : entry.getValue()) {
                    entities.add(RolePermissionEntity.builder()
                            .roleName(entry.getKey())
                            .permissionCode(code)
                            .build());
                }
            }
            rolePermissionRepository.saveAll(entities);
            rolePermissionService.invalidateCache();
            log.info("RolePermissionSeeder: 已种入 {} 个角色模板，共 {} 条角色-权限映射",
                    DEFAULT_ROLE_TEMPLATES.size(), entities.size());
            return;
        }
        reconcileUntouchedRoles();
    }

    /**
     * 对未被手工定制的角色做增量补种：只插入 {@code 模板 ∩ 规则所需 − 已持有}，绝不删除或修改既有行。
     */
    private void reconcileUntouchedRoles() {
        Set<String> requiredByRules = permissionRuleRegistry.getRules().stream()
                .map(PermissionRule::permissionCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Set<String>> heldByRole = rolePermissionRepository
                .findByRoleNameIn(DEFAULT_ROLE_TEMPLATES.keySet()).stream()
                .collect(Collectors.groupingBy(
                        RolePermissionEntity::getRoleName,
                        Collectors.mapping(RolePermissionEntity::getPermissionCode,
                                Collectors.toCollection(LinkedHashSet::new))));

        List<RolePermissionEntity> toInsert = new ArrayList<>();
        int skippedCustomized = 0;
        int seededNewRoles = 0;
        int alreadyCurrent = 0;

        for (Map.Entry<String, List<String>> entry : DEFAULT_ROLE_TEMPLATES.entrySet()) {
            String role = entry.getKey();
            List<String> template = entry.getValue();
            Set<String> held = heldByRole.getOrDefault(role, Set.of());

            if (held.isEmpty()) {
                // 新内置角色首次出现在存量库：播种完整模板（#1174）。
                // 这里不能跳过：跳过会让该角色的权限集合为空，表现为「登录成功但处处 403」。
                for (String code : template) {
                    toInsert.add(RolePermissionEntity.builder()
                            .roleName(role)
                            .permissionCode(code)
                            .build());
                }
                seededNewRoles++;
                log.info("RolePermissionSeeder: 角色 {} 在库中无任何权限行，按模板首次播种 {} 个权限码",
                        role, template.size());
                continue;
            }
            if (!template.containsAll(held)) {
                Set<String> extras = new LinkedHashSet<>(held);
                extras.removeAll(template);
                skippedCustomized++;
                log.info("RolePermissionSeeder: 角色 {} 含模板外权限码 {}，视为手工定制，跳过增量收敛",
                        role, extras);
                continue;
            }

            List<String> missing = template.stream()
                    .filter(requiredByRules::contains)
                    .filter(code -> !held.contains(code))
                    .collect(Collectors.toList());
            if (missing.isEmpty()) {
                alreadyCurrent++;
                log.info("RolePermissionSeeder: 角色 {} 的规则所需权限已齐备，无需补种", role);
                continue;
            }
            for (String code : missing) {
                toInsert.add(RolePermissionEntity.builder()
                        .roleName(role)
                        .permissionCode(code)
                        .build());
            }
            log.info("RolePermissionSeeder: 角色 {} 增量补种 {} 个权限码: {}", role, missing.size(), missing);
        }

        if (!toInsert.isEmpty()) {
            rolePermissionRepository.saveAll(toInsert);
            rolePermissionService.invalidateCache();
        }
        log.info("RolePermissionSeeder: 增量收敛完成，补种 {} 条；跳过手工定制 {} 个角色、首次播种 {} 个角色、已齐备 {} 个角色",
                toInsert.size(), skippedCustomized, seededNewRoles, alreadyCurrent);
    }

    /**
     * 已知角色名集合（ADMIN / OPERATOR / USER / VIEWER / SLA_READONLY / SLA_OPERATOR，
     * 供权限管理 API 校验 roleName 合法性）
     *
     * @return 角色名集合（大写）
     */
    public static Set<String> knownRoles() {
        return DEFAULT_ROLE_TEMPLATES.keySet();
    }

    private static Map<String, List<String>> buildDefaultRoleTemplates() {
        Map<String, List<String>> templates = new LinkedHashMap<>();
        templates.put("ADMIN", PermissionCodes.ALL_PERMISSION_CODES);
        templates.put("OPERATOR", operatorCodes());
        templates.put("USER", userCodes());
        templates.put("VIEWER", viewerCodes());
        templates.put(SLA_READONLY_ROLE, slaReadonlyCodes());
        templates.put(SLA_OPERATOR_ROLE, slaOperatorCodes());
        return Collections.unmodifiableMap(templates);
    }

    /**
     * OPERATOR：所有 :read + :write，排除 system:* / security:*:manage / actuator:*
     *
     * <p>#567：再排除「配对的写/管理码不在本模板内」的管理面 read 码 —— OPERATOR 按设计不持有
     * {@code security:*:manage} 等管理码，若照样带上对应 read 码，既凭空放宽了对密钥/脱敏/令牌/
     * 黑名单/追踪配置的读面，又会把「需要 manage 码才能打开」的页面变成死入口。
     * {@code lb:config:read} 例外：OPERATOR 持有 {@code lb:config:write} ⇒ 按 B11 必须配发。
     */
    private static List<String> operatorCodes() {
        List<String> base = PermissionCodes.ALL_PERMISSION_CODES.stream()
                .filter(code -> code.endsWith(":read") || code.endsWith(":write"))
                .filter(code -> !code.startsWith("system:"))
                .filter(code -> !code.startsWith("actuator:"))
                .filter(code -> !(code.startsWith("security:") && code.endsWith(":manage")))
                .collect(Collectors.toList());
        return base.stream()
                .filter(code -> !MANAGE_PAIRED_READ_CODES.contains(code)
                        || base.contains(MANAGE_PAIRED_READ_TO_WRITE.get(code)))
                .collect(Collectors.toList());
    }

    /**
     * USER：dashboard + config:*:read + lb/cb/rl + monitoring:*:read + tracing dashboard+search +
     * ai:playground:use（兼容现有 USER 默认集，开发计划2026 L1142）
     */
    private static List<String> userCodes() {
        return List.of(
                PermissionCodes.OVERVIEW_DASHBOARD_READ,
                PermissionCodes.CONFIG_SERVICES_READ,
                PermissionCodes.CONFIG_INSTANCES_READ,
                PermissionCodes.CONFIG_VERSIONS_READ,
                PermissionCodes.CONFIG_PERSISTENCE_READ,
                PermissionCodes.CONFIG_ADAPTERS_READ,
                PermissionCodes.CONFIG_RULES_READ,
                PermissionCodes.CONFIG_POOLS_READ,
                PermissionCodes.CONFIG_CIRCUITBREAKER_READ,
                PermissionCodes.CONFIG_CALLHISTORY_READ,
                PermissionCodes.CONFIG_CACHE_READ,
                PermissionCodes.CONFIG_VALIDATION_READ,
                PermissionCodes.LB_MONITORING_READ,
                PermissionCodes.LB_CONFIG_READ,
                PermissionCodes.LB_CONFIG_WRITE,
                PermissionCodes.CB_MONITORING_READ,
                PermissionCodes.CB_HISTORY_READ,
                PermissionCodes.RL_MONITORING_READ,
                PermissionCodes.MONITORING_METRICS_READ,
                PermissionCodes.MONITORING_SLOWQUERY_READ,
                PermissionCodes.MONITORING_TOKENUSAGE_READ,
                PermissionCodes.MONITORING_MODELSTATS_READ,
                PermissionCodes.MONITORING_ROUTING_READ,
                PermissionCodes.TRACING_DASHBOARD_READ,
                PermissionCodes.TRACING_SEARCH_READ,
                PermissionCodes.AI_PLAYGROUND_USE,
                PermissionCodes.CONFIG_QUOTA_READ,
                PermissionCodes.CONFIG_RATELIMIT_READ,
                PermissionCodes.CONFIG_CLUSTER_READ,
                PermissionCodes.CONFIG_HEALTH_READ,
                PermissionCodes.MONITORING_QUOTA_READ,
                PermissionCodes.MONITORING_EXCEPTIONS_READ,
                PermissionCodes.MONITORING_CONSOLE_READ
        );
    }

    /** SLA 只读角色名（外部巡检工具的运行主体，issue #1174）。 */
    public static final String SLA_READONLY_ROLE = "SLA_READONLY";

    /** SLA 运维角色名（只读 + 配置写，供升级后回归与场景编排使用，issue #1174）。 */
    public static final String SLA_OPERATOR_ROLE = "SLA_OPERATOR";

    /**
     * SLA_READONLY：外部巡检工具能看到的全部观测面，写一个都不给。
     *
     * <p>口径 = 「凡是巡检、诊断、容量报表会用到的读面」：总览、监控、追踪、调用记录
     * （<b>不含</b> {@code callhistory:full-content} —— 正文解密仍只给 ADMIN）、配置读、
     * 负载均衡/熔断/限流的监控读、审计读。刻意不给 {@code security:*} 管理面与
     * {@code system:accounts|permissions}：SLA 是观测者，不是管理员。
     */
    private static List<String> slaReadonlyCodes() {
        return List.of(
                PermissionCodes.OVERVIEW_DASHBOARD_READ,
                PermissionCodes.CONFIG_SERVICES_READ,
                PermissionCodes.CONFIG_INSTANCES_READ,
                PermissionCodes.CONFIG_VERSIONS_READ,
                PermissionCodes.CONFIG_PERSISTENCE_READ,
                PermissionCodes.CONFIG_ADAPTERS_READ,
                PermissionCodes.CONFIG_RULES_READ,
                PermissionCodes.CONFIG_POOLS_READ,
                PermissionCodes.CONFIG_CIRCUITBREAKER_READ,
                PermissionCodes.CONFIG_CALLHISTORY_READ,
                PermissionCodes.CONFIG_CACHE_READ,
                PermissionCodes.CONFIG_VALIDATION_READ,
                PermissionCodes.CONFIG_QUOTA_READ,
                PermissionCodes.CONFIG_RATELIMIT_READ,
                PermissionCodes.CONFIG_CLUSTER_READ,
                PermissionCodes.CONFIG_HEALTH_READ,
                PermissionCodes.LB_MONITORING_READ,
                PermissionCodes.LB_CONFIG_READ,
                PermissionCodes.CB_MONITORING_READ,
                PermissionCodes.CB_HISTORY_READ,
                PermissionCodes.RL_MONITORING_READ,
                PermissionCodes.CALLHISTORY_READ,
                PermissionCodes.MONITORING_METRICS_READ,
                PermissionCodes.MONITORING_SLOWQUERY_READ,
                PermissionCodes.MONITORING_TOKENUSAGE_READ,
                PermissionCodes.MONITORING_MODELSTATS_READ,
                PermissionCodes.MONITORING_ROUTING_READ,
                PermissionCodes.MONITORING_QUOTA_READ,
                PermissionCodes.MONITORING_EXCEPTIONS_READ,
                PermissionCodes.MONITORING_CONSOLE_READ,
                PermissionCodes.MONITORING_CONFIG_READ,
                PermissionCodes.TRACING_DASHBOARD_READ,
                PermissionCodes.TRACING_SEARCH_READ,
                PermissionCodes.TRACING_CONFIG_READ,
                PermissionCodes.SECURITY_AUDIT_READ
        );
    }

    /**
     * SLA_OPERATOR：SLA_READONLY + 控制面配置写。
     *
     * <p>用途 = 升级后回归（{@code sla verify --allow-writes}）与场景编排需要改配置再恢复。
     * <b>不含</b>：用户/权限管理、审计写、{@code security:*:manage}（密钥/令牌/黑名单/脱敏）、
     * {@code actuator:admin:manage}、{@code callhistory:full-content}、{@code system:sla:manage}
     * —— 这些都是「能改别人的凭据或看到正文」的能力，不该随巡检账号一起发出去。
     */
    private static List<String> slaOperatorCodes() {
        List<String> codes = new java.util.ArrayList<>(slaReadonlyCodes());
        codes.add(PermissionCodes.CONFIG_SERVICES_WRITE);
        codes.add(PermissionCodes.CONFIG_INSTANCES_WRITE);
        codes.add(PermissionCodes.CONFIG_VERSIONS_WRITE);
        codes.add(PermissionCodes.CONFIG_RULES_WRITE);
        codes.add(PermissionCodes.CONFIG_POOLS_WRITE);
        codes.add(PermissionCodes.CONFIG_CIRCUITBREAKER_WRITE);
        codes.add(PermissionCodes.CONFIG_CACHE_WRITE);
        codes.add(PermissionCodes.CONFIG_QUOTA_WRITE);
        codes.add(PermissionCodes.CONFIG_RATELIMIT_WRITE);
        codes.add(PermissionCodes.CONFIG_CLUSTER_WRITE);
        codes.add(PermissionCodes.CONFIG_HEALTH_WRITE);
        codes.add(PermissionCodes.CONFIG_PERSISTENCE_WRITE);
        codes.add(PermissionCodes.CONFIG_ADAPTERS_WRITE);
        codes.add(PermissionCodes.CONFIG_VALIDATION_WRITE);
        codes.add(PermissionCodes.CONFIG_CALLHISTORY_WRITE);
        codes.add(PermissionCodes.LB_CONFIG_WRITE);
        codes.add(PermissionCodes.TRACING_CONFIG_MANAGE);
        codes.add(PermissionCodes.MONITORING_CONFIG_WRITE);
        return List.copyOf(codes);
    }

    /**
     * VIEWER：仅所有 :read（#567 的 8 个管理面 read 与 callhistory:read 除外）
     *
     * <p>排除理由见 {@link #MANAGE_PAIRED_READ_CODES}：VIEWER 不持任何 :write / :manage，
     * 按 B11「只读角色不凭空扩权」不给它管理面的只读入口。
     *
     * <p>{@code callhistory:read} 单独排除：调用记录里带着请求/响应正文的摘要与调用方标识，
     * 口径上是运维/审计面而非通用只读面（与 {@code config:callhistory:read} 不同 —— 后者只是
     * 「记录治理的配置」）。拆码前该面由 {@code callhistory:view} 把关、三角色都不持有，
     * 拆码后由 {@link #operatorCodes()} 自动带走一份给运维，VIEWER 需显式排除以免凭空扩权。
     */
    private static List<String> viewerCodes() {
        return PermissionCodes.ALL_PERMISSION_CODES.stream()
                .filter(code -> code.endsWith(":read"))
                .filter(code -> !MANAGE_PAIRED_READ_CODES.contains(code))
                .filter(code -> !PermissionCodes.CALLHISTORY_READ.equals(code))
                .collect(Collectors.toList());
    }
}
