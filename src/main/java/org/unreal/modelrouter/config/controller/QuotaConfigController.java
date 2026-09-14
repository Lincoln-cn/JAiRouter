package org.unreal.modelrouter.config.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.auth.security.quota.QuotaLedgerService;
import org.unreal.modelrouter.auth.security.quota.QuotaProperties;
import org.unreal.modelrouter.auth.security.quota.QuotaWindow;
import org.unreal.modelrouter.common.controller.response.RouterResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配额运行时配置管理控制器.
 *
 * <p>提供配额配置的查询与运行时热改 API，采用与响应缓存
 * （{@link org.unreal.modelrouter.router.controller.ResponseCacheController}）
 * 一致的"内存覆盖 + 快照返回"模式。</p>
 *
 * <p>热改范围（PUT 请求体中非 null 字段生效）：</p>
 * <ul>
 *   <li>{@code enabled} — 是否启用配额账本</li>
 *   <li>{@code failOpen} — 账本异常时是否放行</li>
 *   <li>{@code windows} — 启用的窗口列表</li>
 * </ul>
 *
 * <p>以下字段需重启生效，PUT 时如携带非 null 值将被明确拒绝：</p>
 * <ul>
 *   <li>{@code flushIntervalSeconds} — 快照落库间隔（秒），受 Spring {@code @Scheduled} 限制</li>
 *   <li>{@code distributed.enabled} / {@code distributed.keyPrefix} /
 *       {@code distributed.timeoutMs} / {@code distributed.degradeToLocal}</li>
 *   <li>{@code retention.*}（各级保留期）</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/config/quota")
@Tag(name = "配额配置管理", description = "配额账本的运行时配置查询与热改接口")
public class QuotaConfigController {

    private final QuotaProperties quotaProperties;
    private final QuotaLedgerService ledgerService;

    /**
     * 构造配额配置控制器.
     *
     * @param quotaProperties 配额配置属性
     * @param ledgerService   配额账本服务
     */
    public QuotaConfigController(final QuotaProperties quotaProperties,
                                 final QuotaLedgerService ledgerService) {
        this.quotaProperties = quotaProperties;
        this.ledgerService = ledgerService;
    }

    /**
     * 查询当前生效的配额配置快照.
     *
     * <p>返回运行时实际生效的值（含内存覆盖），并标注哪些字段可热改。</p>
     *
     * @return 配额配置快照
     */
    @GetMapping
    @Operation(summary = "查询配额配置快照",
            description = "返回当前生效的配额配置（enabled/failOpen/windows/flushIntervalSeconds"
                    + "/retention/distributed/backendName），并标注可热改字段")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getConfig() {
        return ResponseEntity.ok(RouterResponse.success(buildSnapshot()));
    }

    /**
     * 运行时更新配额配置（部分更新）.
     *
     * <p>仅修改请求体中非 null 的字段，null 字段保持不变。
     * 分布式配置与保留期为重启级字段，携带非 null 值时明确拒绝并返回说明。
     * 全 null 请求视为非法。</p>
     *
     * @param request 配置更新请求
     * @return 更新成功返回最新配置快照；校验失败返回 400
     */
    @PutMapping
    @Operation(summary = "运行时更新配额配置",
            description = "部分更新 enabled/failOpen/windows，"
                    + "flushIntervalSeconds / distributed.* / retention.* 需重启，携带时被拒绝")
    public ResponseEntity<RouterResponse<Map<String, Object>>> updateConfig(
            @RequestBody final QuotaConfigUpdateRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error("请求体不能为空", "INVALID_REQUEST"));
        }

        final List<String> rejected = collectRejectedFields(request);
        if (!rejected.isEmpty()) {
            return ResponseEntity.badRequest().body(RouterResponse.error(
                    "以下字段需重启生效，不支持热改: " + String.join(", ", rejected)
                            + "；请移除这些字段后重试",
                    "RESTART_REQUIRED"));
        }

        if (request.enabled == null && request.failOpen == null
                && request.windows == null) {
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error("至少需要指定一个可热改的配置参数", "INVALID_REQUEST"));
        }

        if (request.enabled != null) {
            quotaProperties.setEnabled(request.enabled);
            log.info("配额 enabled 已热改为: {}", request.enabled);
        }
        if (request.failOpen != null) {
            quotaProperties.setFailOpen(request.failOpen);
            log.info("配额 failOpen 已热改为: {}", request.failOpen);
        }
        if (request.windows != null) {
            final List<QuotaWindow> parsed = new ArrayList<>();
            for (final String name : request.windows) {
                QuotaWindow.parse(name).ifPresent(parsed::add);
            }
            quotaProperties.setWindows(parsed);
            log.info("配额 windows 已热改为: {}", parsed);
        }
        log.info("配额运行时配置已更新");
        return ResponseEntity.ok(RouterResponse.success(buildSnapshot(), "配额配置已更新"));
    }

    /**
     * 构建配置快照（运行时生效值 + 可热改标注 + 运行时后端信息）.
     *
     * @return 不可变快照 map
     */
    private Map<String, Object> buildSnapshot() {
        final Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("enabled", quotaProperties.isEnabled());
        snap.put("failOpen", quotaProperties.isFailOpen());
        snap.put("windows", windowNames(quotaProperties.enabledWindows()));
        snap.put("flushIntervalSeconds", quotaProperties.getFlushIntervalSeconds());

        final Map<String, String> retentionMap = new LinkedHashMap<>();
        for (final QuotaWindow w : QuotaWindow.values()) {
            retentionMap.put(w.name().toLowerCase(), quotaProperties.retentionSpec(w));
        }
        snap.put("retention", retentionMap);

        final Map<String, Object> distributedMap = new LinkedHashMap<>();
        distributedMap.put("enabled", quotaProperties.distributedEnabled());
        distributedMap.put("keyPrefix", quotaProperties.distributedKeyPrefix());
        distributedMap.put("timeoutMs", quotaProperties.distributedTimeout().toMillis());
        distributedMap.put("degradeToLocal", quotaProperties.distributedDegradeToLocal());
        snap.put("distributed", distributedMap);

        snap.put("backendName", ledgerService.backendName());

        final List<String> hotEditable = List.of("enabled", "failOpen", "windows");
        final List<String> restartRequired = List.of(
                "distributed.enabled", "distributed.keyPrefix",
                "distributed.timeoutMs", "distributed.degradeToLocal", "retention.*",
                "flushIntervalSeconds");
        snap.put("hotEditableFields", hotEditable);
        snap.put("restartRequiredFields", restartRequired);

        return snap;
    }

    /**
     * 收集请求中携带的需重启字段.
     *
     * <p>包括 distributed.*、retention.* 和 flushIntervalSeconds。</p>
     *
     * @param request 配置更新请求
     * @return 需重启字段名列表
     */
    private static List<String> collectRejectedFields(final QuotaConfigUpdateRequest request) {
        final List<String> rejected = new ArrayList<>();
        if (request.distributedEnabled != null) {
            rejected.add("distributed.enabled");
        }
        if (request.distributedKeyPrefix != null) {
            rejected.add("distributed.keyPrefix");
        }
        if (request.distributedTimeoutMs != null) {
            rejected.add("distributed.timeoutMs");
        }
        if (request.distributedDegradeToLocal != null) {
            rejected.add("distributed.degradeToLocal");
        }
        if (request.retention != null && !request.retention.isEmpty()) {
            rejected.add("retention.*");
        }
        if (request.flushIntervalSeconds != null) {
            rejected.add("flushIntervalSeconds");
        }
        return rejected;
    }

    /**
     * 窗口列表转名称列表.
     *
     * @param windows 窗口列表
     * @return 名称列表
     */
    private static List<String> windowNames(final List<QuotaWindow> windows) {
        final List<String> names = new ArrayList<>(windows.size());
        for (final QuotaWindow w : windows) {
            names.add(w.name());
        }
        return names;
    }

    /**
     * 配额运行时配置更新请求体.
     *
     * <p>字段均为包装类型以支持部分更新，null = 不修改。
     * 可热改字段：enabled / failOpen / windows。
     * 需重启字段：flushIntervalSeconds / distributed.* / retention.*，携带时被明确拒绝。</p>
     */
    public static class QuotaConfigUpdateRequest {

        /** 是否启用配额账本（可热改） */
        public Boolean enabled;

        /** 账本异常时是否放行（可热改） */
        public Boolean failOpen;

        /** 启用的窗口列表（可热改，如 ["MINUTE", "HOUR", "DAY"]） */
        public List<String> windows;

        /** 快照落库间隔秒数（需重启，受 Spring @Scheduled 限制） */
        public Long flushIntervalSeconds;

        /** 分布式计数是否启用（需重启） */
        public Boolean distributedEnabled;

        /** 分布式计数 Redis key 前缀（需重启） */
        public String distributedKeyPrefix;

        /** 分布式计数 Redis 命令超时毫秒数（需重启） */
        public Long distributedTimeoutMs;

        /** Redis 不可用时是否降级回本地计数（需重启） */
        public Boolean distributedDegradeToLocal;

        /** 各级窗口保留期（需重启，如 {"minute": "1d", "hour": "2d"}） */
        public Map<String, String> retention;
    }
}
