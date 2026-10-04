package org.unreal.modelrouter.auth.security.config;

import java.util.List;

/**
 * 允许匿名访问的运维端点路径（安全启用时的放行清单）。
 *
 * <p>抽成常量而非内联在 {@code SecurityConfiguration} 的 builder 里，是为了让它能被测试直接校验：
 * K8s 清单里的探针路径（{@code /actuator/health/liveness}、{@code /actuator/health/readiness}）
 * **必须**落在这个清单里。此前只写了精确路径 {@code /actuator/health}，于是无凭据的 kubelet 探针
 * 落进 {@code /actuator/**} 的 ADMIN 规则而被拒，pod 永远不就绪（issue #203，真实集群实测）。
 */
public final class AnonymousEndpointPaths {

    /**
     * 健康与运维端点：匿名可访问。
     *
     * <p>必须同时含 {@code /actuator/health/**} —— Spring Boot 的探测组端点
     * （{@code liveness} / {@code readiness}）都是它的子路径。
     */
    public static final List<String> HEALTH_AND_OPS = List.of(
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus");

    private AnonymousEndpointPaths() {
    }
}
