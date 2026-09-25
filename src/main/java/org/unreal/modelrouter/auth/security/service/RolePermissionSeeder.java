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
 * <p>应用启动完成后执行（仿 {@code CompatibilitySchemaMigrator} 的 ApplicationRunner 模式）：
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
 * <p>角色模板（权限码共 53 个，以实现 {@link PermissionCodes} 为准）：
 * <ul>
 *   <li>ADMIN：全量权限码（超集，兼容现有 ADMIN）</li>
 *   <li>OPERATOR：所有 :read + :write（排除 system:* / security:*:manage / actuator:*）</li>
 *   <li>USER：dashboard + config:*:read + lb/cb/rl + monitoring:*:read（含 exceptions:read） +
 *   tracing dashboard+search + ai:playground:use</li>
 *   <li>VIEWER：仅所有 :read</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@Slf4j
@Component
public class RolePermissionSeeder implements ApplicationRunner {

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
     * 表空则种入 4 个角色模板；表非空则增量收敛（只增不删，见类注释）。
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
        int skippedMissing = 0;
        int alreadyCurrent = 0;

        for (Map.Entry<String, List<String>> entry : DEFAULT_ROLE_TEMPLATES.entrySet()) {
            String role = entry.getKey();
            List<String> template = entry.getValue();
            Set<String> held = heldByRole.getOrDefault(role, Set.of());

            if (held.isEmpty()) {
                skippedMissing++;
                log.info("RolePermissionSeeder: 角色 {} 无任何权限行，跳过增量收敛", role);
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
        log.info("RolePermissionSeeder: 增量收敛完成，补种 {} 条；跳过手工定制 {} 个角色、无行 {} 个角色、已齐备 {} 个角色",
                toInsert.size(), skippedCustomized, skippedMissing, alreadyCurrent);
    }

    /**
     * 已知角色名集合（ADMIN / OPERATOR / USER / VIEWER，供权限管理 API 校验 roleName 合法性）
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
        return Collections.unmodifiableMap(templates);
    }

    /**
     * OPERATOR：所有 :read + :write，排除 system:* / security:*:manage / actuator:*
     */
    private static List<String> operatorCodes() {
        return PermissionCodes.ALL_PERMISSION_CODES.stream()
                .filter(code -> code.endsWith(":read") || code.endsWith(":write"))
                .filter(code -> !code.startsWith("system:"))
                .filter(code -> !code.startsWith("actuator:"))
                .filter(code -> !(code.startsWith("security:") && code.endsWith(":manage")))
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
                PermissionCodes.MONITORING_QUOTA_READ,
                PermissionCodes.MONITORING_EXCEPTIONS_READ
        );
    }

    /**
     * VIEWER：仅所有 :read
     */
    private static List<String> viewerCodes() {
        return PermissionCodes.ALL_PERMISSION_CODES.stream()
                .filter(code -> code.endsWith(":read"))
                .collect(Collectors.toList());
    }
}
