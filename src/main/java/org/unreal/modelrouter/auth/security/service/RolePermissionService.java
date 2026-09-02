package org.unreal.modelrouter.auth.security.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.auth.security.permission.PermissionCodes;
import org.unreal.modelrouter.persistence.jpa.entity.RolePermissionEntity;
import org.unreal.modelrouter.persistence.jpa.repository.RolePermissionRepository;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 角色-权限服务（v2.9.8 RBAC 数据驱动权限体系）
 *
 * <p>提供 roles→permission codes 查询，用于 JWT 签发时写入 permissions claim。
 *
 * <p>缓存策略：
 * <ul>
 *   <li>Caffeine 5 分钟过期（开发计划 v2.9.8 要求），角色组合作为缓存键</li>
 *   <li>ADMIN 角色短路：直接返回全量权限码，不查库、不走缓存</li>
 *   <li>缓存仅用于签发（登录时读取），权限变更需重新登录生效（JWT 内嵌）</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@Slf4j
@Service
public class RolePermissionService {

    /** 角色→权限码缓存有效期（5 分钟，与开发计划一致） */
    private static final Duration CACHE_EXPIRE = Duration.ofMinutes(5);

    /** 缓存最大条目数（角色组合数有限，200 足够） */
    private static final int CACHE_MAX_SIZE = 200;

    private final RolePermissionRepository rolePermissionRepository;

    private final Cache<String, List<String>> permissionCache;

    public RolePermissionService(final RolePermissionRepository rolePermissionRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionCache = Caffeine.newBuilder()
                .expireAfterWrite(CACHE_EXPIRE)
                .maximumSize(CACHE_MAX_SIZE)
                .build();
    }

    /**
     * 根据角色列表查询权限码列表
     *
     * <p>角色名统一转为大写后去重；若包含 ADMIN 则短路返回全量权限码。
     * 未在 role_permissions 表登记的角色组合返回空列表。
     *
     * @param roles 角色列表（可为 null 或空）
     * @return 权限码列表（去重、按开发计划 L1132 清单模块顺序排序，可能为空）
     */
    public List<String> getPermissionCodesForRoles(final List<String> roles) {
        List<String> normalized = normalizeRoles(roles);
        if (normalized.isEmpty()) {
            return List.of();
        }
        if (normalized.contains("ADMIN")) {
            return PermissionCodes.ALL_PERMISSION_CODES;
        }
        String cacheKey = String.join(",", normalized);
        return permissionCache.get(cacheKey, key -> loadFromDatabase(normalized));
    }

    /**
     * 清空权限缓存（供权限管理 API 变更后调用，Phase 2 使用）
     */
    public void invalidateCache() {
        permissionCache.invalidateAll();
        log.info("角色权限缓存已清空");
    }

    private List<String> normalizeRoles(final List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .filter(role -> role != null && !role.trim().isEmpty())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> loadFromDatabase(final List<String> roles) {
        List<String> codes = rolePermissionRepository.findByRoleNameIn(roles).stream()
                .map(RolePermissionEntity::getPermissionCode)
                .filter(code -> code != null && !code.trim().isEmpty())
                .distinct()
                .sorted(Comparator.comparingInt(RolePermissionService::canonicalOrder))
                .collect(Collectors.toList());
        log.debug("从 role_permissions 加载权限码: roles={}, codes={}", roles, codes);
        return codes;
    }

    /**
     * 权限码规范顺序（v2.9.8 RBAC）
     *
     * <p>按 {@link PermissionCodes#ALL_PERMISSION_CODES} 的模块顺序排序
     * （开发计划 2026 L1132 清单：overview → config → lb/cb/rl → callhistory →
     * monitoring → tracing → security → system → ai → actuator），
     * 与 ADMIN 短路返回的全量列表顺序一致；未登记清单的未知权限码排在末尾。
     *
     * @param code 权限码
     * @return 规范顺序中的位置
     */
    private static int canonicalOrder(final String code) {
        int index = PermissionCodes.ALL_PERMISSION_CODES.indexOf(code);
        return index >= 0 ? index : PermissionCodes.ALL_PERMISSION_CODES.size();
    }
}
