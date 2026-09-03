package org.unreal.modelrouter.auth.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.auth.security.permission.PermissionCodes;
import org.unreal.modelrouter.persistence.jpa.entity.RolePermissionEntity;
import org.unreal.modelrouter.persistence.jpa.repository.RolePermissionRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色模板种子（v2.9.8 RBAC，Phase 2）
 *
 * <p>应用启动完成后执行（仿 {@code CompatibilitySchemaMigrator} 的 ApplicationRunner 模式）：
 * 当 {@code role_permissions} 表为空时种入 4 个角色模板（ADMIN / OPERATOR / USER / VIEWER，
 * 映射见开发计划2026 L1138-1143），表非空则跳过（幂等，不覆盖已有配置）。
 *
 * <p>角色模板（权限码共 44 个，开发计划写 42，以实现 {@link PermissionCodes} 为准）：
 * <ul>
 *   <li>ADMIN：全量 43 码（超集，兼容现有 ADMIN）</li>
 *   <li>OPERATOR：所有 :read + :write（排除 system:* / security:*:manage / actuator:*）</li>
 *   <li>USER：dashboard + config:*:read + lb/cb/rl + monitoring:*:read + tracing dashboard+search +
 *   ai:playground:use（兼容现有 USER 默认集）</li>
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

    public RolePermissionSeeder(final RolePermissionRepository rolePermissionRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
    }

    @Override
    @Transactional
    public void run(final ApplicationArguments args) {
        seedIfEmpty();
    }

    /**
     * 表空则种入 4 个角色模板，非空跳过（幂等）
     */
    public void seedIfEmpty() {
        if (rolePermissionRepository.count() > 0) {
            log.info("RolePermissionSeeder: role_permissions 表非空，跳过种子（幂等）");
            return;
        }
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
        log.info("RolePermissionSeeder: 已种入 {} 个角色模板，共 {} 条角色-权限映射",
                DEFAULT_ROLE_TEMPLATES.size(), entities.size());
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
                PermissionCodes.AI_PLAYGROUND_USE
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
