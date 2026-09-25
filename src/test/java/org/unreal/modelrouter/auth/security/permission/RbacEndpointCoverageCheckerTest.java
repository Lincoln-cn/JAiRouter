package org.unreal.modelrouter.auth.security.permission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RBAC 端点覆盖自检（#128 Phase 1/2）单元测试。
 *
 * <p>已覆盖样例的输入直接取自 {@link PermissionRuleRegistry#getRules()}（真实规则矩阵），
 * 避免手抄路径清单随代码腐化。Phase 2 起区分 EXEMPT（显式豁免）与 MISSING（可行动缺口）。
 *
 * @author JAiRouter Team
 * @since 3.0.4
 */
@DisplayName("RbacEndpointCoverageChecker 测试")
class RbacEndpointCoverageCheckerTest {

    private PermissionRuleRegistry registry;
    private RbacEndpointCoverageChecker checker;

    @BeforeEach
    void setUp() {
        registry = new PermissionRuleRegistry();
        // 单测不依赖 Spring 上下文；enumerateMappedEndpoints 仅在 ApplicationRunner.run 使用
        checker = new RbacEndpointCoverageChecker(registry, (ApplicationContext) null);
    }

    @Test
    @DisplayName("真实登记规则的端点 -> reported as covered")
    void knownCoveredEndpointFromRegistryReportedAsCovered() {
        // 输入直接来自规则矩阵：每条规则的 pathPattern + 它声明的 HTTP 方法
        List<RbacEndpointCoverageChecker.MappedEndpoint> endpoints = registry.getRules().stream()
                .map(rule -> new RbacEndpointCoverageChecker.MappedEndpoint(
                        rule.methods().isEmpty() ? Set.of() : rule.methods(),
                        rule.pathPattern()))
                .toList();

        RbacEndpointCoverageReport report = checker.check(endpoints);

        assertTrue(endpoints.size() > 0, "规则矩阵不应为空");
        assertEquals(0, report.missingCount(),
                "规则矩阵自身声明的端点必须全部被 findRule 命中: " + report.missingEndpoints());
        assertEquals(endpoints.size(), report.coveredCount());
    }

    @Test
    @DisplayName("未登记路径 -> reported as missing（可行动信号），且列出 method + pattern")
    void knownUnregisteredPathReportedAsMissing() {
        String unregistered = "/api/coverage-self-check-probe/does-not-exist";
        assertFalse(registry.findRule(HttpMethod.GET, unregistered).isPresent(),
                "探针路径不应命中任何规则");
        assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.GET, unregistered),
                "探针路径不应命中豁免清单");

        RbacEndpointCoverageReport report = checker.check(List.of(
                new RbacEndpointCoverageChecker.MappedEndpoint(Set.of(HttpMethod.GET), unregistered)));

        assertEquals(1, report.missingCount());
        assertEquals(1, report.missingEndpoints().size());
        assertTrue(report.missingEndpoints().get(0).contains(unregistered),
                "缺口清单必须包含缺失路径: " + report.missingEndpoints());
        assertTrue(report.missingEndpoints().get(0).contains("GET"),
                "缺口清单必须包含 HTTP 方法: " + report.missingEndpoints());
        assertEquals(0, report.coveredCount());
        assertEquals(0, report.exemptCount());
    }

    @Test
    @DisplayName("豁免清单端点 -> reported as exempt（不是 missing）")
    void exemptListedEndpointsReportedAsExemptNotMissing() {
        // 直接取豁免清单真实条目构造输入，避免手抄路径腐化
        List<RbacEndpointCoverageChecker.MappedEndpoint> endpoints = RbacExemptEndpoints.exemptions().stream()
                .map(e -> new RbacEndpointCoverageChecker.MappedEndpoint(
                        e.methods().isEmpty() ? Set.of() : e.methods(), e.pathPattern()))
                .toList();

        RbacEndpointCoverageReport report = checker.check(endpoints);

        assertFalse(endpoints.isEmpty(), "豁免清单不应为空");
        assertEquals(0, report.missingCount(),
                "豁免清单端点不得报告为缺口: " + report.missingEndpoints());
        assertEquals(0, report.coveredCount(),
                "豁免清单端点不应被规则覆盖（有意不登记）: " + report.exemptEndpoints());
        assertEquals(endpoints.size(), report.exemptCount(),
                "豁免清单端点必须报告为 EXEMPT: " + report.exemptEndpoints());
    }

    @Test
    @DisplayName("排除前缀（/admin/**、/actuator/**、登录端点等）不计入 missing 缺口")
    void excludedPrefixesAreNotReportedAsGaps() {
        List<RbacEndpointCoverageChecker.MappedEndpoint> endpoints = List.of(
                // 非 /api/**：静态 SPA / actuator / 指标 —— 不经过 PermissionAuthorizationManager
                new RbacEndpointCoverageChecker.MappedEndpoint(Set.of(HttpMethod.GET), "/admin/index.html"),
                new RbacEndpointCoverageChecker.MappedEndpoint(Set.of(HttpMethod.GET), "/actuator/health"),
                new RbacEndpointCoverageChecker.MappedEndpoint(Set.of(HttpMethod.POST), "/favicon.ico"),
                // /api/** 下的安全链独立规则
                new RbacEndpointCoverageChecker.MappedEndpoint(Set.of(HttpMethod.GET), "/api/health-status/subscribe"),
                new RbacEndpointCoverageChecker.MappedEndpoint(Set.of(HttpMethod.GET), "/api/model-stats/clear"),
                new RbacEndpointCoverageChecker.MappedEndpoint(Set.of(HttpMethod.POST), "/api/auth/jwt/login"),
                new RbacEndpointCoverageChecker.MappedEndpoint(Set.of(HttpMethod.POST), "/api/auth/jwt/validate"));

        RbacEndpointCoverageReport report = checker.check(endpoints);

        assertEquals(0, report.missingCount(), "排除路径不得报告为缺口: " + report.missingEndpoints());
        assertEquals(0, report.coveredCount());
        assertEquals(0, report.exemptCount());
        assertEquals(endpoints.size(), report.excludedCount());
    }

    @Test
    @DisplayName("isExcluded 与排除清单一致（/api/** 业务端点不被误排除）")
    void exclusionPredicateMatchesDocumentedList() {
        assertTrue(RbacEndpointCoverageChecker.isExcluded("/admin/**"));
        assertTrue(RbacEndpointCoverageChecker.isExcluded("/actuator/jwt-persistence-health"));
        assertTrue(RbacEndpointCoverageChecker.isExcluded("/api/health-status/**"));
        assertTrue(RbacEndpointCoverageChecker.isExcluded("/api/model-stats/**"));
        assertTrue(RbacEndpointCoverageChecker.isExcluded("/api/auth/jwt/login"));
        assertTrue(RbacEndpointCoverageChecker.isExcluded("/api/auth/jwt/validate"));
        assertTrue(RbacEndpointCoverageChecker.isExcluded("/error"));

        // 业务管理端点必须参与覆盖检查（不得被排除清单吞掉）
        assertFalse(RbacEndpointCoverageChecker.isExcluded("/api/config/instance/{id}"));
        assertFalse(RbacEndpointCoverageChecker.isExcluded("/api/auth/api-keys"));
        assertFalse(RbacEndpointCoverageChecker.isExcluded("/api/auth/permissions"));
    }

    @Test
    @DisplayName("映射模式 → 样例路径：变量/通配符归一后再走 findRule")
    void samplePathDerivedFromMappingPattern() {
        assertEquals("/api/config/type/x", RbacEndpointCoverageChecker.toSamplePath("/api/config/type/{name}"));
        assertEquals("/api/services/x/circuitbreaker/x",
                RbacEndpointCoverageChecker.toSamplePath("/api/services/{serviceType}/circuitbreaker/{id}"));
        assertEquals("/api/security/jwt/accounts/x",
                RbacEndpointCoverageChecker.toSamplePath("/api/security/jwt/accounts/**"));

        // 样例路径必须能命中对应规则
        assertTrue(registry.findRule(HttpMethod.GET,
                RbacEndpointCoverageChecker.toSamplePath("/api/config/type/{name}")).isPresent());
    }

    @Test
    @DisplayName("方法约束：GET-only 规则不覆盖 POST（部分方法缺失即 missing）")
    void partialMethodCoverageCountsAsMissing() {
        // /api/config/circuit-breaker/** 仅有 get+write；用 TRACE 表示「无规则方法」
        RbacEndpointCoverageReport report = checker.check(List.of(
                new RbacEndpointCoverageChecker.MappedEndpoint(
                        Set.of(HttpMethod.GET, HttpMethod.TRACE),
                        "/api/config/circuit-breaker/{name}")));

        assertEquals(1, report.missingCount());
        assertTrue(report.missingEndpoints().get(0).contains("TRACE"),
                "缺失方法必须出现在缺口清单: " + report.missingEndpoints());
        assertFalse(report.missingEndpoints().get(0).contains("GET"),
                "已覆盖方法不应出现在缺口清单: " + report.missingEndpoints());
    }

    @Test
    @DisplayName("/api/v1/** 豁免仅限写方法：未登记 GET /api/v1/ 仍报告为 missing")
    void apiV1WriteExemptionDoesNotSwallowUnmatchedGets() {
        assertTrue(RbacExemptEndpoints.isExempt(HttpMethod.POST, "/api/v1/chat/completions"),
                "POST /api/v1/** 必须豁免");
        assertFalse(RbacExemptEndpoints.isExempt(HttpMethod.GET, "/api/v1/unmatched-get-probe"),
                "GET /api/v1/ 未登记路径不在豁免清单（写方法限定）");

        RbacEndpointCoverageReport report = checker.check(List.of(
                new RbacEndpointCoverageChecker.MappedEndpoint(
                        Set.of(HttpMethod.GET), "/api/v1/unmatched-get-probe"),
                new RbacEndpointCoverageChecker.MappedEndpoint(
                        Set.of(HttpMethod.POST), "/api/v1/chat/completions")));

        assertEquals(1, report.missingCount(),
                "未登记 GET /api/v1/ 必须是可行动缺口: " + report.missingEndpoints());
        assertEquals(1, report.exemptCount(),
                "POST /api/v1/chat/completions 必须是豁免: " + report.exemptEndpoints());
    }
}
