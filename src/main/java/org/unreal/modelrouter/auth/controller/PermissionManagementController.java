package org.unreal.modelrouter.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.auth.security.permission.PermissionCodes;
import org.unreal.modelrouter.auth.security.service.RolePermissionSeeder;
import org.unreal.modelrouter.auth.security.service.RolePermissionService;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.persistence.jpa.entity.RolePermissionEntity;
import org.unreal.modelrouter.persistence.jpa.repository.RolePermissionRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 权限管理控制器（v2.9.8 RBAC，Phase 2）
 *
 * <p>提供 4 个端点：
 * <ul>
 *   <li>{@code GET /api/security/permissions}：全部权限码（按 {@link PermissionCodes#ALL_PERMISSION_CODES} 顺序）</li>
 *   <li>{@code GET /api/security/permissions/roles}：全部角色及其权限码</li>
 *   <li>{@code PUT /api/security/permissions/roles/{roleName}}：更新角色权限码集合</li>
 *   <li>{@code GET /api/auth/permissions}：当前登录用户权限码（前端菜单过滤用）</li>
 * </ul>
 *
 * <p>权限落地（RBAC 铁律：同步返回 controller 禁方法级 @PreAuthorize）：
 * <ul>
 *   <li>{@code /api/security/permissions/**} 由 {@code PermissionRuleRegistry} URL 规则保护 →
 *   {@code system:permissions:manage}（系统管理）</li>
 *   <li>{@code GET /api/auth/permissions} 未登记规则，回退 authenticated（任意已登录用户）</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Permission Management", description = "RBAC 权限管理 API（v2.9.8）")
public class PermissionManagementController {

    /** ADMIN 角色 authority（JwtAuthentication 将角色转为 ROLE_ 前缀） */
    private static final String ADMIN_ROLE_AUTHORITY = "ROLE_ADMIN";

    private final RolePermissionService rolePermissionService;
    private final RolePermissionRepository rolePermissionRepository;

    /**
     * 获取全部权限码
     *
     * @return 全部权限码（按 ALL_PERMISSION_CODES 模块顺序，44 个）
     */
    @GetMapping("/security/permissions")
    @Operation(summary = "获取全部权限码",
            description = "按 PermissionCodes.ALL_PERMISSION_CODES 顺序返回全部权限码（44 个，ADMIN 超集）")
    public ResponseEntity<RouterResponse<List<String>>> getAllPermissions() {
        return ResponseEntity.ok(
                RouterResponse.success(PermissionCodes.ALL_PERMISSION_CODES, "获取权限码列表成功"));
    }

    /**
     * 获取全部角色及其权限码
     *
     * @return 角色名 → 权限码列表
     */
    @GetMapping("/security/permissions/roles")
    @Operation(summary = "获取全部角色及其权限码",
            description = "返回 role_permissions 表中全部角色（角色名 → 权限码列表）")
    public ResponseEntity<RouterResponse<Map<String, List<String>>>> getAllRolesWithPermissions() {
        Map<String, List<String>> roles = rolePermissionRepository.findAll().stream()
                .collect(Collectors.groupingBy(RolePermissionEntity::getRoleName,
                        LinkedHashMap::new,
                        Collectors.mapping(RolePermissionEntity::getPermissionCode, Collectors.toList())));
        return ResponseEntity.ok(RouterResponse.success(roles, "获取角色权限列表成功"));
    }

    /**
     * 更新角色权限码集合（整体替换）
     *
     * <p>校验：角色名必须是种子模板角色（ADMIN/OPERATOR/USER/VIEWER），权限码必须存在于
     * {@link PermissionCodes#ALL_PERMISSION_CODES}。更新成功后清空权限缓存。
     *
     * @param roleName        角色名（大小写不敏感，存储统一大写）
     * @param permissionCodes 权限码集合（去重后整体替换）
     * @return 更新后的权限码列表
     */
    @PutMapping("/security/permissions/roles/{roleName}")
    @Operation(summary = "更新角色权限码集合",
            description = "用请求体中的权限码集合整体替换角色的权限映射；校验角色名合法、权限码存在")
    public ResponseEntity<RouterResponse<List<String>>> updateRolePermissions(
            @PathVariable final String roleName,
            @RequestBody final List<String> permissionCodes) {
        String normalizedRole = roleName.trim().toUpperCase(Locale.ROOT);
        if (!RolePermissionSeeder.knownRoles().contains(normalizedRole)) {
            return ResponseEntity.badRequest().body(
                    RouterResponse.error("非法角色名: " + roleName, "INVALID_ROLE"));
        }
        List<String> validatedCodes = normalizeCodes(permissionCodes);
        for (String code : validatedCodes) {
            if (!PermissionCodes.ALL_PERMISSION_CODES.contains(code)) {
                return ResponseEntity.badRequest().body(
                        RouterResponse.error("非法权限码: " + code, "INVALID_PERMISSION"));
            }
        }
        rolePermissionRepository.deleteByRoleName(normalizedRole);
        List<RolePermissionEntity> entities = validatedCodes.stream()
                .map(code -> RolePermissionEntity.builder()
                        .roleName(normalizedRole)
                        .permissionCode(code)
                        .build())
                .collect(Collectors.toList());
        rolePermissionRepository.saveAll(entities);
        // 权限变更后清空缓存（缓存仅用于 JWT 签发，变更需重新登录生效）
        rolePermissionService.invalidateCache();
        log.info("已更新角色权限: role={}, codes={}", normalizedRole, validatedCodes);
        return ResponseEntity.ok(RouterResponse.success(validatedCodes, "角色权限更新成功"));
    }

    /**
     * 获取当前登录用户权限码
     *
     * <p>从认证对象的 authorities 中提取无 {@code ROLE_} 前缀的权限码（即 JWT permissions claim）；
     * ADMIN 角色返回全量权限码（兼容旧令牌无 permissions claim 的场景）。
     *
     * @param authentication 当前认证对象
     * @return 当前用户权限码列表
     */
    @GetMapping("/auth/permissions")
    @Operation(summary = "获取当前登录用户权限码",
            description = "返回当前登录用户的 permissions claim（无 ROLE_ 前缀的 authority）；ADMIN 返回全量权限码")
    public ResponseEntity<RouterResponse<List<String>>> getCurrentUserPermissions(
            final Authentication authentication) {
        return ResponseEntity.ok(
                RouterResponse.success(resolvePermissions(authentication), "获取当前用户权限成功"));
    }

    private static List<String> normalizeCodes(final List<String> permissionCodes) {
        if (permissionCodes == null) {
            return List.of();
        }
        return permissionCodes.stream()
                .filter(Objects::nonNull)
                .map(code -> code.trim())
                .filter(code -> !code.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private static List<String> resolvePermissions(final Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities() == null) {
            return List.of();
        }
        boolean admin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_ROLE_AUTHORITY::equals);
        if (admin) {
            return PermissionCodes.ALL_PERMISSION_CODES;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> !authority.startsWith("ROLE_"))
                .distinct()
                .collect(Collectors.toList());
    }
}
