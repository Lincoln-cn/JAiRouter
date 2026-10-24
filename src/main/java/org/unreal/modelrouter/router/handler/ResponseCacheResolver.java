package org.unreal.modelrouter.router.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.util.IpUtils;
import org.unreal.modelrouter.router.cache.CachedStreamingResponse;
import org.unreal.modelrouter.router.cache.ResponseCacheService;
import org.unreal.modelrouter.router.loadbalancer.AffinityKeyResolver;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * 响应缓存的读侧：缓存键生成 + 命中回放.
 *
 * <p>从 {@code ServiceRequestHandler} 抽出（issue #604 批次 C）：这一簇是「认证后准备缓存键」
 * 到「实例选择前读缓存」的完整短路路径，与处理器的主流程（配额判定、实例选择、适配器调用、
 * 指标记录）没有耦合，只需缓存门面与可选的 {@link ObjectMapper}。</p>
 *
 * <p><b>三条回放分支必须保持互斥且顺序固定</b>（改动前先读 {@code ServiceRequestHandler}
 * 缓存命中相关用例）：</p>
 * <ol>
 *   <li>{@link CachedStreamingResponse} → 同构 {@code text/event-stream} 逐块回放；</li>
 *   <li>原生面（{@code /v1/**}，见 {@link ServiceRequestHandler#NATIVE_RESPONSE_ATTRIBUTE}）
 *       → 直接返回原生 JSON，<b>不包</b> {@code RouterResponse}；序列化失败时回落第三条；</li>
 *   <li>其余（控制台面 {@code /api/**}）→ {@code RouterResponse} 200 JSON。</li>
 * </ol>
 *
 * <p>缓存门面或 {@link ObjectMapper} 未注入（{@code @Autowired(required = false)} 的空值、
 * 或直接 new 的单测）时对应能力自动退化：无门面则完全不读写缓存，无 ObjectMapper 时原生面
 * 只接受「已是 JSON 文本」的缓存值。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.1
 */
final class ResponseCacheResolver {

    private static final Logger logger = LoggerFactory.getLogger(ResponseCacheResolver.class);

    /** 响应缓存门面；{@code null} 表示缓存未装配（整个读侧短路）。 */
    private final ResponseCacheService responseCacheService;

    /** 原生面 JSON 序列化器；{@code null} 表示未注入（退化为只接受文本缓存值）。 */
    private final ObjectMapper objectMapper;

    /**
     * 构造器.
     *
     * @param responseCacheService 响应缓存门面，可为 {@code null}
     * @param objectMapper         全局 ObjectMapper，可为 {@code null}
     */
    ResponseCacheResolver(final ResponseCacheService responseCacheService, final ObjectMapper objectMapper) {
        this.responseCacheService = responseCacheService;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成响应缓存键并写入 httpRequest attribute（写侧 processor 复用同一个键）.
     *
     * <p>缓存未启用、请求 DTO 缺失、租户键不可得时不生成键——「没有键」即代表本次请求
     * 缓存读写全部关闭，因此写侧不需要额外的开关判断。</p>
     *
     * @param exchange    ServerWebExchange（含 Controller 放入的原始 DTO attribute）
     * @param httpRequest HTTP 请求（缓存键存放处）
     * @param callerId    调用主体标识（API Key ID 或 {@code jwt:<用户名>}）
     * @param endpoint    服务端点
     */
    void prepareKey(final ServerWebExchange exchange,
                    final ServerHttpRequest httpRequest,
                    final String callerId,
                    final ServiceEndpoint endpoint) {
        if (responseCacheService == null || !responseCacheService.isEnabled()) {
            return;
        }
        if (exchange == null || httpRequest == null) {
            return;
        }
        Object requestDto = exchange.getAttribute(ServiceRequestHandler.REQUEST_DTO_ATTRIBUTE);
        if (requestDto == null) {
            return;
        }
        // 租户键: callerId 缺省回退 clientIp（复用 AffinityKeyResolver 语义，防跨租户泄漏）
        String tenantKey = AffinityKeyResolver.resolveTenantKey(callerId, IpUtils.getClientIp(httpRequest));
        if (tenantKey == null) {
            return;
        }
        String cacheKey = responseCacheService.buildKey(tenantKey, endpoint.getServiceType(), requestDto);
        if (cacheKey != null) {
            httpRequest.getAttributes().put(ServiceRequestHandler.CACHE_KEY_ATTRIBUTE, cacheKey);
        }
    }

    /**
     * 读缓存并按值类型回放（未命中 / 不可缓存时返回 {@code null} 继续原流程）.
     *
     * @param httpRequest HTTP 请求（缓存键来源）
     * @param serviceType 服务类型
     * @param modelName   模型名称
     * @param exchange    原始交换对象（读原生面标记），可为 {@code null}
     * @return 缓存命中时的响应实体；未命中或缓存不可用时返回 {@code null}
     */
    ResponseEntity<?> readCached(final ServerHttpRequest httpRequest,
                                 final ServiceType serviceType,
                                 final String modelName,
                                 final ServerWebExchange exchange) {
        if (responseCacheService == null || httpRequest == null) {
            return null;
        }
        Object cacheKey = httpRequest.getAttributes().get(ServiceRequestHandler.CACHE_KEY_ATTRIBUTE);
        if (!(cacheKey instanceof String key) || key.isBlank()) {
            return null;
        }
        String serviceName = serviceType != null ? serviceType.name() : "unknown";
        Optional<Object> cached = responseCacheService.lookup(key, serviceName, modelName);
        if (cached.isEmpty()) {
            return null;
        }
        logger.info("Response cache hit: service={}, model={}", serviceName, modelName);
        Object value = cached.get();
        // v2.9.10: 流式缓存值 → SSE 逐块回放
        if (value instanceof CachedStreamingResponse streamingResponse) {
            return buildStreamingCacheResponse(streamingResponse);
        }
        // v3.1 PR-4c: 原生面缓存命中 → 原生 JSON（不包 RouterResponse）
        if (isNativeResponse(exchange, httpRequest)) {
            ResponseEntity<?> nativeResponse = buildNativeCacheResponse(value);
            if (nativeResponse != null) {
                return nativeResponse;
            }
            logger.warn("原生面缓存命中但缓存值无法序列化为原生 JSON, 回退 RouterResponse 包裹体: type={}",
                    value.getClass().getName());
        }
        // v2.9.9: 非流式缓存值 → RouterResponse JSON（回归；控制台面行为不变）
        RouterResponse<Object> body = RouterResponse.success(value, "请求成功");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /**
     * 是否原生面请求（{@code /v1/**}）：exchange 与 httpRequest 两处标记任一命中即为原生面.
     *
     * @param exchange    原始交换对象，可为 {@code null}
     * @param httpRequest HTTP 请求，可为 {@code null}
     * @return 原生面返回 {@code true}
     */
    boolean isNativeResponse(final ServerWebExchange exchange, final ServerHttpRequest httpRequest) {
        if (exchange != null
                && Boolean.TRUE.equals(exchange.getAttribute(ServiceRequestHandler.NATIVE_RESPONSE_ATTRIBUTE))) {
            return true;
        }
        return httpRequest != null
                && Boolean.TRUE.equals(httpRequest.getAttributes().get(ServiceRequestHandler.NATIVE_RESPONSE_ATTRIBUTE));
    }

    /**
     * 原生面缓存命中响应（原生 JSON 文本）.
     *
     * @param value 缓存值
     * @return 原生 JSON 响应；无法序列化时返回 {@code null}（调用方回落包裹体，不返回破损响应）
     */
    ResponseEntity<?> buildNativeCacheResponse(final Object value) {
        String json = toNativeJson(value);
        if (json == null) {
            return null;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    /**
     * 缓存值 → 原生 JSON 文本（与 {@code NonStreamingRequestProcessor#nativeJson} 同口径）.
     *
     * @param value 缓存值
     * @return JSON 文本；不可得返回 {@code null}
     */
    String toNativeJson(final Object value) {
        if (objectMapper == null) {
            return value instanceof String text ? text : null;
        }
        if (value instanceof String text) {
            try {
                objectMapper.readTree(text);
                return text;
            } catch (JsonProcessingException e) {
                logger.debug("原生面缓存值非合法 JSON 文本, 转为 JSON 字符串: {}", e.getOriginalMessage());
            }
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            logger.warn("原生面缓存值序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 流式缓存值 → {@code text/event-stream} 响应（逐块包装 {@link ServerSentEvent}）.
     *
     * @param cachedResponse 流式缓存值
     * @return SSE 响应实体
     */
    ResponseEntity<?> buildStreamingCacheResponse(final CachedStreamingResponse cachedResponse) {
        Flux<ServerSentEvent<String>> flux = Flux.fromIterable(cachedResponse.chunks())
                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build());
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(flux);
    }
}
