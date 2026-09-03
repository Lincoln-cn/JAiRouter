package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.auth.security.permission.PermissionCodes;
import org.unreal.modelrouter.persistence.jpa.entity.RolePermissionEntity;
import org.unreal.modelrouter.persistence.jpa.repository.RolePermissionRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RolePermissionSeeder 单元测试（v2.9.8 RBAC Phase 2）
 *
 * 覆盖：表空播种 4 角色模板（数量与排除项）/ 非空跳过 / 幂等。
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RolePermissionSeeder 测试")
class RolePermissionSeederTest {

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    private RolePermissionSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new RolePermissionSeeder(rolePermissionRepository);
    }

    @Nested
    @DisplayName("表空播种")
    class SeedWhenEmptyTests {

        @Test
        @DisplayName("表空时种入 4 个角色模板，权限码数量正确")
        void seedsFourRolesWithCorrectCounts() {
            when(rolePermissionRepository.count()).thenReturn(0L);

            seeder.seedIfEmpty();

            ArgumentCaptor<List<RolePermissionEntity>> captor = entityListCaptor();
            verify(rolePermissionRepository).saveAll(captor.capture());
            List<RolePermissionEntity> saved = captor.getValue();
            Map<String, Long> counts = saved.stream().collect(Collectors.groupingBy(
                    RolePermissionEntity::getRoleName, Collectors.counting()));

            assertEquals(Set.of("ADMIN", "OPERATOR", "USER", "VIEWER"), counts.keySet());
            assertEquals(44L, counts.get("ADMIN"));
            assertEquals(35L, counts.get("OPERATOR"));
            assertEquals(24L, counts.get("USER"));
            assertEquals(23L, counts.get("VIEWER"));
        }

        @Test
        @DisplayName("ADMIN 模板等于全量权限码")
        void adminTemplateEqualsAllCodes() {
            assertEquals(PermissionCodes.ALL_PERMISSION_CODES,
                    RolePermissionSeeder.DEFAULT_ROLE_TEMPLATES.get("ADMIN"));
        }

        @Test
        @DisplayName("OPERATOR 排除 system:* / security:*:manage / actuator:*，包含 security:audit:read")
        void operatorExcludesSystemSecurityManageActuator() {
            List<String> codes = codesOf("OPERATOR");

            assertTrue(codes.contains(PermissionCodes.CONFIG_SERVICES_READ));
            assertTrue(codes.contains(PermissionCodes.CONFIG_SERVICES_WRITE));
            assertTrue(codes.contains(PermissionCodes.LB_CONFIG_WRITE));
            assertTrue(codes.contains(PermissionCodes.SECURITY_AUDIT_READ));
            assertFalse(codes.contains(PermissionCodes.SYSTEM_ACCOUNTS_MANAGE));
            assertFalse(codes.contains(PermissionCodes.SYSTEM_PERMISSIONS_MANAGE));
            assertFalse(codes.contains(PermissionCodes.SECURITY_APIKEYS_MANAGE));
            assertFalse(codes.contains(PermissionCodes.SECURITY_JWTTOKENS_MANAGE));
            assertFalse(codes.contains(PermissionCodes.SECURITY_BLACKLIST_MANAGE));
            assertFalse(codes.contains(PermissionCodes.ACTUATOR_ADMIN_MANAGE));
            assertFalse(codes.contains(PermissionCodes.CALLHISTORY_VIEW));
            assertFalse(codes.contains(PermissionCodes.TRACING_CONFIG_MANAGE));
            assertFalse(codes.contains(PermissionCodes.AI_PLAYGROUND_USE));
        }

        @Test
        @DisplayName("USER 模板 = dashboard + config:*:read + lb/cb/rl + monitoring:*:read "
                + "+ tracing dashboard/search + ai")
        void userTemplateMatchesSpec() {
            List<String> codes = codesOf("USER");

            assertTrue(codes.contains(PermissionCodes.OVERVIEW_DASHBOARD_READ));
            for (String configRead : configReadCodes()) {
                assertTrue(codes.contains(configRead), "USER 应包含 " + configRead);
            }
            assertTrue(codes.contains(PermissionCodes.LB_MONITORING_READ));
            assertTrue(codes.contains(PermissionCodes.LB_CONFIG_WRITE));
            assertTrue(codes.contains(PermissionCodes.CB_MONITORING_READ));
            assertTrue(codes.contains(PermissionCodes.CB_HISTORY_READ));
            assertTrue(codes.contains(PermissionCodes.RL_MONITORING_READ));
            for (String monitoringRead : monitoringReadCodes()) {
                assertTrue(codes.contains(monitoringRead), "USER 应包含 " + monitoringRead);
            }
            assertTrue(codes.contains(PermissionCodes.TRACING_DASHBOARD_READ));
            assertTrue(codes.contains(PermissionCodes.TRACING_SEARCH_READ));
            assertTrue(codes.contains(PermissionCodes.AI_PLAYGROUND_USE));
            // 排除项：config 写、security 读/管理、system、actuator、callhistory
            assertFalse(codes.contains(PermissionCodes.CONFIG_SERVICES_WRITE));
            assertFalse(codes.contains(PermissionCodes.SECURITY_AUDIT_READ));
            assertFalse(codes.contains(PermissionCodes.CALLHISTORY_VIEW));
            assertFalse(codes.contains(PermissionCodes.TRACING_CONFIG_MANAGE));
            assertFalse(codes.contains(PermissionCodes.SYSTEM_PERMISSIONS_MANAGE));
            assertFalse(codes.contains(PermissionCodes.ACTUATOR_ADMIN_MANAGE));
        }

        @Test
        @DisplayName("VIEWER 仅含 :read 权限码（23 个），排除 view/write/manage/use")
        void viewerContainsOnlyReadCodes() {
            List<String> codes = codesOf("VIEWER");

            assertEquals(23, codes.size());
            assertTrue(codes.stream().allMatch(code -> code.endsWith(":read")));
            assertTrue(codes.contains(PermissionCodes.SECURITY_AUDIT_READ));
            assertFalse(codes.contains(PermissionCodes.CALLHISTORY_VIEW));
            assertFalse(codes.contains(PermissionCodes.LB_CONFIG_WRITE));
            assertFalse(codes.contains(PermissionCodes.AI_PLAYGROUND_USE));
            assertFalse(codes.contains(PermissionCodes.TRACING_CONFIG_MANAGE));
        }
    }

    @Nested
    @DisplayName("非空跳过 / 幂等")
    class SkipWhenNotEmptyTests {

        @Test
        @DisplayName("表非空时跳过种子，不写入任何数据")
        void skipsWhenTableNotEmpty() {
            when(rolePermissionRepository.count()).thenReturn(5L);

            seeder.seedIfEmpty();

            verify(rolePermissionRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("重复执行幂等：首次播种后表非空，再次执行跳过")
        void idempotentAcrossRuns() {
            when(rolePermissionRepository.count()).thenReturn(0L, 1L);

            seeder.seedIfEmpty();
            seeder.seedIfEmpty();

            verify(rolePermissionRepository, times(1)).saveAll(anyList());
        }
    }

    private List<String> codesOf(final String role) {
        List<RolePermissionEntity> saved = seedAndCapture();
        return saved.stream()
                .filter(entity -> role.equals(entity.getRoleName()))
                .map(RolePermissionEntity::getPermissionCode)
                .collect(Collectors.toList());
    }

    private List<RolePermissionEntity> seedAndCapture() {
        when(rolePermissionRepository.count()).thenReturn(0L);
        seeder.seedIfEmpty();
        ArgumentCaptor<List<RolePermissionEntity>> captor = entityListCaptor();
        verify(rolePermissionRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<RolePermissionEntity>> entityListCaptor() {
        return (ArgumentCaptor<List<RolePermissionEntity>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
    }

    private static List<String> configReadCodes() {
        return PermissionCodes.ALL_PERMISSION_CODES.stream()
                .filter(code -> code.startsWith("config:") && code.endsWith(":read"))
                .collect(Collectors.toList());
    }

    private static List<String> monitoringReadCodes() {
        return PermissionCodes.ALL_PERMISSION_CODES.stream()
                .filter(code -> code.startsWith("monitoring:") && code.endsWith(":read"))
                .collect(Collectors.toList());
    }
}
