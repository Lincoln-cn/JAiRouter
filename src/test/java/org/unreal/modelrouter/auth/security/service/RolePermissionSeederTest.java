package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.permission.PermissionCodes;
import org.unreal.modelrouter.auth.security.permission.PermissionRule;
import org.unreal.modelrouter.auth.security.permission.PermissionRuleRegistry;
import org.unreal.modelrouter.persistence.jpa.entity.RolePermissionEntity;
import org.unreal.modelrouter.persistence.jpa.repository.RolePermissionRepository;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RolePermissionSeeder 单元测试（v2.9.8 RBAC Phase 2 / #144 增量收敛）
 *
 * <p>覆盖：表空播种 4 角色模板（数量与排除项）；表非空时的增量收敛契约——
 * 只补种「规则所需 ∩ 模板」的缺失码、不删不改既有行、手工定制角色整角色跳过、
 * 幂等、不补种不被任何规则需要的装饰性权限码。</p>
 *
 * <p>被测对象是真实 {@link RolePermissionSeeder} + 真实 {@link PermissionRuleRegistry}
 * + 真实 {@link RolePermissionService} + 手写内存仓库（{@link InMemoryRolePermissionRepository}），
 * 不使用 Mockito。</p>
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@DisplayName("RolePermissionSeeder 测试")
class RolePermissionSeederTest {

    private InMemoryRolePermissionRepository repository;
    private RolePermissionRepository rolePermissionRepository;
    private RolePermissionService rolePermissionService;
    private RolePermissionSeeder seeder;

    @BeforeEach
    void setUp() {
        repository = InMemoryRolePermissionRepository.create();
        rolePermissionRepository = repository.proxy();
        rolePermissionService = new RolePermissionService(rolePermissionRepository);
        seeder = new RolePermissionSeeder(rolePermissionRepository, new PermissionRuleRegistry(),
                rolePermissionService);
    }

    @Nested
    @DisplayName("表空播种")
    class SeedWhenEmptyTests {

        @Test
        @DisplayName("表空时种入 4 个角色模板，权限码数量正确")
        void seedsFourRolesWithCorrectCounts() {
            seeder.seedIfEmpty();

            Map<String, Long> counts = repository.rows().stream().collect(Collectors.groupingBy(
                    RolePermissionEntity::getRoleName, Collectors.counting()));

            assertEquals(Set.of("ADMIN", "OPERATOR", "USER", "VIEWER"), counts.keySet());
            assertEquals(PermissionCodes.ALL_PERMISSION_CODES.size(), counts.get("ADMIN").intValue());
            // R2-P1-03: + monitoring:exceptions:read/write；#116: + monitoring:config:write, security:audit:write
            assertEquals(43L, counts.get("OPERATOR"));
            assertEquals(28L, counts.get("USER"));
            assertEquals(27L, counts.get("VIEWER"));
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
            assertTrue(codes.contains(PermissionCodes.MONITORING_EXCEPTIONS_READ));
            assertFalse(codes.contains(PermissionCodes.MONITORING_EXCEPTIONS_WRITE));
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
        @DisplayName("VIEWER 仅含 :read 权限码（27 个），排除 view/write/manage/use")
        void viewerContainsOnlyReadCodes() {
            List<String> codes = codesOf("VIEWER");

            assertEquals(27, codes.size());
            assertTrue(codes.stream().allMatch(code -> code.endsWith(":read")));
            assertTrue(codes.contains(PermissionCodes.SECURITY_AUDIT_READ));
            assertFalse(codes.contains(PermissionCodes.CALLHISTORY_VIEW));
            assertFalse(codes.contains(PermissionCodes.LB_CONFIG_WRITE));
            assertFalse(codes.contains(PermissionCodes.AI_PLAYGROUND_USE));
            assertFalse(codes.contains(PermissionCodes.TRACING_CONFIG_MANAGE));
        }
    }

    @Nested
    @DisplayName("非空增量收敛（#144）")
    class ReconcileWhenNotEmptyTests {

        @Test
        @DisplayName("角色为模板的陈旧子集时，只补种规则所需且缺失的权限码，既有行不删不改")
        void staleSubsetBackfillsOnlyMissingRuleRequiredCodes() {
            // OPERATOR 旧部署快照：只持有 2 个早期码（均为模板子集）
            repository.seed("OPERATOR", PermissionCodes.CONFIG_SERVICES_READ, PermissionCodes.CONFIG_SERVICES_WRITE);
            repository.seed("USER", PermissionCodes.OVERVIEW_DASHBOARD_READ);
            Map<String, Long> idsBefore = repository.idByKey();

            seeder.seedIfEmpty();

            Set<String> insertedKeys = repository.insertedKeys();
            // 既有行必须原样保留（同 id、同 (role,code)，未被删除/更新）
            for (Map.Entry<String, Long> entry : idsBefore.entrySet()) {
                assertEquals(entry.getValue(), repository.idByKey().get(entry.getKey()),
                        "既有行 " + entry.getKey() + " 不得被删除或修改");
            }
            assertEquals(idsBefore.size() + insertedKeys.size(), repository.rows().size(),
                    "只允许新增，不允许删改");

            // 补种的必须是「规则需要 ∩ 该角色模板」且原先缺失的码
            Set<String> required = requiredByRealRules();
            for (String key : insertedKeys) {
                String role = key.substring(0, key.indexOf('|'));
                String code = key.substring(key.indexOf('|') + 1);
                Set<String> template = new LinkedHashSet<>(
                        RolePermissionSeeder.DEFAULT_ROLE_TEMPLATES.get(role));
                assertTrue(template.contains(code), "补种码必须在 " + role + " 模板内: " + code);
                assertTrue(required.contains(code), "补种码必须被某条规则需要: " + code);
                assertFalse(idsBefore.containsKey(key), "不得重复插入既有码: " + key);
            }
            // 真实缺口（issue #144 实测）必须被补上
            Set<String> operatorCodes = repository.codesOf("OPERATOR");
            assertTrue(operatorCodes.contains(PermissionCodes.CONFIG_CACHE_READ));
            assertTrue(operatorCodes.contains(PermissionCodes.CONFIG_CACHE_WRITE));
            assertTrue(operatorCodes.contains(PermissionCodes.CONFIG_QUOTA_READ));
            assertTrue(operatorCodes.contains(PermissionCodes.MONITORING_QUOTA_READ));
            assertTrue(operatorCodes.contains(PermissionCodes.MONITORING_EXCEPTIONS_READ));
            assertTrue(operatorCodes.contains(PermissionCodes.MONITORING_EXCEPTIONS_WRITE));
            assertTrue(operatorCodes.contains(PermissionCodes.MONITORING_CONFIG_WRITE));
            assertTrue(operatorCodes.contains(PermissionCodes.SECURITY_AUDIT_WRITE));
            // USER 的模板内规则所需码也应补齐
            assertTrue(repository.codesOf("USER").contains(PermissionCodes.CONFIG_CACHE_READ));
        }

        @Test
        @DisplayName("角色持有模板外权限码（手工定制）时，该角色完全不被触碰")
        void customizedRoleIsLeftUntouched() {
            repository.seed("OPERATOR",
                    PermissionCodes.CONFIG_SERVICES_READ,
                    "custom:hand:rolled");
            Set<String> before = repository.codesOf("OPERATOR");

            seeder.seedIfEmpty();

            assertEquals(before, repository.codesOf("OPERATOR"),
                    "手工定制角色的权限集合必须原样保留");
            assertTrue(repository.insertedKeys().stream()
                            .noneMatch(key -> key.startsWith("OPERATOR|")),
                    "手工定制角色不得插入任何行");
            assertTrue(repository.rows().stream()
                    .noneMatch(row -> "OPERATOR".equals(row.getRoleName())
                            && !before.contains(row.getPermissionCode())),
                    "OPERATOR 不得出现新行");
        }

        @Test
        @DisplayName("模板外权限码导致整角色跳过：即使同时缺失规则所需码也不补")
        void customizedRoleSkipsEvenWhenRuleCodesMissing() {
            repository.seed("OPERATOR", "custom:hand:rolled");

            seeder.seedIfEmpty();

            assertTrue(repository.codesOf("OPERATOR").equals(Set.of("custom:hand:rolled")),
                    "只持有模板外码的角色必须保持原样，不得补齐规则所需码");
        }

        @Test
        @DisplayName("重复收敛幂等：第二次执行不再插入任何行")
        void reconcileIsIdempotent() {
            repository.seed("OPERATOR", PermissionCodes.CONFIG_SERVICES_READ);

            seeder.seedIfEmpty();
            int afterFirst = repository.rows().size();
            int batchesAfterFirst = repository.saveAllBatches();
            assertTrue(afterFirst > 1, "首次收敛应补种缺失码");

            seeder.seedIfEmpty();

            assertEquals(afterFirst, repository.rows().size(), "第二次收敛不得新增行");
            assertEquals(batchesAfterFirst, repository.saveAllBatches(),
                    "第二次收敛不应再调用 saveAll");
        }

        @Test
        @DisplayName("模板内但不被任何规则需要的装饰性权限码，不得被补种")
        void decorativeTemplateCodeNotBackfilled() {
            Set<String> required = requiredByRealRules();
            List<String> userTemplate = RolePermissionSeeder.DEFAULT_ROLE_TEMPLATES.get("USER");
            List<String> decorative = userTemplate.stream()
                    .filter(code -> !required.contains(code))
                    .collect(Collectors.toList());
            assertFalse(decorative.isEmpty(), "前置条件：USER 模板存在不被任何规则需要的装饰性码");

            // 只保留一个模板内码，使装饰性码和规则所需码都处于缺失状态
            repository.seed("USER", PermissionCodes.OVERVIEW_DASHBOARD_READ);

            seeder.seedIfEmpty();

            Set<String> inserted = repository.insertedCodes();
            for (String code : decorative) {
                assertFalse(repository.codesOf("USER").contains(code),
                        "装饰性权限码不得被补种: " + code);
            }
            // 对照：规则所需且模板内的缺失码必须被补种
            Set<String> userTemplateSet = new LinkedHashSet<>(userTemplate);
            List<String> expected = userTemplate.stream()
                    .filter(required::contains)
                    .filter(code -> !code.equals(PermissionCodes.OVERVIEW_DASHBOARD_READ))
                    .collect(Collectors.toList());
            for (String code : expected) {
                assertTrue(repository.codesOf("USER").contains(code),
                        "规则所需的模板内码必须被补种: " + code);
            }
            assertTrue(userTemplateSet.containsAll(repository.codesOf("USER")));
            assertFalse(inserted.contains(PermissionCodes.AI_PLAYGROUND_USE));
            assertFalse(inserted.contains(PermissionCodes.CB_HISTORY_READ));
        }

        @Test
        @DisplayName("完全没有任何权限行的角色被跳过，不整表播种")
        void roleWithNoRowsIsSkipped() {
            repository.seed("OPERATOR", PermissionCodes.CONFIG_SERVICES_READ);

            seeder.seedIfEmpty();

            assertFalse(repository.codesOf("USER").contains(PermissionCodes.CONFIG_QUOTA_READ),
                    "无行角色不得被增量补种");
            assertTrue(repository.rows().stream()
                            .noneMatch(row -> "VIEWER".equals(row.getRoleName())),
                    "无行角色不得被播种");
        }

        @Test
        @DisplayName("收敛写入后权限缓存失效：启动窗口内已查询过的角色能读到新码")
        void invalidateCacheAfterReconcile() {
            repository.seed("OPERATOR", PermissionCodes.CONFIG_SERVICES_READ);
            // 先查询一次，把「陈旧子集」写入 Caffeine 缓存
            List<String> cached = rolePermissionService.getPermissionCodesForRoles(List.of("OPERATOR"));
            assertEquals(List.of(PermissionCodes.CONFIG_SERVICES_READ), cached);

            seeder.seedIfEmpty();

            List<String> refreshed = rolePermissionService.getPermissionCodesForRoles(List.of("OPERATOR"));
            assertTrue(refreshed.contains(PermissionCodes.CONFIG_CACHE_READ),
                    "补种后必须通过缓存失效读到新权限码，而不是 5 分钟内的陈旧缓存");
            assertTrue(refreshed.contains(PermissionCodes.SECURITY_AUDIT_WRITE));
        }
    }

    private Set<String> requiredByRealRules() {
        return new PermissionRuleRegistry().getRules().stream()
                .map(PermissionRule::permissionCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> codesOf(final String role) {
        seeder.seedIfEmpty();
        return new ArrayList<>(repository.codesOf(role));
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

    /**
     * 手写内存角色-权限仓库（真实现，不用 Mockito）。
     * 动态代理实现 {@link RolePermissionRepository}，避免为 JpaRepository 编写样板。
     */
    private static final class InMemoryRolePermissionRepository implements InvocationHandler {

        private final List<RolePermissionEntity> rows = new ArrayList<>();
        private final List<List<RolePermissionEntity>> saveAllBatches = new ArrayList<>();
        private long nextId = 1L;

        static InMemoryRolePermissionRepository create() {
            return new InMemoryRolePermissionRepository();
        }

        RolePermissionRepository proxy() {
            return (RolePermissionRepository) Proxy.newProxyInstance(
                    RolePermissionRepository.class.getClassLoader(),
                    new Class<?>[]{RolePermissionRepository.class},
                    this);
        }

        /** 直接写入一行（模拟历史快照），返回 (role|code) → id 以便断言不删不改。 */
        void seed(final String role, final String... codes) {
            for (String code : codes) {
                rows.add(RolePermissionEntity.builder()
                        .id(nextId++)
                        .roleName(role)
                        .permissionCode(code)
                        .build());
            }
        }

        List<RolePermissionEntity> rows() {
            return new ArrayList<>(rows);
        }

        Set<String> codesOf(final String role) {
            return rows.stream()
                    .filter(row -> role.equals(row.getRoleName()))
                    .map(RolePermissionEntity::getPermissionCode)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        Map<String, Long> idByKey() {
            return rows.stream().collect(Collectors.toMap(
                    row -> row.getRoleName() + "|" + row.getPermissionCode(),
                    RolePermissionEntity::getId,
                    (a, b) -> a));
        }

        /** saveAll 新插入的 (role|code) 键快照。 */
        Set<String> insertedKeys() {
            return saveAllBatches.stream()
                    .flatMap(List::stream)
                    .map(row -> row.getRoleName() + "|" + row.getPermissionCode())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        /** saveAll 新插入的 permissionCode 快照。 */
        Set<String> insertedCodes() {
            return saveAllBatches.stream()
                    .flatMap(List::stream)
                    .map(RolePermissionEntity::getPermissionCode)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        int saveAllBatches() {
            return saveAllBatches.size();
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) {
            final String name = method.getName();
            switch (name) {
                case "toString":
                    return "InMemoryRolePermissionRepository(rows=" + rows.size() + ")";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "count":
                    return (long) rows.size();
                case "findByRoleName":
                    return rows.stream()
                            .filter(row -> Objects.equals(args[0], row.getRoleName()))
                            .collect(Collectors.toList());
                case "findByRoleNameIn": {
                    Collection<String> names = (Collection<String>) args[0];
                    return rows.stream()
                            .filter(row -> names.contains(row.getRoleName()))
                            .collect(Collectors.toList());
                }
                case "findAll":
                    return rows();
                case "saveAll": {
                    List<RolePermissionEntity> batch = new ArrayList<>();
                    for (Object item : (Iterable<?>) args[0]) {
                        RolePermissionEntity entity = (RolePermissionEntity) item;
                        String key = entity.getRoleName() + "|" + entity.getPermissionCode();
                        boolean duplicate = rows.stream().anyMatch(row ->
                                Objects.equals(row.getRoleName(), entity.getRoleName())
                                        && Objects.equals(row.getPermissionCode(), entity.getPermissionCode()));
                        if (duplicate) {
                            throw new IllegalStateException("唯一约束冲突: " + key);
                        }
                        if (entity.getId() == null) {
                            entity.setId(nextId++);
                        }
                        rows.add(entity);
                        batch.add(entity);
                    }
                    saveAllBatches.add(batch);
                    return batch;
                }
                case "deleteByRoleName":
                    rows.removeIf(row -> Objects.equals(args[0], row.getRoleName()));
                    return null;
                default:
                    throw new UnsupportedOperationException("测试假实现未覆盖的仓库方法: " + name);
            }
        }
    }
}
