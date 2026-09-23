package org.unreal.modelrouter.monitor.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.auth.security.quota.QuotaCounterMetrics;
import org.unreal.modelrouter.auth.security.quota.QuotaDimension;
import org.unreal.modelrouter.auth.security.quota.QuotaLedgerService;
import org.unreal.modelrouter.auth.security.quota.QuotaProperties;
import org.unreal.modelrouter.auth.security.quota.QuotaUsage;
import org.unreal.modelrouter.auth.security.quota.QuotaWindow;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 配额观测面控制器.
 *
 * <p>提供只读的配额运行状态与用量查询接口，绝不触发写入或结算。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/monitoring/quota")
@Tag(name = "配额监控", description = "配额账本运行状态与用量查询（只读）")
public class QuotaMonitoringController {

    private final QuotaLedgerService ledgerService;
    private final QuotaProperties quotaProperties;
    private final QuotaCounterMetrics metrics;

    /**
     * 构造配额监控控制器.
     *
     * @param ledgerService   配额账本服务
     * @param quotaProperties 配额配置
     * @param metrics         配额计数指标
     */
    public QuotaMonitoringController(final QuotaLedgerService ledgerService,
                                     final QuotaProperties quotaProperties,
                                     final QuotaCounterMetrics metrics) {
        this.ledgerService = ledgerService;
        this.quotaProperties = quotaProperties;
        this.metrics = metrics;
    }

    /**
     * 查询配额运行状态.
     *
     * <p>返回后端类型、是否降级、降级原因、窗口启用情况、fail-open 模式等运行时信息。
     * Redis 模式下尝试连通性探针，失败时不抛 5xx，返回 status=degraded + 说明。</p>
     *
     * @return 配额运行状态
     */
    @GetMapping("/status")
    @Operation(summary = "查询配额运行状态",
            description = "返回 backendName/degraded/degradedReason/enabled/failOpen/windows"
                    + " 与 Redis 连通性信息（只读，不触发写入）")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getStatus() {
        final Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", ledgerService.isEnabled());
        status.put("backendName", ledgerService.backendName());
        status.put("degraded", ledgerService.isDegraded());
        status.put("degradedReason", ledgerService.degradedReason());
        status.put("failOpen", quotaProperties.isFailOpen());

        final List<String> windowNames = new ArrayList<>();
        for (final QuotaWindow w : quotaProperties.enabledWindows()) {
            windowNames.add(w.name());
        }
        status.put("windows", windowNames);

        final Map<String, Object> distributed = new LinkedHashMap<>();
        distributed.put("enabled", quotaProperties.distributedEnabled());
        distributed.put("keyPrefix", quotaProperties.distributedKeyPrefix());
        distributed.put("timeoutMs", quotaProperties.distributedTimeout().toMillis());
        distributed.put("degradeToLocal", quotaProperties.distributedDegradeToLocal());
        status.put("distributed", distributed);

        if (quotaProperties.distributedEnabled()) {
            final Map<String, Object> redisProbe = probeRedis();
            status.put("redisProbe", redisProbe);

            final Map<String, Object> counterMetrics = new LinkedHashMap<>();
            counterMetrics.put("degradationCount",
                    metrics.degradationCount(QuotaLedgerService.REASON_REDIS_UNAVAILABLE)
                            + metrics.degradationCount(QuotaLedgerService.REASON_REDIS_TIMEOUT)
                            + metrics.degradationCount(QuotaLedgerService.REASON_REDIS_NOT_CONFIGURED));
            status.put("counterMetrics", counterMetrics);
        }

        return ResponseEntity.ok(RouterResponse.success(status));
    }

    /**
     * 查询配额用量.
     *
     * <p>支持按维度筛选（tenantId / apiKeyId / userId / serviceType / model）与窗口类型。
     * 缺省维度使用空串哨兵语义（{@code QuotaDimension.SENTINEL}）。
     * 无参数时返回空维度 + 全部已启用窗口的聚合（按 API Key 聚合视图，即空维度哨兵值）。</p>
     *
     * <p>本端点严格只读，不会触发任何写入或结算操作。</p>
     *
     * <p><b>线程语义（issue #105）</b>：分布式模式下 {@code ledgerService.usage(...)} 会同步阻塞读取
     * Redis / 数据库，而 WebFlux 的同步签名端点运行在事件循环线程上（本仓库未装配
     * {@code BlockingExecutionConfigurer}），因此这里改为返回 {@code Mono} 并把读数显式切到
     * {@link Schedulers#boundedElastic()}；响应体与状态码与同步实现完全一致。</p>
     *
     * @param tenantId    租户 ID（可选，缺省为空串）
     * @param apiKeyId    API Key ID（可选，缺省为空串）
     * @param userId      用户 ID（可选，缺省为空串）
     * @param serviceType 服务类型（可选，缺省为空串）
     * @param model       模型名称（可选，缺省为空串）
     * @param window      窗口类型（可选，如 MINUTE / HOUR / DAY / MONTH；缺省返回所有已启用窗口）
     * @return 用量列表
     */
    @GetMapping("/usage")
    @Operation(summary = "查询配额用量",
            description = "按维度与窗口筛选配额用量（只读），"
                    + "返回 dimensions/window/windowStart/requestCount/tokenCount")
    public Mono<ResponseEntity<RouterResponse<List<Map<String, Object>>>>> getUsage(
            @RequestParam(required = false) final String tenantId,
            @RequestParam(required = false) final String apiKeyId,
            @RequestParam(required = false) final String userId,
            @RequestParam(required = false) final String serviceType,
            @RequestParam(required = false) final String model,
            @RequestParam(required = false) final String window) {
        return Mono.fromCallable(() ->
                        doGetUsage(tenantId, apiKeyId, userId, serviceType, model, window))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 用量查询的实际实现（同步读取账本，必须运行在可阻塞线程上）.
     *
     * @param tenantId    租户 ID
     * @param apiKeyId    API Key ID
     * @param userId      用户 ID
     * @param serviceType 服务类型
     * @param model       模型名称
     * @param window      窗口类型
     * @return 用量响应
     */
    private ResponseEntity<RouterResponse<List<Map<String, Object>>>> doGetUsage(
            final String tenantId,
            final String apiKeyId,
            final String userId,
            final String serviceType,
            final String model,
            final String window) {
        if (!ledgerService.isEnabled()) {
            return ResponseEntity.ok(RouterResponse.success(List.of(), "配额账本未启用"));
        }

        final QuotaDimension dimension = new QuotaDimension(
                tenantId, apiKeyId, userId, serviceType, model);

        final List<QuotaWindow> targetWindows;
        if (window != null && !window.isBlank()) {
            final Optional<QuotaWindow> parsed = QuotaWindow.parse(window);
            if (parsed.isEmpty()) {
                return ResponseEntity.badRequest().body(RouterResponse.error(
                        "无效的窗口类型: " + window
                                + "（有效值: MINUTE, HOUR, DAY, MONTH）",
                        "INVALID_REQUEST"));
            }
            targetWindows = List.of(parsed.get());
        } else {
            targetWindows = quotaProperties.enabledWindows();
        }

        final List<Map<String, Object>> results = new ArrayList<>();
        for (final QuotaWindow w : targetWindows) {
            final Optional<QuotaUsage> usage = ledgerService.usage(dimension, w);
            usage.ifPresent(u -> results.add(usageToMap(u)));
        }

        return ResponseEntity.ok(RouterResponse.success(results));
    }

    /**
     * Redis 连通性探针（不抛异常）.
     *
     * @return 探针结果 map
     */
    private Map<String, Object> probeRedis() {
        final Map<String, Object> probe = new LinkedHashMap<>();
        try {
            final boolean degraded = ledgerService.isDegraded();
            probe.put("reachable", !degraded);
            if (degraded) {
                probe.put("reason", ledgerService.degradedReason());
                probe.put("status", "degraded");
            } else {
                probe.put("status", "healthy");
            }
        } catch (Exception e) {
            log.debug("Redis 连通性探针异常: {}", e.toString());
            probe.put("reachable", false);
            probe.put("status", "error");
            probe.put("reason", e.getMessage());
        }
        return probe;
    }

    /**
     * 用量记录转 Map.
     *
     * @param usage 用量
     * @return map
     */
    private static Map<String, Object> usageToMap(final QuotaUsage usage) {
        final Map<String, Object> map = new LinkedHashMap<>();
        final Map<String, String> dimensions = new LinkedHashMap<>();
        dimensions.put("tenantId", usage.dimension().tenantId());
        dimensions.put("apiKeyId", usage.dimension().apiKeyId());
        dimensions.put("userId", usage.dimension().userId());
        dimensions.put("serviceType", usage.dimension().serviceType());
        dimensions.put("model", usage.dimension().model());
        map.put("dimensions", dimensions);
        map.put("window", usage.window().name());
        map.put("windowStart", usage.windowStart().toString());
        map.put("requestCount", usage.requestCount());
        map.put("tokenCount", usage.tokenCount());
        // 限额对照：仅当维度只关心单个 API Key 时，由前端/调用方结合 /auth/api-keys/quota/overview 填充；
        // 此处预留字段，保证响应契约稳定。
        map.put("dailyRequestLimit", null);
        map.put("dailyTokenLimit", null);
        map.put("rateLimitPerMinute", null);
        return map;
    }
}
