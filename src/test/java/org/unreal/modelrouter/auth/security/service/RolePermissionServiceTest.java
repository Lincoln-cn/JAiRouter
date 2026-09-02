package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.auth.security.permission.PermissionCodes;
import org.unreal.modelrouter.persistence.jpa.entity.RolePermissionEntity;
import org.unreal.modelrouter.persistence.jpa.repository.RolePermissionRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RolePermissionService 单元测试（v2.9.8 RBAC）
 *
 * 覆盖：roles→codes 查询、ADMIN 短路全量、Caffeine 5min 缓存、空入参容错。
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RolePermissionService 测试")
class RolePermissionServiceTest {

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    private RolePermissionService service;

    @BeforeEach
    void setUp() {
        service = new RolePermissionService(rolePermissionRepository);
    }

    @Nested
    @DisplayName("roles → codes 查询")
    class RolesToCodesTests {

        @Test
        @DisplayName("USER 角色从库中查询到权限码")
        void userRoleReturnsCodesFromDatabase() {
            when(rolePermissionRepository.findByRoleNameIn(List.of("USER")))
                    .thenReturn(List.of(
                            entity("USER", PermissionCodes.OVERVIEW_DASHBOARD_READ),
                            entity("USER", PermissionCodes.CONFIG_SERVICES_READ),
                            entity("USER", PermissionCodes.CONFIG_SERVICES_WRITE)
                    ));

            List<String> codes = service.getPermissionCodesForRoles(List.of("user"));

            assertEquals(List.of(
                    PermissionCodes.OVERVIEW_DASHBOARD_READ,
                    PermissionCodes.CONFIG_SERVICES_READ,
                    PermissionCodes.CONFIG_SERVICES_WRITE), codes);
        }

        @Test
        @DisplayName("多角色合并查询且去重")
        void multipleRolesMergedAndDistinct() {
            when(rolePermissionRepository.findByRoleNameIn(List.of("USER", "VIEWER")))
                    .thenReturn(List.of(
                            entity("USER", PermissionCodes.CONFIG_SERVICES_READ),
                            entity("VIEWER", PermissionCodes.CONFIG_SERVICES_READ),
                            entity("VIEWER", PermissionCodes.MONITORING_METRICS_READ)
                    ));

            List<String> codes = service.getPermissionCodesForRoles(List.of("user", "viewer"));

            assertEquals(List.of(
                    PermissionCodes.CONFIG_SERVICES_READ,
                    PermissionCodes.MONITORING_METRICS_READ), codes);
        }

        @Test
        @DisplayName("未登记角色返回空列表")
        void unknownRoleReturnsEmpty() {
            when(rolePermissionRepository.findByRoleNameIn(List.of("GHOST"))).thenReturn(List.of());

            List<String> codes = service.getPermissionCodesForRoles(List.of("ghost"));

            assertTrue(codes.isEmpty());
        }

        @Test
        @DisplayName("null / 空角色列表返回空列表，不查库")
        void nullOrEmptyRolesReturnsEmpty() {
            assertTrue(service.getPermissionCodesForRoles(null).isEmpty());
            assertTrue(service.getPermissionCodesForRoles(List.of()).isEmpty());
            verify(rolePermissionRepository, never()).findByRoleNameIn(anyCollection());
        }
    }

    @Nested
    @DisplayName("ADMIN 短路")
    class AdminShortCircuitTests {

        @Test
        @DisplayName("包含 ADMIN 角色时返回全量权限码且不查库")
        void adminReturnsAllCodesWithoutDatabase() {
            List<String> codes = service.getPermissionCodesForRoles(List.of("ADMIN"));

            assertEquals(PermissionCodes.ALL_PERMISSION_CODES, codes);
            assertEquals(43, codes.size());
            verify(rolePermissionRepository, never()).findByRoleNameIn(anyCollection());
        }

        @Test
        @DisplayName("ADMIN 与其他角色混合时同样短路全量")
        void adminMixedWithOtherRolesShortCircuits() {
            List<String> codes = service.getPermissionCodesForRoles(List.of("user", "admin"));

            assertEquals(PermissionCodes.ALL_PERMISSION_CODES, codes);
            verify(rolePermissionRepository, never()).findByRoleNameIn(anyCollection());
        }
    }

    @Nested
    @DisplayName("Caffeine 缓存")
    class CacheTests {

        @Test
        @DisplayName("相同角色组合第二次调用命中缓存，只查一次库")
        void repeatedCallHitsCache() {
            when(rolePermissionRepository.findByRoleNameIn(List.of("USER")))
                    .thenReturn(List.of(entity("USER", PermissionCodes.OVERVIEW_DASHBOARD_READ)));

            service.getPermissionCodesForRoles(List.of("user"));
            service.getPermissionCodesForRoles(List.of("user"));
            service.getPermissionCodesForRoles(List.of("USER"));

            verify(rolePermissionRepository, times(1)).findByRoleNameIn(anyCollection());
        }

        @Test
        @DisplayName("invalidateCache 后重新查库")
        void invalidateCacheReloads() {
            when(rolePermissionRepository.findByRoleNameIn(List.of("USER")))
                    .thenReturn(List.of(entity("USER", PermissionCodes.OVERVIEW_DASHBOARD_READ)));

            service.getPermissionCodesForRoles(List.of("user"));
            service.invalidateCache();
            service.getPermissionCodesForRoles(List.of("user"));

            verify(rolePermissionRepository, times(2)).findByRoleNameIn(anyCollection());
        }
    }

    private static RolePermissionEntity entity(final String role, final String code) {
        return RolePermissionEntity.builder().roleName(role).permissionCode(code).build();
    }
}
