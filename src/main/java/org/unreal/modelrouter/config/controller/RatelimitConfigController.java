package org.unreal.modelrouter.config.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.router.ratelimit.apikey.ApiKeyMinuteRateLimitProperties;
import org.unreal.modelrouter.router.ratelimit.apikey.ApiKeyMinuteRateLimitService;
import org.unreal.modelrouter.router.ratelimit.enduser.EndUserRateLimitCounter;
import org.unreal.modelrouter.router.ratelimit.enduser.EndUserRateLimitProperties;
import org.unreal.modelrouter.router.ratelimit.enduser.EndUserRateLimitProperties.MissingIdentity;
import org.unreal.modelrouter.router.ratelimit.enduser.RedisEndUserRateLimitCounter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 限流运行时配置管理控制器（L1 = API Key 级，L2 = 终端用户级）.
 *
 * <p>与配额配置（{@link QuotaConfigController}）保持同一形态：查询返回「运行时生效值 +
 * 可热改字段 + 需重启字段」的快照，更新按「请求体中非 null 的字段生效」做部分更新。</p>
 *
 * <p><b>为什么这两个开关能热改</b>：两套限流装配（{@code EndUserRateLimitConfiguration} /
 * {@code ApiKeyMinuteRateLimitConfiguration}）都是恒注册的，启用与否由
 * {@code properties.isEnabled()} 在每次判定前读取，因此改属性立即改变热路径行为，
 * 不需要重启、也不需要重新创建 Bean。</p>
 *
 * <p><b>哪些字段需要重启</b>：</p>
 * <ul>
 *   <li>{@code distributed.enabled} / {@code distributed.timeoutMs} —— 装配期由
 *       {@code @Value} 注入并据此选择计数器实现（Redis 或本地），运行期改属性不会重选后端；</li>
 *   <li>{@code apiKey.localCacheSize} / {@code endUser.localCacheSize} —— 本地计数器的
 *       Caffeine 容量在构造时固定；</li>
 *   <li>{@code endUser.fingerprintLength} —— 标识摘要长度参与桶键计算与日志展示口径，
 *       热改会让同一用户在窗口内落到不同桶。</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
@Slf4j
@RestController
@RequestMapping("/api/config/ratelimit")
@Tag(name = "限流配置管理", description = "API Key 级与终端用户级限流的运行时配置查询与热改接口")
public class RatelimitConfigController {

    private final ApiKeyMinuteRateLimitProperties apiKeyProperties;
    private final EndUserRateLimitProperties endUserProperties;
    private final ObjectProvider<ApiKeyMinuteRateLimitService> apiKeyRateLimitServiceProvider;
    private final ObjectProvider<EndUserRateLimitCounter> endUserCounterProvider;
    private final boolean distributedEnabled;
    private final long distributedTimeoutMs;

    /**
     * 构造限流配置控制器.
     *
     * @param apiKeyProperties             API Key 级限流配置
     * @param endUserProperties            终端用户级限流配置
     * @param apiKeyRateLimitServiceProvider API Key 级限流门面（展示后端名用，可选）
     * @param endUserCounterProvider       终端用户级计数器（展示后端名用，可选）
     * @param distributedEnabled           分布式计数开关（装配期读取，仅供展示）
     * @param distributedTimeoutMs         分布式计数超时（装配期读取，仅供展示）
     */
    public RatelimitConfigController(
            final ApiKeyMinuteRateLimitProperties apiKeyProperties,
            final EndUserRateLimitProperties endUserProperties,
            final ObjectProvider<ApiKeyMinuteRateLimitService> apiKeyRateLimitServiceProvider,
            final ObjectProvider<EndUserRateLimitCounter> endUserCounterProvider,
            @Value("${jairouter.ratelimit.distributed.enabled:false}") final boolean distributedEnabled,
            @Value("${jairouter.ratelimit.distributed.timeout-ms:50}") final long distributedTimeoutMs) {
        this.apiKeyProperties = apiKeyProperties;
        this.endUserProperties = endUserProperties;
        this.apiKeyRateLimitServiceProvider = apiKeyRateLimitServiceProvider;
        this.endUserCounterProvider = endUserCounterProvider;
        this.distributedEnabled = distributedEnabled;
        this.distributedTimeoutMs = distributedTimeoutMs;
    }

    /**
     * 查询当前生效的限流配置快照.
     *
     * @return 限流配置快照
     */
    @GetMapping
    @Operation(summary = "查询限流配置快照",
            description = "返回 API Key 级与终端用户级限流的当前配置、计数后端与可热改/需重启字段清单")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getConfig() {
        return ResponseEntity.ok(RouterResponse.success(buildSnapshot()));
    }

    /**
     * 运行时更新限流配置（部分更新）.
     *
     * <p>仅修改请求体中非 null 的字段。携带需重启字段时明确拒绝并返回说明；
     * 全 null 请求视为非法。</p>
     *
     * @param request 配置更新请求
     * @return 更新成功返回最新快照；校验失败返回 400
     */
    @PutMapping
    @Operation(summary = "运行时更新限流配置",
            description = "部分更新 apiKeyEnabled / endUserEnabled / endUserRequestsPerMinute / "
                    + "endUserHeaderEnabled / endUserMissingIdentity；distributed.* 与两个 localCacheSize、"
                    + "fingerprintLength 需重启，携带时被拒绝")
    public ResponseEntity<RouterResponse<Map<String, Object>>> updateConfig(
            @RequestBody final RatelimitConfigUpdateRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error("请求体不能为空", ErrorCode.INVALID_REQUEST.code()));
        }

        final List<String> rejected = collectRejectedFields(request);
        if (!rejected.isEmpty()) {
            return ResponseEntity.badRequest().body(RouterResponse.error(
                    "以下字段需重启生效，不支持热改: " + String.join(", ", rejected)
                            + "；请移除这些字段后重试",
                    ErrorCode.RESTART_REQUIRED.code()));
        }

        if (request.apiKeyEnabled == null && request.endUserEnabled == null
                && request.endUserRequestsPerMinute == null
                && request.endUserHeaderEnabled == null
                && request.endUserMissingIdentity == null) {
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error("至少需要指定一个可热改的配置参数", ErrorCode.INVALID_REQUEST.code()));
        }

        MissingIdentity parsedIdentity = null;
        if (request.endUserMissingIdentity != null) {
            parsedIdentity = parseMissingIdentity(request.endUserMissingIdentity);
            if (parsedIdentity == null) {
                return ResponseEntity.badRequest().body(RouterResponse.error(
                        "endUserMissingIdentity 取值非法，只支持 SKIP 或 REJECT: "
                                + request.endUserMissingIdentity,
                        ErrorCode.INVALID_REQUEST.code()));
            }
        }
        if (request.endUserRequestsPerMinute != null && request.endUserRequestsPerMinute < 1) {
            return ResponseEntity.badRequest().body(RouterResponse.error(
                    "endUserRequestsPerMinute 必须大于 0: " + request.endUserRequestsPerMinute,
                    ErrorCode.INVALID_REQUEST.code()));
        }

        if (request.apiKeyEnabled != null) {
            apiKeyProperties.setEnabled(request.apiKeyEnabled);
            log.info("API Key 级限流开关已热改为: {}", request.apiKeyEnabled);
        }
        if (request.endUserEnabled != null) {
            endUserProperties.setEnabled(request.endUserEnabled);
            log.info("终端用户级限流开关已热改为: {}", request.endUserEnabled);
        }
        if (request.endUserRequestsPerMinute != null) {
            endUserProperties.setRequestsPerMinute(request.endUserRequestsPerMinute);
            log.info("终端用户级限流阈值已热改为: {}/min", request.endUserRequestsPerMinute);
        }
        if (request.endUserHeaderEnabled != null) {
            endUserProperties.setHeaderEnabled(request.endUserHeaderEnabled);
            log.info("终端用户标识请求头开关已热改为: {}", request.endUserHeaderEnabled);
        }
        if (parsedIdentity != null) {
            endUserProperties.setMissingIdentity(parsedIdentity);
            log.info("终端用户标识缺失时的处理方式已热改为: {}", parsedIdentity);
        }
        return ResponseEntity.ok(RouterResponse.success(buildSnapshot(), "限流配置已更新"));
    }

    /**
     * 构建配置快照（运行时生效值 + 可热改标注 + 运行时后端信息）.
     *
     * @return 快照 map
     */
    private Map<String, Object> buildSnapshot() {
        final Map<String, Object> snap = new LinkedHashMap<>();

        final Map<String, Object> apiKeyMap = new LinkedHashMap<>();
        apiKeyMap.put("enabled", apiKeyProperties.isEnabled());
        apiKeyMap.put("localCacheSize", apiKeyProperties.effectiveLocalCacheSize());
        snap.put("apiKey", apiKeyMap);

        final Map<String, Object> endUserMap = new LinkedHashMap<>();
        endUserMap.put("enabled", endUserProperties.isEnabled());
        endUserMap.put("requestsPerMinute", endUserProperties.effectiveRequestsPerMinute());
        endUserMap.put("headerEnabled", endUserProperties.isHeaderEnabled());
        endUserMap.put("missingIdentity", endUserProperties.getMissingIdentity().name());
        endUserMap.put("localCacheSize", endUserProperties.effectiveLocalCacheSize());
        endUserMap.put("fingerprintLength", endUserProperties.effectiveFingerprintLength());
        snap.put("endUser", endUserMap);

        final Map<String, Object> distributedMap = new LinkedHashMap<>();
        distributedMap.put("enabled", distributedEnabled);
        distributedMap.put("timeoutMs", distributedTimeoutMs);
        distributedMap.put("apiKeyBackend", apiKeyBackendName());
        distributedMap.put("endUserBackend", endUserBackendName());
        snap.put("distributed", distributedMap);

        snap.put("hotEditableFields", List.of(
                "apiKey.enabled", "endUser.enabled", "endUser.requestsPerMinute",
                "endUser.headerEnabled", "endUser.missingIdentity"));
        snap.put("restartRequiredFields", List.of(
                "distributed.enabled", "distributed.timeoutMs",
                "apiKey.localCacheSize", "endUser.localCacheSize", "endUser.fingerprintLength"));

        return snap;
    }

    /**
     * 收集请求中携带的需重启字段.
     *
     * @param request 配置更新请求
     * @return 需重启字段名列表
     */
    private static List<String> collectRejectedFields(final RatelimitConfigUpdateRequest request) {
        final List<String> rejected = new ArrayList<>();
        if (request.distributedEnabled != null) {
            rejected.add("distributed.enabled");
        }
        if (request.distributedTimeoutMs != null) {
            rejected.add("distributed.timeoutMs");
        }
        if (request.apiKeyLocalCacheSize != null) {
            rejected.add("apiKey.localCacheSize");
        }
        if (request.endUserLocalCacheSize != null) {
            rejected.add("endUser.localCacheSize");
        }
        if (request.endUserFingerprintLength != null) {
            rejected.add("endUser.fingerprintLength");
        }
        return rejected;
    }

    /**
     * API Key 级计数后端名称.
     *
     * @return {@code redis} / {@code local}；门面未装配时返回 {@code unknown}
     */
    private String apiKeyBackendName() {
        final ApiKeyMinuteRateLimitService service = apiKeyRateLimitServiceProvider.getIfAvailable();
        return service == null ? "unknown" : service.backendName();
    }

    /**
     * 终端用户级计数后端名称.
     *
     * @return {@code redis} / {@code local}；计数器未装配时返回 {@code unknown}
     */
    private String endUserBackendName() {
        final EndUserRateLimitCounter counter = endUserCounterProvider.getIfAvailable();
        if (counter == null) {
            return "unknown";
        }
        return counter instanceof RedisEndUserRateLimitCounter ? "redis" : "local";
    }

    /**
     * 解析标识缺失处理方式（大小写不敏感）.
     *
     * @param raw 原始文本
     * @return 枚举值；非法时返回 {@code null}
     */
    private static MissingIdentity parseMissingIdentity(final String raw) {
        final String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (final MissingIdentity candidate : MissingIdentity.values()) {
            if (candidate.name().equals(normalized)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 限流运行时配置更新请求体.
     *
     * <p>字段均为包装类型以支持部分更新，null = 不修改。
     * 可热改：apiKeyEnabled / endUserEnabled / endUserRequestsPerMinute /
     * endUserHeaderEnabled / endUserMissingIdentity。
     * 需重启：distributedEnabled / distributedTimeoutMs / apiKeyLocalCacheSize /
     * endUserLocalCacheSize / endUserFingerprintLength，携带时被明确拒绝。</p>
     */
    public static class RatelimitConfigUpdateRequest {

        /** API Key 级每分钟限流开关（可热改） */
        public Boolean apiKeyEnabled;

        /** 终端用户级限流开关（可热改） */
        public Boolean endUserEnabled;

        /** 终端用户级每分钟请求上限（可热改，必须大于 0） */
        public Integer endUserRequestsPerMinute;

        /** 是否允许从请求头 X-End-User-Id 读取终端用户标识（可热改） */
        public Boolean endUserHeaderEnabled;

        /** 标识缺失时的处理方式：SKIP / REJECT（可热改） */
        public String endUserMissingIdentity;

        /** 分布式计数是否启用（需重启） */
        public Boolean distributedEnabled;

        /** 分布式计数命令超时毫秒数（需重启） */
        public Long distributedTimeoutMs;

        /** API Key 级本地计数器容量（需重启） */
        public Integer apiKeyLocalCacheSize;

        /** 终端用户级本地计数器容量（需重启） */
        public Integer endUserLocalCacheSize;

        /** 终端用户标识摘要长度（需重启） */
        public Integer endUserFingerprintLength;
    }
}
