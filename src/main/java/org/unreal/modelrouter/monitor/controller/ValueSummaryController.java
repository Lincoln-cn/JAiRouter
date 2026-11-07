package org.unreal.modelrouter.monitor.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.controller.response.RouterResponse;

/**
 * 网关价值概览：把「这个网关替我做了什么」翻译成四个数字。
 *
 * <p>只报次数与命中率，<strong>不折算金额</strong> —— 单价没有配置，
 * 凭单价算出来的金额是编造数据。等真的有人问「省了多少钱」再引入单价配置。</p>
 *
 * <p>指标全部来自 Micrometer，未启用对应能力时计数为 0（不是报错）。</p>
 */
@RestController
@RequestMapping("/api/monitoring")
public class ValueSummaryController {

    /** 语义缓存命中 / 未命中的指标名，与 {@code SemanticCacheService} 保持一致. */
    private static final String SEMANTIC_HITS = "jairouter.semantic.cache.hits";

    /** 语义缓存未命中. */
    private static final String SEMANTIC_MISSES = "jairouter.semantic.cache.misses";

    /** 流式护栏判定，标签 action=REDACT/ABORT. */
    private static final String GUARD_DECISIONS = "jairouter.guard.decisions";

    /** 出站请求耗时，标签 instance=name@host:port. */
    private static final String BACKEND_CALL = "jairouter.backend.call.duration";

    private final MeterRegistry meterRegistry;

    /**
     * 构造器.
     *
     * @param meterRegistry 指标注册表，允许缺失（测试或裁剪部署）
     */
    @Autowired(required = false)
    public ValueSummaryController(final MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 价值概览.
     *
     * @return 缓存命中率、节省的上游调用次数、护栏拦截次数、各实例的 P95 延迟
     */
    @GetMapping("/value-summary")
    public ResponseEntity<RouterResponse<Map<String, Object>>> valueSummary() {
        final Map<String, Object> summary = new LinkedHashMap<>();
        final double hits = sumCounter(SEMANTIC_HITS);
        final double misses = sumCounter(SEMANTIC_MISSES);
        final double total = hits + misses;
        summary.put("semanticCacheHits", (long) hits);
        summary.put("semanticCacheMisses", (long) misses);
        summary.put("semanticCacheHitRate", total > 0 ? hits / total : 0.0);
        summary.put("savedUpstreamCalls", (long) hits);
        summary.put("guardRedactions", sumCounterWithTag(GUARD_DECISIONS, "action", "REDACT"));
        summary.put("guardAborts", sumCounterWithTag(GUARD_DECISIONS, "action", "ABORT"));
        summary.put("backendP95", p95ByInstance());
        return ResponseEntity.ok(RouterResponse.success(summary, "请求成功"));
    }

    private double sumCounter(final String name) {
        if (meterRegistry == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (final Counter counter : meterRegistry.find(name).counters()) {
            sum += counter.count();
        }
        return sum;
    }

    private double sumCounterWithTag(final String name, final String key, final String value) {
        if (meterRegistry == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (final Counter counter : meterRegistry.find(name).tag(key, value).counters()) {
            sum += counter.count();
        }
        return sum;
    }

    /**
     * 每个实例的 P95 延迟（毫秒）.
     *
     * <p>用 Timer 的 max 近似 —— 精确 P95 需要自建直方图，这里只做「哪个实例慢」的粗判，
     * 因此字段名用 {@code p95Ms} 会让读者误以为是精确分位值，所以改叫 {@code maxMs}。</p>
     *
     * @return 实例标签 → 最大耗时（毫秒）
     */
    private Map<String, Double> p95ByInstance() {
        final Map<String, Double> result = new LinkedHashMap<>();
        if (meterRegistry == null) {
            return result;
        }
        for (final Timer timer : meterRegistry.find(BACKEND_CALL).timers()) {
            final String instance = timer.getId().getTag("instance");
            if (instance == null) {
                continue;
            }
            final double maxMs = timer.max(TimeUnit.MILLISECONDS);
            result.merge(instance, maxMs, Math::max);
        }
        return result;
    }
}
