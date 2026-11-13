package org.unreal.modelrouter.config.cluster;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.cluster.ClusterEventBus;
import org.unreal.modelrouter.common.cluster.MultiReplicaSharedStateGate;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 集群设置管理控制器（{@code /api/config/cluster}，#1132 / #1136 / #1137）。
 *
 * <p>与限流配置（{@link org.unreal.modelrouter.config.controller.RatelimitConfigController}）
 * 保持同一形态：查询返回「运行时生效值 + 可热改字段 + 需重启字段」的快照，更新按
 * 「请求体中非 null 的字段生效」做部分更新。</p>
 *
 * <p><b>为什么这些开关能热改</b>：同步服务是恒装配的，调度 tick 每次读
 * {@link ClusterConfigSyncProperties#isEnabled()} 与
 * {@link ClusterConfigSyncProperties#effectivePollIntervalMs()} 决定是否执行、多久执行一次；
 * 事件订阅也始终建立，是否处理由开关在回调里判断。因此改属性立即改变行为，不需要重启。</p>
 *
 * <p><b>单机部署的表现</b>：{@code mode=standalone}，配置同步默认仍然开启（每 5 秒查一次
 * 版本号，开销可忽略），但事件总线是空实现，跨副本加速不会生效。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.7
 */
@Slf4j
@RestController
@RequestMapping("/api/config/cluster")
@Tag(name = "集群设置管理", description = "跨副本配置同步、成员发现与上游健康共享的开关与阈值")
public class ClusterConfigController {

    private final ClusterConfigSyncProperties configSyncProperties;
    private final ClusterConfigSyncService configSyncService;
    private final ClusterEventBus clusterEventBus;
    private final ClusterMemberRegistry memberRegistry;
    private final HealthSharingProperties healthSharingProperties;
    private final ClusterHealthShareService healthShareService;
    private final int replicas;

    /**
     * 构造集群设置控制器.
     *
     * @param configSyncProperties   配置同步配置
     * @param configSyncService      配置同步服务（读取已应用版本号）
     * @param clusterEventBus        集群事件总线（判断是否真跨副本）
     * @param memberRegistry         成员注册表（统一形态判定来源）
     * @param healthSharingProperties 上游健康共享配置
     * @param healthShareService     上游健康共享服务（读取运行时快照）
     * @param replicas               显式副本数（{@code <=0} 表示未知）
     */
    public ClusterConfigController(final ClusterConfigSyncProperties configSyncProperties,
                                   final ClusterConfigSyncService configSyncService,
                                   final ClusterEventBus clusterEventBus,
                                   final ClusterMemberRegistry memberRegistry,
                                   final HealthSharingProperties healthSharingProperties,
                                   final ClusterHealthShareService healthShareService,
                                   @Value("${jairouter.cluster.replicas:0}") final int replicas) {
        this.configSyncProperties = configSyncProperties;
        this.configSyncService = configSyncService;
        this.clusterEventBus = clusterEventBus;
        this.memberRegistry = memberRegistry;
        this.healthSharingProperties = healthSharingProperties;
        this.healthShareService = healthShareService;
        this.replicas = replicas;
    }

    /**
     * 查询当前集群设置快照.
     *
     * @return 集群设置快照
     */
    @GetMapping
    @Operation(summary = "查询集群设置快照",
            description = "返回部署形态（单机/集群）、跨副本配置同步的当前配置与已应用版本号")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getConfig() {
        return ResponseEntity.ok(RouterResponse.success(buildSnapshot()));
    }

    /**
     * 运行时更新集群设置（部分更新）.
     *
     * <p>三个字段均可热改：{@code enabled} 与 {@code pollIntervalMs} 由调度 tick 每轮读取，
     * {@code eventBusEnabled} 由事件回调每轮读取。</p>
     *
     * @param request 集群设置更新请求
     * @return 更新成功返回最新快照；校验失败返回 400
     */
    @PutMapping
    @Operation(summary = "运行时更新集群设置",
            description = "部分更新 configSyncEnabled / configSyncPollIntervalMs / "
                    + "configSyncEventBusEnabled；三个字段均可热改")
    public ResponseEntity<RouterResponse<Map<String, Object>>> updateConfig(
            @RequestBody final ClusterConfigUpdateRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error("请求体不能为空", ErrorCode.INVALID_REQUEST.code()));
        }
        if (request.configSyncEnabled == null
                && request.configSyncPollIntervalMs == null
                && request.configSyncEventBusEnabled == null
                && request.healthSharingEnabled == null
                && request.healthSharingMode == null
                && request.healthSharingToleranceCount == null
                && request.healthSharingGraceSeconds == null) {
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error("至少需要指定一个可热改的配置参数", ErrorCode.INVALID_REQUEST.code()));
        }
        if (request.configSyncPollIntervalMs != null && request.configSyncPollIntervalMs < 1_000) {
            return ResponseEntity.badRequest().body(RouterResponse.error(
                    "configSyncPollIntervalMs 不能小于 1000: " + request.configSyncPollIntervalMs,
                    ErrorCode.INVALID_REQUEST.code()));
        }
        if (request.healthSharingMode != null
                && !HealthSharingResolver.MODE_LOCAL.equals(request.healthSharingMode)
                && !HealthSharingResolver.MODE_SHARED.equals(request.healthSharingMode)
                && !HealthSharingResolver.MODE_QUORUM.equals(request.healthSharingMode)) {
            return ResponseEntity.badRequest().body(RouterResponse.error(
                    "healthSharingMode 只能是 local / shared / quorum: " + request.healthSharingMode,
                    ErrorCode.INVALID_REQUEST.code()));
        }
        if (request.healthSharingToleranceCount != null && request.healthSharingToleranceCount < 1) {
            return ResponseEntity.badRequest().body(RouterResponse.error(
                    "healthSharingToleranceCount 不能小于 1: " + request.healthSharingToleranceCount,
                    ErrorCode.INVALID_REQUEST.code()));
        }
        if (request.healthSharingGraceSeconds != null && request.healthSharingGraceSeconds < 0) {
            return ResponseEntity.badRequest().body(RouterResponse.error(
                    "healthSharingGraceSeconds 不能为负数: " + request.healthSharingGraceSeconds,
                    ErrorCode.INVALID_REQUEST.code()));
        }

        if (request.configSyncEnabled != null) {
            configSyncProperties.setEnabled(request.configSyncEnabled);
            log.info("跨副本配置同步开关已热改为: {}", request.configSyncEnabled);
        }
        if (request.configSyncPollIntervalMs != null) {
            configSyncProperties.setPollIntervalMs(request.configSyncPollIntervalMs);
            log.info("跨副本配置同步轮询间隔已热改为: {} ms", request.configSyncPollIntervalMs);
        }
        if (request.configSyncEventBusEnabled != null) {
            configSyncProperties.setEventBusEnabled(request.configSyncEventBusEnabled);
            log.info("跨副本配置同步事件加速已热改为: {}", request.configSyncEventBusEnabled);
        }
        if (request.healthSharingEnabled != null) {
            healthSharingProperties.setEnabled(request.healthSharingEnabled);
            log.info("上游健康共享开关已热改为: {}", request.healthSharingEnabled);
        }
        if (request.healthSharingMode != null) {
            healthSharingProperties.setMode(request.healthSharingMode);
            log.info("上游健康共享判定语义已热改为: {}", request.healthSharingMode);
        }
        if (request.healthSharingToleranceCount != null) {
            healthSharingProperties.setToleranceCount(request.healthSharingToleranceCount);
            log.info("上游健康共享容忍副本数已热改为: {}", request.healthSharingToleranceCount);
        }
        if (request.healthSharingGraceSeconds != null) {
            healthSharingProperties.setGraceSeconds(request.healthSharingGraceSeconds);
            log.info("上游健康共享有效期已热改为: {} 秒", request.healthSharingGraceSeconds);
        }
        return ResponseEntity.ok(RouterResponse.success(buildSnapshot(), "集群设置已更新"));
    }

    /**
     * 构建集群设置快照.
     *
     * @return 快照 map
     */
    private Map<String, Object> buildSnapshot() {
        final Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("mode", memberRegistry.isClusterMode() ? "cluster" : "standalone");
        snap.put("modeSource", memberRegistry.modeSource());
        snap.put("replicas", replicas);
        snap.put("clusterEventsEnabled", clusterEventBus.isClusterWide());

        final Map<String, Object> sync = new LinkedHashMap<>();
        sync.put("enabled", configSyncProperties.isEnabled());
        sync.put("pollIntervalMs", configSyncProperties.effectivePollIntervalMs());
        sync.put("eventBusEnabled", configSyncProperties.isEventBusEnabled());
        sync.put("appliedConfigVersion", configSyncService.getAppliedConfigVersion());
        sync.put("appliedApiKeyVersion", configSyncService.getAppliedApiKeyVersion());
        snap.put("configSync", sync);

        final Map<String, Object> healthSharing = new LinkedHashMap<>();
        healthSharing.put("enabled", healthSharingProperties.isEnabled());
        healthSharing.put("mode", healthSharingProperties.getMode());
        healthSharing.put("toleranceCount", healthSharingProperties.getToleranceCount());
        healthSharing.put("graceSeconds", healthSharingProperties.getGraceSeconds());
        healthSharing.putAll(healthShareService.stats());
        snap.put("healthSharing", healthSharing);

        snap.put("hotEditableFields", List.of(
                "configSync.enabled", "configSync.pollIntervalMs", "configSync.eventBusEnabled",
                "healthSharing.enabled", "healthSharing.mode",
                "healthSharing.toleranceCount", "healthSharing.graceSeconds"));
        snap.put("restartRequiredFields", List.of());

        return snap;
    }

    /**
     * 集群设置更新请求体.
     *
     * <p>字段均为包装类型以支持部分更新，null = 不修改。三个字段均可热改。</p>
     */
    public static class ClusterConfigUpdateRequest {

        /** 跨副本配置同步开关（可热改） */
        public Boolean configSyncEnabled;

        /** 跨副本配置同步轮询间隔毫秒数（可热改，最小 1000） */
        public Long configSyncPollIntervalMs;

        /** 事件加速开关（可热改） */
        public Boolean configSyncEventBusEnabled;

        /** 上游健康共享开关（可热改） */
        public Boolean healthSharingEnabled;

        /** 上游健康共享判定语义：local / shared / quorum（可热改） */
        public String healthSharingMode;

        /** quorum 语义下认为健康所需的最小副本数（可热改，最小 1） */
        public Integer healthSharingToleranceCount;

        /** 共享上报有效期秒数（可热改，最小 0） */
        public Integer healthSharingGraceSeconds;
    }
}
