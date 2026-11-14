package org.unreal.modelrouter.config.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康探针设置控制器（{@code /api/config/health}）。
 *
 * <p>暴露两个开关，语义见各自 properties 的 javadoc：</p>
 * <ul>
 *   <li>{@code jairouter.health.readiness.include-dependencies} —— 就绪探针是否聚合
 *       {@code db}/{@code redis}（默认关闭）</li>
 *   <li>{@code jairouter.security.jwt.redis-health.fail-health} —— Redis 不可用时
 *       {@code jwtRedis} 组件是否判 DOWN（默认开启）</li>
 * </ul>
 *
 * <p><b>两项都能热改</b>：组件每次执行健康检查时都从 properties 读当前值，不缓存。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/config/health")
@Tag(name = "健康探针设置", description = "就绪探针依赖聚合与 JWT Redis 故障判定的开关")
public class HealthConfigController {

    private final HealthProbeProperties healthProbeProperties;
    private final JwtRedisHealthProperties jwtRedisHealthProperties;

    /**
     * 构造健康探针设置控制器.
     *
     * @param healthProbeProperties     就绪探针配置
     * @param jwtRedisHealthProperties  JWT Redis 健康探针配置
     */
    public HealthConfigController(final HealthProbeProperties healthProbeProperties,
                                  final JwtRedisHealthProperties jwtRedisHealthProperties) {
        this.healthProbeProperties = healthProbeProperties;
        this.jwtRedisHealthProperties = jwtRedisHealthProperties;
    }

    /**
     * 查询当前健康探针设置.
     *
     * @return 设置快照
     */
    @GetMapping
    @Operation(summary = "查询健康探针设置",
            description = "返回就绪探针依赖聚合与 JWT Redis 故障判定的当前取值及影响面说明")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getConfig() {
        return ResponseEntity.ok(RouterResponse.success(buildSnapshot()));
    }

    /**
     * 运行时更新健康探针设置（部分更新）.
     *
     * @param request 更新请求，只对非 null 字段生效
     * @return 更新成功返回最新快照；未指定任何字段返回 400
     */
    @PutMapping
    @Operation(summary = "运行时更新健康探针设置",
            description = "部分更新 readinessIncludeDependencies / jwtRedisFailHealth；两项均可热改")
    public ResponseEntity<RouterResponse<Map<String, Object>>> updateConfig(
            @RequestBody final HealthConfigUpdateRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error("请求体不能为空", ErrorCode.INVALID_REQUEST.code()));
        }
        if (request.readinessIncludeDependencies == null && request.jwtRedisFailHealth == null) {
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error("至少需要指定一个可热改的配置参数", ErrorCode.INVALID_REQUEST.code()));
        }
        if (request.readinessIncludeDependencies != null) {
            healthProbeProperties.getReadiness()
                    .setIncludeDependencies(request.readinessIncludeDependencies);
            log.info("就绪探针依赖聚合已设置为 {}", request.readinessIncludeDependencies);
        }
        if (request.jwtRedisFailHealth != null) {
            jwtRedisHealthProperties.setFailHealth(request.jwtRedisFailHealth);
            log.info("JWT Redis 故障判定已设置为 {}", request.jwtRedisFailHealth);
        }
        return ResponseEntity.ok(RouterResponse.success(buildSnapshot()));
    }

    private Map<String, Object> buildSnapshot() {
        Map<String, Object> readiness = new LinkedHashMap<>();
        readiness.put("includeDependencies",
                healthProbeProperties.getReadiness().isIncludeDependencies());
        readiness.put("impact",
                "开启后 db/redis 任一不可用会让副本退出就绪集合；依赖故障期间滚动发布会卡住");

        Map<String, Object> jwtRedis = new LinkedHashMap<>();
        jwtRedis.put("failHealth", jwtRedisHealthProperties.isFailHealth());
        jwtRedis.put("impact", jwtRedisHealthProperties.isFailHealth()
                ? "Redis 不可用时整体 /actuator/health 返回 503，按该端点的编排探针会摘流或重启副本"
                : "Redis 不可用时仍返回 UP，仅在 details 里标注影响面；本副本继续接收流量");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("readiness", readiness);
        body.put("jwtRedis", jwtRedis);
        body.put("hotEditableFields", List.of("readinessIncludeDependencies", "jwtRedisFailHealth"));
        body.put("restartRequiredFields", List.of());
        return body;
    }

    /**
     * 健康探针设置更新请求.
     *
     * @param readinessIncludeDependencies 就绪探针是否聚合 db/redis
     * @param jwtRedisFailHealth           Redis 不可用时 jwtRedis 是否判 DOWN
     */
    public record HealthConfigUpdateRequest(Boolean readinessIncludeDependencies,
                                            Boolean jwtRedisFailHealth) {
    }
}
