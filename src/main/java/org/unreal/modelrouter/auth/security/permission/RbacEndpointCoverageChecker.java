package org.unreal.modelrouter.auth.security.permission;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * RBAC 端点覆盖自检（#128 Phase 1）——让端点/规则漂移可见，而不是静默 fail-open。
 *
 * <p>启动时枚举 WebFlux {@link RequestMappingHandlerMapping} 上的全部映射
 * （method + path pattern），对每个端点用与 {@link PermissionAuthorizationManager}
 * 相同的 {@link PermissionRuleRegistry#findRule(HttpMethod, String)} 判定是否被规则覆盖。
 * 未覆盖清单以单条 WARN 汇总输出，并写入 {@link RbacEndpointCoverageReport} 供
 * Health 指标暴露。
 *
 * <p><b>本阶段不改变任何授权行为</b>：无规则路径仍回退 authenticated（login-only）。
 * fail-closed 切换属后续阶段，需完整端点审计 + 前端回归后才能启用。
 *
 * <p><b>排除清单</b>（不报告为缺口——它们根本不经过 {@code PermissionAuthorizationManager}，
 * 或在 SecurityConfiguration 中有独立授权规则）：
 * <ul>
 *   <li>非 {@code /api/**} 路径：{@code /admin/**} 静态 SPA、{@code /actuator/**}、
 *       {@code /favicon.ico}、Swagger/OpenAPI、{@code /ws/**}、{@code /v1/**} 等
 *       —— SecurityConfiguration 中位于 {@code /api/**}.access(...) 之前的规则；
 *   <li>{@code /api/health-status/**}：SSE，独立 {@code authenticated()}；
 *   <li>{@code /api/model-stats/**}：独立 {@code hasRole('ADMIN')}；
 *   <li>{@code POST /api/auth/jwt/login}、{@code POST /api/auth/jwt/validate}：permitAll 登录/验签端点；
 *   <li>错误/静态资源等非业务映射（如 {@code /error}）——同样不在 {@code /api/**} 下。
 * </ul>
 *
 * @author JAiRouter Team
 * @since 3.0.3
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "jairouter.security.rbac.coverage-check.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RbacEndpointCoverageChecker implements ApplicationRunner {

    /** 空方法约束时按这些动词逐一核对（与 PermissionRule.get/write 使用面一致） */
    private static final List<HttpMethod> DEFAULT_METHODS_TO_CHECK =
            List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH);

    /** path pattern → 样例路径的占位段（{@code {var}} / {@code *} / {@code **}） */
    private static final String SAMPLE_SEGMENT = "x";

    private final PermissionRuleRegistry permissionRuleRegistry;
    private final ApplicationContext applicationContext;

    private volatile RbacEndpointCoverageReport report = RbacEndpointCoverageReport.empty();

    public RbacEndpointCoverageChecker(final PermissionRuleRegistry permissionRuleRegistry,
                                       final ApplicationContext applicationContext) {
        this.permissionRuleRegistry = permissionRuleRegistry;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(final ApplicationArguments args) {
        try {
            this.report = check(enumerateMappedEndpoints());
            logReport(this.report);
        } catch (Exception e) {
            log.warn("RBAC 端点覆盖自检未能完成（不影响启动与授权行为）", e);
        }
    }

    /** 最近一次自检结果（启动前为 empty） */
    public RbacEndpointCoverageReport getReport() {
        return report;
    }

    /**
     * 对给定端点清单执行覆盖判定（可单测，不依赖 Spring 上下文）。
     *
     * @param endpoints 已映射端点（HTTP 方法集合 + 路径模式）
     * @return 覆盖报告
     */
    public RbacEndpointCoverageReport check(final Collection<MappedEndpoint> endpoints) {
        int covered = 0;
        int uncovered = 0;
        int excluded = 0;
        Set<String> uncoveredLines = new TreeSet<>();

        for (MappedEndpoint endpoint : endpoints) {
            if (isExcluded(endpoint.pathPattern())) {
                excluded++;
                continue;
            }
            String samplePath = toSamplePath(endpoint.pathPattern());
            List<HttpMethod> methods = methodsToCheck(endpoint.methods());
            boolean allCovered = true;
            List<String> missingMethods = new ArrayList<>();
            for (HttpMethod method : methods) {
                if (permissionRuleRegistry.findRule(method, samplePath).isEmpty()) {
                    allCovered = false;
                    missingMethods.add(method.name());
                }
            }
            if (allCovered) {
                covered++;
            } else {
                uncovered++;
                uncoveredLines.add(String.join(",", missingMethods) + " " + endpoint.pathPattern());
            }
        }

        int checked = covered + uncovered;
        return new RbacEndpointCoverageReport(
                checked, covered, uncovered, excluded, List.copyOf(uncoveredLines));
    }

    /**
     * 是否为排除路径：不经过 PermissionAuthorizationManager，或在安全链中有独立规则。
     */
    public static boolean isExcluded(final String pathPattern) {
        if (pathPattern == null || pathPattern.isEmpty()) {
            return true;
        }
        String normalized = normalize(pathPattern);
        // 仅 /api/** 走 PermissionAuthorizationManager；其余映射由安全链其他规则处理
        if (!normalized.startsWith("/api/")) {
            return true;
        }
        // SecurityConfiguration 中先于 /api/**.access(...) 命中的独立规则
        if (normalized.startsWith("/api/health-status/")
                || normalized.equals("/api/health-status")) {
            return true;
        }
        if (normalized.startsWith("/api/model-stats/")
                || normalized.equals("/api/model-stats")) {
            return true;
        }
        if (isPublicAuthEndpoint(normalized)) {
            return true;
        }
        return false;
    }

    private static boolean isPublicAuthEndpoint(final String normalized) {
        // POST /api/auth/jwt/login、POST /api/auth/jwt/validate 为 permitAll
        return normalized.equals("/api/auth/jwt/login")
                || normalized.equals("/api/auth/jwt/validate")
                || normalized.startsWith("/api/auth/jwt/login/")
                || normalized.startsWith("/api/auth/jwt/validate/");
    }

    private static String normalize(final String pathPattern) {
        String p = pathPattern.trim();
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /**
     * 将映射模式转换为样例具体路径，再交给 {@code findRule} 匹配——
     * 与授权管理器对真实请求路径的匹配逻辑完全一致。
     */
    static String toSamplePath(final String pathPattern) {
        String p = normalize(pathPattern);
        // {var} / {*var} / {var:regex} → 占位段
        p = p.replaceAll("\\{[^}]*}", SAMPLE_SEGMENT);
        // * / ** 通配 → 占位段
        p = p.replaceAll("\\*+", SAMPLE_SEGMENT);
        return p;
    }

    private static List<HttpMethod> methodsToCheck(final Set<HttpMethod> methods) {
        if (methods == null || methods.isEmpty()) {
            return DEFAULT_METHODS_TO_CHECK;
        }
        return List.copyOf(methods);
    }

    /**
     * 从全部 {@link RequestMappingHandlerMapping} bean 枚举已映射端点。
     */
    List<MappedEndpoint> enumerateMappedEndpoints() {
        Map<String, RequestMappingHandlerMapping> mappings =
                applicationContext.getBeansOfType(RequestMappingHandlerMapping.class);
        Set<MappedEndpoint> endpoints = new LinkedHashSet<>();
        for (RequestMappingHandlerMapping mapping : mappings.values()) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
                RequestMappingInfo info = entry.getKey();
                Set<String> patterns = patternStrings(info);
                Set<HttpMethod> methods = httpMethods(info);
                for (String pattern : patterns) {
                    endpoints.add(new MappedEndpoint(methods, pattern));
                }
            }
        }
        return List.copyOf(endpoints);
    }

    private static Set<String> patternStrings(final RequestMappingInfo info) {
        Set<String> patterns = new TreeSet<>();
        // WebFlux RequestMappingInfo：PatternsRequestCondition#getPatterns() 为 Set<PathPattern>
        if (info.getPatternsCondition() != null) {
            for (PathPattern pattern : info.getPatternsCondition().getPatterns()) {
                patterns.add(pattern.getPatternString());
            }
        }
        return patterns;
    }

    private static Set<HttpMethod> httpMethods(final RequestMappingInfo info) {
        Set<HttpMethod> methods = new TreeSet<>(Comparator.comparing(HttpMethod::name));
        if (info.getMethodsCondition() != null) {
            info.getMethodsCondition().getMethods().forEach(m -> methods.add(HttpMethod.valueOf(m.name())));
        }
        return methods;
    }

    private void logReport(final RbacEndpointCoverageReport report) {
        log.info("RBAC 端点覆盖自检完成: checked={}, covered={}, uncovered={}, excluded={}",
                report.checkedCount(), report.coveredCount(),
                report.uncoveredCount(), report.excludedCount());
        if (report.uncoveredCount() > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("RBAC 端点覆盖自检发现 ").append(report.uncoveredCount())
                    .append(" 个端点未登记权限规则（当前回退 authenticated，任意登录用户可访问；")
                    .append("fail-open 为默认行为，后续阶段才考虑收紧）:\n");
            for (String line : report.uncoveredEndpoints()) {
                sb.append("  - ").append(line).append('\n');
            }
            log.warn(sb.toString().stripTrailing());
        }
    }

    /**
     * 单个已映射端点：HTTP 方法集合（空 = 任意方法）+ 路径模式。
     */
    public record MappedEndpoint(Set<HttpMethod> methods, String pathPattern) {
        public MappedEndpoint {
            methods = methods == null ? Set.of() : Set.copyOf(methods);
        }
    }
}
