package org.unreal.modelrouter.auth.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;
import org.unreal.modelrouter.auth.security.permission.PermissionCodes;
import org.unreal.modelrouter.auth.security.service.RolePermissionService;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.persistence.jpa.entity.RolePermissionEntity;
import org.unreal.modelrouter.persistence.jpa.repository.RolePermissionRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PermissionManagementController 单元测试（v2.9.8 RBAC Phase 2）
 *
 * 覆盖：GET 权限码列表 / GET 角色权限 / PUT 更新角色（成功 + 非法角色 + 非法码 + 去重）/
 * invalidateCache 触发 / GET 当前用户权限。
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PermissionManagementController 测试")
class PermissionManagementControllerTest {

    @Mock
    private RolePermissionService rolePermissionService;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @InjectMocks
    private PermissionManagementController controller;

    @Nested
    @DisplayName("GET /api/security/permissions - 权限码列表")
    class GetAllPermissionsTests {

        @Test
        @DisplayName("返回全部权限码（43 个，顺序与 ALL_PERMISSION_CODES 一致）")
        void returnsAllPermissionCodes() {
            ResponseEntity<RouterResponse<List<String>>> result = controller.getAllPermissions();

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals(PermissionCodes.ALL_PERMISSION_CODES, result.getBody().getData());
            assertEquals(43, result.getBody().getData().size());
        }
    }

    @Nested
    @DisplayName("GET /api/security/permissions/roles - 角色权限")
    class GetAllRolesWithPermissionsTests {

        @Test
        @DisplayName("返回角色名 → 权限码列表")
        void returnsRolesWithCodes() {
            when(rolePermissionRepository.findAll()).thenReturn(List.of(
                    entity("USER", PermissionCodes.OVERVIEW_DASHBOARD_READ),
                    entity("USER", PermissionCodes.CONFIG_SERVICES_READ),
                    entity("ADMIN", PermissionCodes.SYSTEM_PERMISSIONS_MANAGE)));

            ResponseEntity<RouterResponse<Map<String, List<String>>>> result = controller.getAllRolesWithPermissions();

            assertTrue(result.getBody().isSuccess());
            Map<String, List<String>> data = result.getBody().getData();
            assertEquals(List.of(PermissionCodes.OVERVIEW_DASHBOARD_READ, PermissionCodes.CONFIG_SERVICES_READ),
                    data.get("USER"));
            assertEquals(List.of(PermissionCodes.SYSTEM_PERMISSIONS_MANAGE), data.get("ADMIN"));
        }
    }

    @Nested
    @DisplayName("PUT /api/security/permissions/roles/{roleName} - 更新角色权限")
    class UpdateRolePermissionsTests {

        @Test
        @DisplayName("成功更新（删除旧映射 + 保存新映射 + 触发 invalidateCache）")
        void updateSuccess() {
            List<String> codes = List.of(
                    PermissionCodes.OVERVIEW_DASHBOARD_READ, PermissionCodes.CONFIG_SERVICES_READ);

            ResponseEntity<RouterResponse<List<String>>> result =
                    controller.updateRolePermissions("user", codes);

            assertTrue(result.getBody().isSuccess());
            assertEquals(codes, result.getBody().getData());
            verify(rolePermissionRepository).deleteByRoleName("USER");
            ArgumentCaptor<List<RolePermissionEntity>> captor = entityListCaptor();
            verify(rolePermissionRepository).saveAll(captor.capture());
            assertEquals(2, captor.getValue().size());
            assertEquals("USER", captor.getValue().get(0).getRoleName());
            verify(rolePermissionService).invalidateCache();
        }

        @Test
        @DisplayName("角色名大小写不敏感，统一大写存储")
        void roleNameNormalizedToUpperCase() {
            controller.updateRolePermissions("  Viewer  ",
                    List.of(PermissionCodes.OVERVIEW_DASHBOARD_READ));

            verify(rolePermissionRepository).deleteByRoleName("VIEWER");
            verify(rolePermissionService).invalidateCache();
        }

        @Test
        @DisplayName("非法角色名 → 400 INVALID_ROLE，不触碰仓库与缓存")
        void invalidRoleNameRejected() {
            ResponseEntity<RouterResponse<List<String>>> result =
                    controller.updateRolePermissions("ghost", List.of(PermissionCodes.OVERVIEW_DASHBOARD_READ));

            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            assertEquals("INVALID_ROLE", result.getBody().getErrorCode());
            verify(rolePermissionRepository, never()).deleteByRoleName(anyString());
            verify(rolePermissionRepository, never()).saveAll(anyList());
            verify(rolePermissionService, never()).invalidateCache();
        }

        @Test
        @DisplayName("非法权限码 → 400 INVALID_PERMISSION，不触碰仓库与缓存")
        void invalidPermissionCodeRejected() {
            ResponseEntity<RouterResponse<List<String>>> result =
                    controller.updateRolePermissions("user", List.of("bogus:code:read"));

            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertEquals("INVALID_PERMISSION", result.getBody().getErrorCode());
            verify(rolePermissionRepository, never()).deleteByRoleName(anyString());
            verify(rolePermissionService, never()).invalidateCache();
        }

        @Test
        @DisplayName("合法与非法码混合 → 400 INVALID_PERMISSION")
        void mixedValidAndInvalidCodesRejected() {
            ResponseEntity<RouterResponse<List<String>>> result = controller.updateRolePermissions(
                    "user", List.of(PermissionCodes.OVERVIEW_DASHBOARD_READ, "bogus:code:read"));

            assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
            assertEquals("INVALID_PERMISSION", result.getBody().getErrorCode());
            verify(rolePermissionService, never()).invalidateCache();
        }

        @Test
        @DisplayName("重复权限码去重、空白与 null 过滤后再保存")
        void codesDeduplicatedAndCleaned() {
            List<String> raw = new java.util.ArrayList<>();
            raw.add(PermissionCodes.OVERVIEW_DASHBOARD_READ);
            raw.add(" ");
            raw.add(PermissionCodes.OVERVIEW_DASHBOARD_READ);
            raw.add(null);

            ResponseEntity<RouterResponse<List<String>>> result = controller.updateRolePermissions("user", raw);

            assertTrue(result.getBody().isSuccess());
            assertEquals(List.of(PermissionCodes.OVERVIEW_DASHBOARD_READ), result.getBody().getData());
            ArgumentCaptor<List<RolePermissionEntity>> captor = entityListCaptor();
            verify(rolePermissionRepository).saveAll(captor.capture());
            assertEquals(1, captor.getValue().size());
        }
    }

    @Nested
    @DisplayName("GET /api/auth/permissions - 当前用户权限")
    class GetCurrentUserPermissionsTests {

        @Test
        @DisplayName("返回 JWT permissions claim（过滤 ROLE_ 前缀）")
        void returnsPermissionsFromJwt() {
            JwtAuthentication auth = authenticated("user", List.of("USER"), List.of(
                    PermissionCodes.OVERVIEW_DASHBOARD_READ, PermissionCodes.CONFIG_SERVICES_READ));

            ResponseEntity<RouterResponse<List<String>>> result = controller.getCurrentUserPermissions(auth);

            assertTrue(result.getBody().isSuccess());
            assertEquals(List.of(PermissionCodes.OVERVIEW_DASHBOARD_READ, PermissionCodes.CONFIG_SERVICES_READ),
                    result.getBody().getData());
        }

        @Test
        @DisplayName("ADMIN 返回全量权限码")
        void adminReturnsAllCodes() {
            JwtAuthentication auth = authenticated("admin", List.of("ADMIN"), List.of());

            ResponseEntity<RouterResponse<List<String>>> result = controller.getCurrentUserPermissions(auth);

            assertEquals(PermissionCodes.ALL_PERMISSION_CODES, result.getBody().getData());
        }

        @Test
        @DisplayName("未认证（null / authenticated=false）→ 空列表")
        void unauthenticatedReturnsEmpty() {
            assertTrue(controller.getCurrentUserPermissions(null).getBody().getData().isEmpty());

            JwtAuthentication unauthenticated = new JwtAuthentication(
                    "user", "token", List.of("USER"), List.of(PermissionCodes.OVERVIEW_DASHBOARD_READ));
            assertTrue(controller.getCurrentUserPermissions(unauthenticated).getBody().getData().isEmpty());
        }
    }

    private static JwtAuthentication authenticated(final String subject,
                                                   final List<String> roles,
                                                   final List<String> permissions) {
        JwtAuthentication auth = new JwtAuthentication(subject, "token", roles, permissions);
        auth.setAuthenticated(true);
        return auth;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<RolePermissionEntity>> entityListCaptor() {
        return (ArgumentCaptor<List<RolePermissionEntity>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
    }

    private static RolePermissionEntity entity(final String role, final String code) {
        return RolePermissionEntity.builder().roleName(role).permissionCode(code).build();
    }
}
