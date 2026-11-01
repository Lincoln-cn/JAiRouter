package org.unreal.modelrouter.router.adapter.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.auth.security.quota.QuotaReservation;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import org.unreal.modelrouter.config.core.StreamingSafetyProperties;
import org.unreal.modelrouter.monitor.callhistory.ApiCallHistoryRecorder;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.config.RecordLevel;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitor.service.TokenUsageRecorder;
import org.unreal.modelrouter.monitor.tracing.TracingContextHolder;
import org.unreal.modelrouter.router.adapter.metrics.AdapterMetricsRecorder;
import org.unreal.modelrouter.monitor.monitoring.metrics.SseConnectionMetrics;
import org.unreal.modelrouter.router.adapter.transformer.ResponseTransformer;
import org.unreal.modelrouter.router.cache.CachedStreamingResponse;
import org.unreal.modelrouter.router.cache.ResponseCacheService;
import org.unreal.modelrouter.router.handler.ServiceRequestHandler;
import org.unreal.modelrouter.router.pool.PoolSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 流式请求处理器
 * 负责 SSE (Server-Sent Events) 格式的流式请求处理
 *
 * @since v2.15.0
 */
@Component
public class StreamingRequestProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StreamingRequestProcessor.class);

    /**
     * #720：请求属性键 —— 客户端是否要求 usage 帧（{@code stream_options.include_usage}）。
     *
     * <p>放在请求属性上而不是参数列表里，是因为适配器拿到的 {@code request} 已被翻译成各自形状；
     * 只有 {@code BaseAdapter} 在翻译前还持有 OpenAI 原生 DTO。</p>
     */
    public static final String INCLUDE_USAGE_ATTRIBUTE =
            StreamingRequestProcessor.class.getName() + ".includeUsage";

    // Token 估算系数
    private static final double ENGLISH_CHARS_PER_TOKEN = 4.0;
    private static final double CHINESE_CHARS_PER_TOKEN = 2.0;

    /** v3.2.3 (#127): 累积内容默认字符上限（宽松；配置可调） */
    static final int DEFAULT_MAX_CONTENT_CHARS = 1_048_576;

    /** v3.2.3 (#127): 流式缓存默认字符预算（超出则不缓存） */
    static final int DEFAULT_MAX_CACHE_CHARS = 262_144;

    /** v3.2.3 (#126): 上游块间空闲超时默认值（宽松；PT0S 可禁用） */
    static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(5);

    /** v3.2.3 (#127): 记录侧截断标记（累积超上界时附加在记录内容尾部） */
    static final String TRUNCATION_MARKER = "\n…[content truncated]";

    private final ResponseTransformer responseTransformer;

    // v3.2.3: 流式安全上界（可选注入，缺省时用宽松常量默认值）
    @Autowired(required = false)
    private StreamingSafetyProperties streamingSafety;

    @Autowired(required = false)
    private MetricsCollector metricsCollector;

    @Autowired(required = false)
    private TokenUsageRecorder tokenUsageRecorder;

    @Autowired(required = false)
    private ApiKeyService apiKeyService;

    // v2.8.9: 资源池选择器(池路由后改写下游流式请求的 model 字段为实际实例名)
    @Autowired(required = false)
    private PoolSelector poolSelector;

    // v2.9.2: 记录治理
    @Autowired(required = false)
    private ApiCallHistoryRecorder callHistoryRecorder;

    @Autowired(required = false)
    private CallHistoryProperties callHistoryProperties;

    @Autowired(required = false)
    private SanitizationService sanitizationService;

    // v2.9.10: 响应缓存门面（可选注入，未装配时跳过流式缓存写）
    @Autowired(required = false)
    private ResponseCacheService responseCacheService;
    
    // #716：流式调用的成败回报口（可选注入；未装配时只记既有 metrics）
    @Autowired(required = false)
    private AdapterMetricsRecorder adapterMetricsRecorder;

    // #947：流式端点连接计数（可选注入；未装配时流语义完全不变）
    @Autowired(required = false)
    private SseConnectionMetrics sseConnectionMetrics;

    // 流式护栏（可选注入；未装配或未启用时链路完全不介入）
    @Autowired(required = false)
    private org.unreal.modelrouter.router.guard.StreamGuardService streamGuardService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public StreamingRequestProcessor(final ResponseTransformer responseTransformer) {
        this.responseTransformer = responseTransformer;
    }

    /**
     * v2.8.9: 池路由后,将请求 Map 的 model 字段改写为实际实例名(仅池名触发)
     */
    @SuppressWarnings("unchecked")
    private Object rewriteModelField(final Object target, final String instanceName) {
        if (target instanceof Map && instanceName != null && poolSelector != null) {
            Object model = ((Map<String, Object>) target).get("model");
            if (model instanceof String && poolSelector.isPoolName((String) model)
                    && !model.equals(instanceName)) {
                ((Map<String, Object>) target).put("model", instanceName);
            }
        }
        return target;
    }

    /**
     * 处理流式请求
     *
     * @param request            请求对象
     * @param authorization      授权信息
     * @param client             WebClient实例
     * @param path               请求路径
     * @param selectedInstance   选中的实例
     * @param serviceType        服务类型
     * @param adapterType        适配器类型
     * @param transformChunkFn   数据块转换函数（可选）
     * @return 流式响应 ResponseEntity
     */
    public <T> Mono<? extends org.springframework.http.ResponseEntity<?>> processStreamingRequest(
            final T request,
            final String authorization,
            final WebClient client,
            final String path,
            final ModelRouterProperties.ModelInstance selectedInstance,
            final ModelServiceRegistry.ServiceType serviceType,
            final String adapterType,
            final Function<String, String> transformChunkFn,
            final ServerHttpRequest httpRequest) {

        final String capturedKeyId = captureApiKeyId(httpRequest);
        // #720：客户端是否要求 usage 帧
        final boolean includeUsage;
        if (httpRequest != null) {
            includeUsage = Boolean.TRUE.equals(
                    httpRequest.getAttributes().get(INCLUDE_USAGE_ATTRIBUTE));
        } else {
            includeUsage = false;
        }
        // v3.1 PR-2: 配额预留凭据（handler 在 selectInstance 前挂载；未预扣/账本未启用时为 null）
        final QuotaReservation quotaReservation = QuotaReservation.from(httpRequest);

        String instanceName = selectedInstance.getName();
        long requestStartTime = System.currentTimeMillis();
        // #716：流终端回调需要实例对象本身（记账要按实例落到熔断器与负载均衡器在途计数）
        final ModelRouterProperties.ModelInstance accountedInstance = selectedInstance;

        logger.debug("开始流式请求: adapter={}, instance={}, path={}", adapterType, instanceName, path);

        // v2.9.10: 流式缓存写 — 读取 handler 预存的缓存键（skipStreaming=false 且确定性请求时非空）
        final String cacheKeyForWrite;
        if (httpRequest != null && responseCacheService != null) {
            Object attr = httpRequest.getAttributes()
                    .get(ServiceRequestHandler.CACHE_KEY_ATTRIBUTE);
            cacheKeyForWrite = (attr instanceof String key && !key.isBlank()) ? key : null;
        } else {
            cacheKeyForWrite = null;
        }

        // v2.9.2: 记录治理 - 在入口处序列化请求体
        final String capturedRequestBody;
        RecordLevel recordLevel = resolveRecordLevel();
        if (recordLevel != RecordLevel.METADATA_ONLY) {
            capturedRequestBody = serializeAndTruncate(request);
        } else {
            capturedRequestBody = null;
        }

        // Token 使用量追踪
        AtomicLong promptTokens = new AtomicLong(0);
        AtomicLong completionTokens = new AtomicLong(0);
        AtomicLong totalTokens = new AtomicLong(0);
        // v2.9.0: KV 缓存命中/未命中 token 追踪
        AtomicLong cacheHitTokens = new AtomicLong(0);
        AtomicLong cacheMissTokens = new AtomicLong(0);
        StringBuilder contentBuilder = new StringBuilder();
        AtomicReference<String> modelRef = new AtomicReference<>("unknown");

        // v3.2.3 (#127): 独立 token 估算计数（按全部增量累计，不受截断影响）+ 截断标记
        final int maxContentChars = maxContentChars();
        final int maxCacheChars = maxCacheChars();
        final AtomicLong contentChineseChars = new AtomicLong(0);
        final AtomicLong contentOtherChars = new AtomicLong(0);
        final AtomicBoolean contentSawAnyChar = new AtomicBoolean(false);
        final AtomicBoolean contentTruncated = new AtomicBoolean(false);
        // #356：是否已经产出过至少一个块 —— 决定失败时「按下游真实状态结束」（未产出）还是
        // 「先补协议 error 帧再终止」（已产出，此时 200 已提交，状态已无法更改）。
        final AtomicBoolean firstChunkEmitted = new AtomicBoolean(false);

        // v2.9.10: 流式缓存写 — 仅缓存键存在时收集变换后块与 finish_reason
        final List<String> transformedChunks = (cacheKeyForWrite != null) ? new ArrayList<>() : null;
        final AtomicReference<String> finishReasonRef = (cacheKeyForWrite != null)
                ? new AtomicReference<>(null) : null;
        // v3.2.3 (#127): 缓存字符预算 — 超预算的响应不缓存（缓存是优化，不是正确性）
        final AtomicLong cacheChars = new AtomicLong(0);
        final AtomicBoolean cacheOverBudget = new AtomicBoolean(false);
        // #721：是否见过终止标记 —— 没有终止标记的流即使 doOnComplete 也不许写缓存
        final AtomicBoolean terminatedSeen = new AtomicBoolean(false);

        // v3.1 修复: 与非流式路径保持一致——实例自定义 headers 优先（含下游 Authorization），
        // 仅在实例未提供 Authorization 时回退到请求传入的 authorization。
        // 修复前流式路径写死 .header("Authorization", authorization)，导致"密钥只配在实例
        // headers 上"的部署（参见 config-external/router/services.yml 的既有模式）流式调用
        // 必然被下游 401 拒绝。
        final Map<String, String> instanceHeaders = (selectedInstance.getHeaders() != null)
                ? selectedInstance.getHeaders() : Map.of();
        final boolean hasInstanceAuth = instanceHeaders.keySet().stream()
                .anyMatch(key -> "Authorization".equalsIgnoreCase(key));

        WebClient.RequestBodySpec requestSpec = client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON);
        for (Map.Entry<String, String> header : instanceHeaders.entrySet()) {
            requestSpec = requestSpec.header(header.getKey(), header.getValue());
            logger.debug("应用实例自定义请求头: {} = {}", header.getKey(), "***");
        }
        if (!hasInstanceAuth && authorization != null && !authorization.isEmpty()) {
            requestSpec = requestSpec.header("Authorization", authorization);
        }

        // 使用 ServerSentEvent 包装每个数据块，确保 SSE 格式正确。
        // v3.2.x (#257 A)：后处理链抽成局部 lambda —— /v1 面需要在**订阅响应体之前**先看下游状态，
        // 因此链路入口从「固定 retrieve()」改为「由调用方传入原始 chunk 流」；
        // legacy /api/** 面仍以同样的 retrieve().onStatus(...) 进入（见下方分支），语义逐字节不变。
        final Function<Flux<String>, Flux<ServerSentEvent<String>>> decorateChunks = rawChunks -> {
            // #720：上游若已经自带 usage 帧（部分 vLLM 会），就不再补第二帧
            final java.util.concurrent.atomic.AtomicBoolean usageFrameSeen =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            // 流式护栏：guardAborted 一旦为 true，本流不再产出上游内容，也不写缓存。
            final java.util.concurrent.atomic.AtomicBoolean guardAborted =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            final java.util.concurrent.atomic.AtomicReference<String> guardPolicy =
                    new java.util.concurrent.atomic.AtomicReference<>(null);
            Flux<ServerSentEvent<String>> streamResponse = rawChunks
                .map(chunk -> {
                    firstChunkEmitted.set(true);
                    // #720：上游自带 usage 帧时只透传，不重复补
                    if (includeUsage && chunk.contains("\"usage\"")) {
                        usageFrameSeen.set(true);
                    }
                    // 提取 usage 信息和累积内容。
                    // v3.2.3: contentBuilder 与 doOnCancel/doOnError 的缓冲释放共享监视器
                    // （Reactor 不保证 cancel 与 onNext 串行，StringBuilder 非线程安全）。
                    synchronized (contentBuilder) {
                        final int contentLenBefore = contentBuilder.length();
                        extractUsageAndContent(chunk, promptTokens, completionTokens, totalTokens,
                                cacheHitTokens, cacheMissTokens, contentBuilder, modelRef);
                        // v3.2.3 (#127): 独立计数（截断前）+ 累积上界
                        accountAppendedChars(contentBuilder, contentLenBefore,
                                contentChineseChars, contentOtherChars, contentSawAnyChar);
                        if (contentBuilder.length() > maxContentChars) {
                            contentBuilder.setLength(maxContentChars);
                            contentTruncated.set(true);
                        }
                    }
                    // v2.9.10: 提取 finish_reason 用于流式缓存
                    if (cacheKeyForWrite != null) {
                        extractFinishReason(chunk, finishReasonRef);
                        // #721：记录终止标记（[DONE] / message_stop），作为写缓存的必要条件
                        if (isTerminatorChunk(chunk)) {
                            terminatedSeen.set(true);
                        }
                    }
                    // 流式护栏：ABORT 时用终止帧替换本块并让整条流到此为止；
                    // REDACT 时把改写后的文本交给后续的适配器变换（顺序：先脱敏，再变换）。
                    String guardedChunk = chunk;
                    if (streamGuardService != null && streamGuardService.enabled()) {
                        final org.unreal.modelrouter.router.guard.GuardDecision guardDecision =
                                streamGuardService.evaluate(chunk);
                        if (guardDecision.aborting()) {
                            guardAborted.set(true);
                            guardPolicy.set(guardDecision.policy());
                            logger.warn("流式响应被护栏阻断：policy={}, instance={}, adapter={}",
                                    guardDecision.policy(), instanceName, adapterType);
                            return ServerSentEvent.<String>builder().data("[DONE]").build();
                        }
                        if (guardDecision.redacted()) {
                            guardedChunk = guardDecision.output();
                        }
                    }
                    ServerSentEvent<String> sse = transformAndWrapChunk(guardedChunk, transformChunkFn);
                    // v2.9.10: 收集变换后 data 串用于流式缓存（v3.2.3: 受缓存字符预算约束）
                    if (transformedChunks != null && !cacheOverBudget.get()) {
                        final String data = sse.data();
                        cacheChars.addAndGet(data != null ? data.length() : 0);
                        synchronized (transformedChunks) {
                            if (cacheChars.get() > maxCacheChars) {
                                cacheOverBudget.set(true);
                                transformedChunks.clear();
                            } else {
                                transformedChunks.add(data);
                            }
                        }
                    }
                    return sse;
                });

        // v3.2.3 (#126): 块间空闲看门狗 — 超时向上游 cancel（释放连接），
        // 向下游发出终止错误（TimeoutException），经 doOnError 回滚配额并释放累积缓冲。
        // 空闲型而非总时长型：长但持续产出的流不受影响。PT0S/负值可禁用。
        // 流式护栏：阻断帧发出后立即结束，不再等待上游后续内容。
        streamResponse = streamResponse.takeUntil(event -> guardAborted.get());

        final Duration idleTimeout = idleTimeout();
        if (idleTimeout != null && !idleTimeout.isZero() && !idleTimeout.isNegative()) {
            streamResponse = streamResponse.timeout(idleTimeout);
        }

        streamResponse = streamResponse
                .doOnComplete(() -> {
                    recordStreamingComplete(serviceType, adapterType, instanceName, requestStartTime);
                    // #716：流正常结束才是这次调用「成功」——建流时记成功会把熔断失败计数清零
                    reportStreamOutcome(serviceType, adapterType, accountedInstance, instanceName,
                            modelRef.get(), requestStartTime, AdapterMetricsRecorder.CallOutcome.SUCCESS, null);

                    // v2.9.10: 流式缓存写 — 完整成功流结束后缓存（错误/中断不触发 doOnComplete）
                    // v3.2.3 (#127): 超出缓存预算的响应不缓存
                    // #721：还必须见过终止标记 —— 上游干净关闭但没发 [DONE]/finish_reason 时，
                    // 半截内容曾被按默认 1 小时 TTL 缓存，后续同参数请求回放的都是缺尾输出。
                    final boolean terminated = terminatedSeen.get()
                            || (finishReasonRef != null && finishReasonRef.get() != null);
                    if (guardAborted.get() && cacheKeyForWrite != null) {
                        logger.debug("流式响应被护栏阻断，跳过写缓存: key={}, policy={}",
                                cacheKeyForWrite, guardPolicy.get());
                    }
                    if (cacheKeyForWrite != null && responseCacheService != null
                            && transformedChunks != null && !transformedChunks.isEmpty()
                            && !cacheOverBudget.get() && terminated && !guardAborted.get()) {
                        final List<String> chunksToCache;
                        synchronized (transformedChunks) {
                            chunksToCache = List.copyOf(transformedChunks);
                        }
                        cacheStreamingResponse(cacheKeyForWrite, new CachedStreamingResponse(
                                chunksToCache,
                                modelRef.get(),
                                promptTokens.get() > 0 ? promptTokens.get() : null,
                                completionTokens.get() > 0 ? completionTokens.get() : null,
                                totalTokens.get() > 0 ? totalTokens.get() : null,
                                finishReasonRef != null ? finishReasonRef.get() : null,
                                true));
                    } else if (cacheKeyForWrite != null && !transformedChunks.isEmpty()
                            && !cacheOverBudget.get() && !terminated) {
                        logger.debug("#721 流未出现终止标记（[DONE]/finish_reason/message_stop），跳过写缓存: key={}",
                                cacheKeyForWrite);
                    }

                    // 记录 token 使用量(含 KV 缓存指标)。
                    // v3.2.3 (#127): 估算走独立计数，不依赖可能被截断的 contentBuilder。
                    recordTokenUsage(adapterType, instanceName, modelRef.get(),
                            promptTokens.get(), completionTokens.get(), totalTokens.get(),
                            cacheHitTokens.get(), cacheMissTokens.get(),
                            estimateTokensFromCounts(contentChineseChars.get(), contentOtherChars.get()),
                            contentSawAnyChar.get(), capturedKeyId, true, quotaReservation);

                    // v2.9.2: 记录治理 - 记录含请求/响应体的调用历史
                    // 脱敏与落库不得阻塞 EventLoop（doOnComplete 可能在 IO 线程执行）
                    if (recordLevel != RecordLevel.METADATA_ONLY && callHistoryRecorder != null) {
                        final long duration = System.currentTimeMillis() - requestStartTime;
                        final String rawResponseBody;
                        synchronized (contentBuilder) {
                            rawResponseBody = recordedResponseBody(contentBuilder, contentTruncated.get());
                        }
                        final String modelForRecord = modelRef.get();
                        final long promptTok = promptTokens.get();
                        final long completionTok = completionTokens.get();
                        final long totalTok = totalTokens.get();
                        final boolean summarySanitize = recordLevel == RecordLevel.SUMMARY
                                && sanitizationService != null && rawResponseBody != null;

                        Mono<String> bodyMono = summarySanitize
                                ? sanitizationService.sanitizeForStorage(rawResponseBody, "application/json")
                                        .timeout(Duration.ofSeconds(5))
                                        .onErrorResume(e -> {
                                            logger.debug("流式响应体脱敏失败: {}", e.getMessage());
                                            return Mono.just(rawResponseBody);
                                        })
                                        .map(sanitized -> sanitized != null ? sanitized : rawResponseBody)
                                : Mono.just(rawResponseBody);

                        bodyMono.subscribeOn(Schedulers.boundedElastic())
                                .subscribe(responseBodyForRecord -> {
                                    try {
                                        CallHistoryRecordDTO dto = CallHistoryRecordDTO.builder()
                                                .serviceType(serviceType != null ? serviceType.name() : null)
                                                .modelName(modelForRecord)
                                                .provider(adapterType)
                                                .instanceName(instanceName)
                                                .instanceUrl(selectedInstance.getBaseUrl())
                                                .responseTimeMs(duration)
                                                .isSuccess(true)
                                                .promptTokens(promptTok > 0 ? promptTok : null)
                                                .completionTokens(completionTok > 0 ? completionTok : null)
                                                .totalTokens(totalTok > 0 ? totalTok : null)
                                                .apiKeyId(capturedKeyId)
                                                .requestBody(capturedRequestBody)
                                                .responseBody(responseBodyForRecord)
                                                .build();
                                        callHistoryRecorder.record(dto);
                                    } catch (Exception e) {
                                        logger.debug("流式调用历史记录失败: {}", e.getMessage());
                                    }
                                }, err -> logger.debug("流式调用历史脱敏订阅失败: {}", err.getMessage()));
                    }
                })
                .doOnError(throwable -> {
                    // v3.2.3 (#126/#127): 释放累积缓冲（空闲超时/异常终止时立即归还内存）。
                    // onError 与 onNext 串行，此处与 map() 无并发；同步仅统一监视器约定。
                    releaseAccumulation(contentBuilder, transformedChunks);
                    recordStreamingError(serviceType, adapterType, instanceName,
                            requestStartTime, throwable);
                    // #716：流内失败（含首块之后的 5xx / 空闲超时 / 连接中断）回挂到熔断器
                    reportStreamOutcome(serviceType, adapterType, accountedInstance, instanceName,
                            modelRef.get(), requestStartTime,
                            AdapterMetricsRecorder.classifyFailure(throwable),
                            throwable.getClass().getSimpleName());
                    // #710：已流出的 token 是真实成本 —— 异常路径也要入账并落一条失败调用记录
                    final long errorEstimated = estimateTokensFromCounts(
                            contentChineseChars.get(), contentOtherChars.get());
                    final long errorCompletion = completionTokens.get() > 0
                            ? completionTokens.get() : errorEstimated;
                    final long errorPrompt = promptTokens.get();
                    final long errorTotal = totalTokens.get() > 0
                            ? totalTokens.get() : errorPrompt + errorCompletion;
                    recordTokenUsage(adapterType, instanceName, modelRef.get(), errorPrompt, errorCompletion,
                            errorTotal, cacheHitTokens.get(), cacheMissTokens.get(), errorEstimated,
                            contentSawAnyChar.get(), capturedKeyId, false, null);
                    recordInterruptedCallHistory(serviceType, adapterType, instanceName, modelRef.get(),
                            accountedInstance != null ? accountedInstance.getBaseUrl() : null,
                            requestStartTime, errorPrompt, errorCompletion, errorTotal, capturedKeyId,
                            recordLevel, throwable.getClass().getSimpleName(), capturedRequestBody,
                            recordedResponseBody(contentBuilder, contentTruncated.get()));
                    // v3.1 PR-2: 流式中断/异常 — 回滚整笔配额预留
                    QuotaReservation.settleFailure(quotaReservation);
                })
                .doOnCancel(() -> {
                    // v3.2.3 (#126/#127): 同样释放累积缓冲（cancel 不与 onNext 串行，须持锁）
                    releaseAccumulation(contentBuilder, transformedChunks);
                    // #716：客户端主动断开不是实例故障 —— 只把在途计数减掉，不累加熔断失败
                    reportStreamOutcome(serviceType, adapterType, accountedInstance, instanceName,
                            modelRef.get(), requestStartTime,
                            AdapterMetricsRecorder.CallOutcome.CLIENT_ERROR, "CLIENT_CANCELLED");
                    // #710：取消不是「这次请求没发生过」—— 已消耗部分入账并按实际结算，不再整笔回滚
                    final long cancelEstimated = estimateTokensFromCounts(
                            contentChineseChars.get(), contentOtherChars.get());
                    final long cancelCompletion = completionTokens.get() > 0
                            ? completionTokens.get() : cancelEstimated;
                    final long cancelPrompt = promptTokens.get();
                    final long cancelTotal = totalTokens.get() > 0
                            ? totalTokens.get() : cancelPrompt + cancelCompletion;
                    recordTokenUsage(adapterType, instanceName, modelRef.get(), cancelPrompt, cancelCompletion,
                            cancelTotal, cacheHitTokens.get(), cacheMissTokens.get(), cancelEstimated,
                            contentSawAnyChar.get(), capturedKeyId, false, null);
                    recordInterruptedCallHistory(serviceType, adapterType, instanceName, modelRef.get(),
                            accountedInstance != null ? accountedInstance.getBaseUrl() : null,
                            requestStartTime, cancelPrompt, cancelCompletion, cancelTotal, capturedKeyId,
                            recordLevel, "CLIENT_CANCELLED", capturedRequestBody,
                            recordedResponseBody(contentBuilder, contentTruncated.get()));
                    if (cancelTotal > 0) {
                        // 已流出部分付费、未流出部分退回；空请求仍整笔回滚，避免占额度
                        QuotaReservation.settlePartial(quotaReservation, cancelTotal);
                    } else {
                        QuotaReservation.settleFailure(quotaReservation);
                    }
                })
                // v3.2.x (#257): /v1 OpenAI 面在失败时先补一个协议形状 error 帧、再以异常终止
                // （不补 [DONE]：它是「正常结束」的语汇，补上会让截断被读成成功；详见 StreamingErrorTermination）。
                // #356：**只在首块之后**补帧 —— 首块之前失败时状态码还没提交，必须让异常原样上抛，
                // 才能按下游真实状态结束（#257 A 的状态保真）；若此时补帧，WebFlux 会先写出 200，
                // 客户端就把「下游 500」读成 200 + error 帧（本类测试的 face=v1 mode=before 用例钉住这一点）。
                .onErrorResume(throwable -> firstChunkEmitted.get()
                        ? StreamingErrorTermination.terminate(throwable, httpRequest, objectMapper)
                        : Flux.error(throwable));

            if (includeUsage) {
                // #720：OpenAI 语义要求 usage 帧出现在 [DONE] 之前、choices 为空数组。
                // 上游的 [DONE] 由网关重新发（保持「只有正常结束才有 [DONE]」这条既有契约）。
                streamResponse = streamResponse
                        .filter(event -> !isDoneEvent(event))
                        .concatWith(Flux.defer(() -> usageFrameSeen.get()
                                ? Flux.just(doneEvent())
                                : Flux.just(buildUsageEvent(modelRef.get(), promptTokens.get(),
                                        completionTokens.get(), totalTokens.get()), doneEvent())));
            }

            return streamResponse;
        };

        if (StreamingErrorTermination.isV1Face(httpRequest)) {
            // #257 A：/v1 面在提交响应前按**下游真实状态**结束（不再统一成 500 + 空体），
            // 错误体由 /v1 面的既有协议化机制产出（ReactiveGlobalExceptionHandler → V1ErrorBodyMapper）。
            //
            // #356 修复：原实现用 `exchangeToMono` 并把 `clientResponse.bodyToFlux(...)` 交回框架。
            // `Mono.just(ResponseEntity...)` 会**立即终止**，WebClient 随之释放并排空响应体
            // ⇒ 框架后续订阅那个 body flux 时一个 chunk 都拿不到：客户端看到
            // `200 + text/event-stream + chunked` 但**响应体 0 字节**（根因已由
            // StreamingErrorPathRuntimeTest 的 face=v1 用例在本机确定性复现）。
            // 现改为 `retrieve()` + 状态保真的 `onStatus`：错误仍在**订阅响应体之前**发生，
            // 而正常流的 body 与 legacy 面走同一条已验证有帧的 `bodyToFlux` 路径。
            final Flux<ServerSentEvent<String>> v1Stream = decorateChunks.apply(requestSpec
                    .bodyValue(rewriteModelField(request, instanceName))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> {
                        logger.warn("流式请求下游错误: instance={}, status={}",
                                instanceName, clientResponse.statusCode());
                        return clientResponse.releaseBody()
                                .then(Mono.error(new ResponseStatusException(
                                        clientResponse.statusCode(), "下游服务错误")));
                    })
                    .bodyToFlux(String.class));

            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(trackStream(v1Stream, "v1-chat-completions")));
        }

        // legacy /api/** 面（与 Anthropic 面之外的其余路径）：保持既有语义 —— 200 + 流，异常在流内终止
        final Flux<ServerSentEvent<String>> legacyStream = decorateChunks.apply(requestSpec
                .bodyValue(rewriteModelField(request, instanceName))
                .retrieve()
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                    logger.error("流式请求5xx错误: instance={}, status={}", instanceName, clientResponse.statusCode());
                    return Mono.error(new ResponseStatusException(
                            clientResponse.statusCode(), "下游服务错误"));
                })
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                    logger.error("流式请求4xx错误: instance={}, status={}", instanceName, clientResponse.statusCode());
                    return Mono.error(new ResponseStatusException(
                            clientResponse.statusCode(), "请求错误"));
                })
                .bodyToFlux(String.class));

        return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(trackStream(legacyStream, "chat-completions")));
    }

    /**
     * #947：给流式端点包一层活跃连接计数。
     *
     * <p>指标未装配时原样返回 —— 计数是观测能力，不能成为流式链路的必要条件。</p>
     *
     * @param stream   原始事件流
     * @param endpoint 端点标识（作为 gauge 标签）
     * @return 计数包装后的事件流
     */
    Flux<ServerSentEvent<String>> trackStream(final Flux<ServerSentEvent<String>> stream,
                                                      final String endpoint) {
        return sseConnectionMetrics == null ? stream : sseConnectionMetrics.track(stream, endpoint);
    }

    /**
     * 转换并包装数据块为 SSE 格式
     */
    private ServerSentEvent<String> transformAndWrapChunk(final String chunk,
                                                            final Function<String, String> transformFn) {
        String transformed;
        if (transformFn != null) {
            transformed = transformFn.apply(chunk);
        } else {
            transformed = responseTransformer.transformStreamChunk(chunk);
        }

        return ServerSentEvent.<String>builder()
                .data(transformed)
                .build();
    }

    /**
     * #720：判断一个 SSE 事件是不是终止帧 {@code [DONE]}。
     *
     * @param event SSE 事件
     * @return 是否为终止帧
     */
    private static boolean isDoneEvent(final ServerSentEvent<String> event) {
        final String data = event == null ? null : event.data();
        return data != null && "[DONE]".equals(data.trim());
    }

    /**
     * #720：构造终止帧。
     *
     * @return 只含 {@code [DONE]} 的 SSE 事件
     */
    private static ServerSentEvent<String> doneEvent() {
        return ServerSentEvent.<String>builder().data("[DONE]").build();
    }

    /**
     * #720：构造 usage 帧（OpenAI 语义：{@code choices} 为空数组，usage 三个计数）。
     *
     * @param model            模型名
     * @param promptTokens     prompt tokens
     * @param completionTokens completion tokens
     * @param totalTokens      total tokens
     * @return usage 帧
     */
    private ServerSentEvent<String> buildUsageEvent(final String model,
                                                     final long promptTokens,
                                                     final long completionTokens,
                                                     final long totalTokens) {
        final java.util.Map<String, Object> usage = new java.util.LinkedHashMap<>();
        usage.put("prompt_tokens", promptTokens);
        usage.put("completion_tokens", completionTokens);
        usage.put("total_tokens", totalTokens > 0 ? totalTokens : promptTokens + completionTokens);

        final java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("id", "chatcmpl-usage-" + java.util.UUID.randomUUID());
        payload.put("object", "chat.completion.chunk");
        payload.put("created", System.currentTimeMillis() / 1000L);
        payload.put("model", model != null ? model : "");
        payload.put("choices", java.util.List.of());
        payload.put("usage", usage);

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            json = "{\"choices\":[],\"usage\":{\"prompt_tokens\":" + promptTokens
                    + ",\"completion_tokens\":" + completionTokens + ",\"total_tokens\":" + usage.get("total_tokens") + "}}";
        }
        return ServerSentEvent.<String>builder().data(json).build();
    }

    /**
     * #716：把流式调用的最终结果回报给记账层（服务注册 / 熔断 / 指标 / 统计）。
     *
     * <p>非流式路径由 {@code BaseAdapter} 在响应就绪时记账；流式路径的「成功」必须等流正常结束，
     * 否则持续在首块之后失败的实例永远进不了 OPEN（#716）。</p>
     */
    private void reportStreamOutcome(final ModelServiceRegistry.ServiceType serviceType,
                                     final String adapterType,
                                     final ModelRouterProperties.ModelInstance instance,
                                     final String instanceName,
                                     final String modelName,
                                     final long startTime,
                                     final AdapterMetricsRecorder.CallOutcome outcome,
                                     final String errorCode) {
        final AdapterMetricsRecorder recorder = adapterMetricsRecorder;
        if (recorder == null || instance == null || serviceType == null) {
            return;
        }
        final long duration = System.currentTimeMillis() - startTime;
        try {
            recorder.recordCompleteCallStats(adapterType, instanceName, duration, outcome, errorCode,
                    modelName, serviceType, instance);
        } catch (RuntimeException e) {
            // 记账失败不得影响已终止的响应（与 BaseAdapter 的既有姿态一致）
            logger.debug("流式调用结果记账失败: {}", e.getMessage());
        }
    }

    /**
     * 记录流式请求完成指标
     */
    private void recordStreamingComplete(final ModelServiceRegistry.ServiceType serviceType,
                                          final String adapterType,
                                          final String instanceName,
                                          final long startTime) {
        if (metricsCollector != null) {
            long responseTime = System.currentTimeMillis() - startTime;
            metricsCollector.recordRequest(serviceType.name(), "STREAM", responseTime, "200");
            // #716：后端调用计数改由 AdapterMetricsRecorder 在流终端统一记录，避免重复计数
            logger.debug("流式请求完成: adapter={}, instance={}, duration={}ms",
                    adapterType, instanceName, responseTime);
        }
    }

    /**
     * 记录流式请求错误指标
     */
    private void recordStreamingError(final ModelServiceRegistry.ServiceType serviceType,
                                        final String adapterType,
                                        final String instanceName,
                                        final long startTime,
                                        final Throwable throwable) {
        if (metricsCollector != null) {
            long responseTime = System.currentTimeMillis() - startTime;
            // #716：失败计数同样由 AdapterMetricsRecorder 在流终端统一记录（只保留日志）
            logger.error("流式请求错误: adapter={}, instance={}, error={}",
                    adapterType, instanceName, throwable.getMessage());
        }
    }

    /**
     * #710：为被打断的流落一条调用记录（{@code isSuccess=false} + 错误码）。
     *
     * <p>与完成路径的区别只在于成功标记与错误码；响应体取中断前已累积的内容。
     * 记录等级为 {@code METADATA_ONLY} 或未注入记录器时直接跳过。</p>
     *
     * @param serviceType     服务类型
     * @param adapterType     适配器类型
     * @param instanceName    实例名
     * @param model           模型名
     * @param instanceUrl     实例地址（可为 null）
     * @param requestStartTime 请求开始时间
     * @param promptTokens    已用量：输入 token（可为 0）
     * @param completionTokens 已用量：输出 token（可为 0）
     * @param totalTokens     已用量：合计 token（可为 0）
     * @param apiKeyId        API Key 标识（可为 null）
     * @param recordLevel     记录等级（{@code METADATA_ONLY} 时跳过）
     * @param errorCode       错误码
     * @param requestBody     请求体（可为 null）
     * @param responseBody    中断前已累积的响应体（可为 null）
     * @since 3.5.5
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    private void recordInterruptedCallHistory(final ModelServiceRegistry.ServiceType serviceType,
                                              final String adapterType,
                                              final String instanceName,
                                              final String model,
                                              final String instanceUrl,
                                              final long requestStartTime,
                                              final long promptTokens,
                                              final long completionTokens,
                                              final long totalTokens,
                                              final String apiKeyId,
                                              final RecordLevel recordLevel,
                                              final String errorCode,
                                              final String requestBody,
                                              final String responseBody) {
        if (recordLevel == RecordLevel.METADATA_ONLY || callHistoryRecorder == null) {
            return;
        }
        try {
            final CallHistoryRecordDTO dto = CallHistoryRecordDTO.builder()
                    .serviceType(serviceType != null ? serviceType.name() : null)
                    .modelName(model)
                    .provider(adapterType)
                    .instanceName(instanceName)
                    .instanceUrl(instanceUrl)
                    .responseTimeMs(System.currentTimeMillis() - requestStartTime)
                    .isSuccess(false)
                    .errorCode(errorCode)
                    .promptTokens(promptTokens > 0 ? promptTokens : null)
                    .completionTokens(completionTokens > 0 ? completionTokens : null)
                    .totalTokens(totalTokens > 0 ? totalTokens : null)
                    .apiKeyId(apiKeyId)
                    .requestBody(requestBody)
                    .responseBody(responseBody)
                    .build();
            callHistoryRecorder.record(dto);
        } catch (RuntimeException e) {
            logger.debug("流式中断的调用历史记录失败: {}", e.getMessage());
        }
    }

    /**
     * 获取默认的数据块转换器
     */
    public Function<String, String> getDefaultChunkTransformer(final String adapterType) {
        return chunk -> responseTransformer.transformStreamChunk(chunk);
    }

    /**
     * 处理流式请求（使用默认转换器）
     */
    public <T> Mono<? extends org.springframework.http.ResponseEntity<?>> processStreamingRequest(
            final T request,
            final String authorization,
            final WebClient client,
            final String path,
            final ModelRouterProperties.ModelInstance selectedInstance,
            final ModelServiceRegistry.ServiceType serviceType,
            final String adapterType,
            final ServerHttpRequest httpRequest) {

        return processStreamingRequest(request, authorization, client, path,
                selectedInstance, serviceType, adapterType, null, httpRequest);
    }

    /**
     * 从 SSE chunk 提取 usage 信息和累积响应内容
     *
     * @param chunk              SSE 数据块
     * @param promptTokens       prompt tokens 计数器
     * @param completionTokens   completion tokens 计数器
     * @param totalTokens        total tokens 计数器
     * @param cacheHitTokens     v2.9.0: KV 缓存命中 token 计数器
     * @param cacheMissTokens    v2.9.0: KV 缓存未命中 token 计数器
     * @param contentBuilder     内容累积器
     * @param modelRef           模型名称引用
     */
    private void extractUsageAndContent(final String chunk,
                                         final AtomicLong promptTokens,
                                         final AtomicLong completionTokens,
                                         final AtomicLong totalTokens,
                                         final AtomicLong cacheHitTokens,
                                         final AtomicLong cacheMissTokens,
                                         final StringBuilder contentBuilder,
                                         final AtomicReference<String> modelRef) {
        try {
            String jsonPart = chunk;
            if (chunk.startsWith("data: ")) {
                jsonPart = chunk.substring(6);
            }

            if ("[DONE]".equals(jsonPart.trim())) {
                return;
            }

            JsonNode jsonNode = objectMapper.readTree(jsonPart);

            // 提取模型名称
            if (jsonNode.has("model")) {
                modelRef.set(jsonNode.get("model").asText());
            }

            // 提取 usage 信息（如果后端提供）
            if (jsonNode.has("usage")) {
                JsonNode usage = jsonNode.get("usage");
                if (usage.has("prompt_tokens")) {
                    promptTokens.set(usage.get("prompt_tokens").asLong());
                }
                if (usage.has("completion_tokens")) {
                    completionTokens.set(usage.get("completion_tokens").asLong());
                }
                if (usage.has("total_tokens")) {
                    totalTokens.set(usage.get("total_tokens").asLong());
                }
                // v2.9.0: 提取 KV 缓存命中/未命中 token 数
                // DeepSeek 形态
                if (usage.has("prompt_cache_hit_tokens")) {
                    cacheHitTokens.set(usage.get("prompt_cache_hit_tokens").asLong());
                }
                if (usage.has("prompt_cache_miss_tokens")) {
                    cacheMissTokens.set(usage.get("prompt_cache_miss_tokens").asLong());
                }
                // OpenAI/vLLM 形态: prompt_tokens_details.cached_tokens
                if (cacheHitTokens.get() == 0 && usage.has("prompt_tokens_details")) {
                    JsonNode details = usage.get("prompt_tokens_details");
                    if (details.has("cached_tokens")) {
                        cacheHitTokens.set(details.get("cached_tokens").asLong());
                    }
                }
            }

            // 累积响应内容（从 choices 中提取）
            if (jsonNode.has("choices")) {
                JsonNode choices = jsonNode.get("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode delta = choices.get(0).path("delta");
                    if (delta.has("content")) {
                        contentBuilder.append(delta.get("content").asText());
                    }
                }
            }
        } catch (Exception e) {
            logger.trace("Failed to parse chunk for usage extraction: {}", e.getMessage());
        }
    }

    /**
     * 记录 Token 使用量
     * 如果后端未提供 usage 信息，则根据独立字符计数进行估算（与完整内容估算逐字节一致）
     *
     * <p>v3.1 PR-2: 本方法同时是流式链路的配额结算点——在 token 落库处按
     * {@code 实际 − 估算} 冲正预留（{@link QuotaReservation#settleSuccess(QuotaReservation, long)}），
     * 中断/异常路径由调用方的 {@code doOnError} / {@code doOnCancel} 回滚，
     * 凭据自身保证恰一次结算。</p>
     *
     * <p>v3.2.3 (#127): 估算入参改为独立累计的 token 数与「是否见过内容」标记，
     * 不再读取可能被截断的累积缓冲——截断不影响账单/配额。</p>
     */
    private void recordTokenUsage(final String adapterType,
                                   final String instanceName,
                                   final String model,
                                   final long promptTokens,
                                   final long completionTokens,
                                   final long totalTokens,
                                   final long cacheHitTokens,
                                   final long cacheMissTokens,
                                   final long estimatedCompletionTokens,
                                   final boolean sawContent,
                                   final String apiKeyId,
                                   final boolean success,
                                   final QuotaReservation quotaReservation) {
        if (tokenUsageRecorder == null) {
            return;
        }

        long finalPromptTokens = promptTokens;
        long finalCompletionTokens = completionTokens;
        long finalTotalTokens = totalTokens;

        // 如果后端未提供 usage，则估算（估算值来自独立计数，与截断无关）
        if (totalTokens == 0 && sawContent) {
            finalCompletionTokens = estimatedCompletionTokens;
            finalTotalTokens = finalPromptTokens + finalCompletionTokens;
            logger.debug("Token usage estimated: adapter={}, instance={}, prompt={}, completion={}, total={}",
                    adapterType, instanceName, finalPromptTokens, finalCompletionTokens, finalTotalTokens);
        } else if (totalTokens > 0) {
            logger.debug("Token usage from backend: adapter={}, instance={}, prompt={}, completion={}, total={}",
                    adapterType, instanceName, finalPromptTokens, finalCompletionTokens, finalTotalTokens);
        }

        // 只有当有实际 token 使用量时才记录
        if (finalTotalTokens > 0) {
            try {
                // 获取 traceId
                String traceId = TracingContextHolder.getCurrentTraceId();

                tokenUsageRecorder.recordTokenUsageNoAuth(
                        "CHAT",
                        model,
                        adapterType,
                        instanceName,
                        null, // instanceUrl
                        apiKeyId, // #713：按 Key 统计所需
                        finalPromptTokens,
                        finalCompletionTokens,
                        finalTotalTokens,
                        traceId,
                        null, // clientIp
                        success, // #710：中断路径落 isSuccess=false，别把断流记成成功调用
                        null, // errorCode
                        null, // errorMessage
                        null  // responseTimeMs
                );

                // 更新 API Key 的每日 Token 使用量配额
                updateApiKeyTokenUsage(apiKeyId, finalTotalTokens);

                // v3.1 PR-2: 配额结算 — 按实际用量冲正预留（恰一次；失败/中断路径回滚）
                QuotaReservation.settleSuccess(quotaReservation, finalTotalTokens);

                // v2.9.0: 记录 KV 缓存命中/未命中指标
                if (metricsCollector != null && (cacheHitTokens > 0 || cacheMissTokens > 0)) {
                    metricsCollector.recordCacheTokenUsage(adapterType, instanceName,
                            cacheHitTokens, cacheMissTokens);
                }

            } catch (Exception e) {
                logger.warn("Failed to record token usage: {}", e.getMessage());
            }
        }

        // v2.9.0: 即使 totalTokens==0，如果有缓存命中信息也记录指标
        if (finalTotalTokens == 0 && metricsCollector != null && (cacheHitTokens > 0 || cacheMissTokens > 0)) {
            metricsCollector.recordCacheTokenUsage(adapterType, instanceName,
                    cacheHitTokens, cacheMissTokens);
        }
    }

    /**
     * 更新 API Key 的 Token 使用量
     */
    private void updateApiKeyTokenUsage(final String apiKeyId, final long totalTokens) {
        if (apiKeyService == null || totalTokens <= 0 || apiKeyId == null) {
            return;
        }
        try {
            apiKeyService.updateTokenUsage(apiKeyId, totalTokens);
            logger.debug("API Key token usage updated: keyId={}, tokens={}", apiKeyId, totalTokens);
        } catch (Exception e) {
            logger.debug("Failed to update API Key token usage: keyId={}, error={}", apiKeyId, e.getMessage());
        }
    }

    /**
     * 从请求属性中捕获 API Key ID
     * 使用 ServiceRequestHandler 预存的 keyId（与 NonStreamingRequestProcessor 一致）
     */
    private String captureApiKeyId(final ServerHttpRequest httpRequest) {
        if (httpRequest == null) {
            return null;
        }
        try {
            Object keyId = httpRequest.getAttributes()
                    .get(org.unreal.modelrouter.router.handler.ServiceRequestHandler.API_KEY_ID_ATTRIBUTE);
            if (keyId instanceof String key && !key.isBlank()) {
                return key;
            }
        } catch (Exception e) {
            logger.debug("Failed to capture API Key ID from request attributes: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 根据内容估算 token 数量
     * 英文约 4 字符/token，中文约 2 字符/token
     */
    private long estimateTokens(final String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }

        int chineseChars = 0;
        int otherChars = 0;

        for (char c : content.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }

        return (long) Math.ceil(chineseChars / CHINESE_CHARS_PER_TOKEN
                + otherChars / ENGLISH_CHARS_PER_TOKEN);
    }

    // ==================== v3.2.3 (#126, #127): 流式安全辅助 ====================

    /**
     * 释放累积缓冲（异常/取消终止时立即归还内存）。
     *
     * <p>{@code doOnCancel} 与 {@code onNext} 不保证串行，必须与 {@code map()} 中的
     * 累积写入共用监视器，避免 {@link StringBuilder}/{@link ArrayList} 并发损坏。</p>
     *
     * @param contentBuilder    内容累积缓冲
     * @param transformedChunks 流式缓存块收集器（无缓存键时为 {@code null}）
     */
    private void releaseAccumulation(final StringBuilder contentBuilder,
                                     final List<String> transformedChunks) {
        synchronized (contentBuilder) {
            contentBuilder.setLength(0);
        }
        if (transformedChunks != null) {
            synchronized (transformedChunks) {
                transformedChunks.clear();
            }
        }
    }

    /**
     * 将 contentBuilder 新增区间计入独立 token 估算计数（截断前调用，保证与完整内容估算一致）.
     *
     * @param contentBuilder   累积缓冲
     * @param from             本次增量起始下标
     * @param chineseChars     汉字计数（估算用，独立于缓冲）
     * @param otherChars       其余非空白字符计数（估算用，独立于缓冲）
     * @param sawAnyChar       是否出现过任意字符（含空白；决定估算分支是否启用）
     */
    private void accountAppendedChars(final StringBuilder contentBuilder,
                                      final int from,
                                      final AtomicLong chineseChars,
                                      final AtomicLong otherChars,
                                      final AtomicBoolean sawAnyChar) {
        final int to = contentBuilder.length();
        if (to <= from) {
            return;
        }
        sawAnyChar.set(true);
        for (int i = from; i < to; i++) {
            final char c = contentBuilder.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars.incrementAndGet();
            } else if (!Character.isWhitespace(c)) {
                otherChars.incrementAndGet();
            }
        }
    }

    /**
     * 由独立字符计数估算 token 数——与 {@link #estimateTokens(String)} 同一系数.
     *
     * <p>对未截断的同一段内容，结果与 {@code estimateTokens(contentBuilder.toString())}
     * 完全一致（先按字符分类计数再代入公式，除法顺序不变）。</p>
     *
     * @param chineseChars 汉字字符数
     * @param otherChars   其余非空白字符数
     * @return 估算 token 数（非负）
     */
    private long estimateTokensFromCounts(final long chineseChars, final long otherChars) {
        return (long) Math.ceil(chineseChars / CHINESE_CHARS_PER_TOKEN
                + otherChars / ENGLISH_CHARS_PER_TOKEN);
    }

    /**
     * 记录用响应体：有界前缀 + 截断标记（如有），再按 call-history 上限截断.
     *
     * @param contentBuilder    累积缓冲（已按 maxContentChars 收紧）
     * @param contentTruncated  是否发生过累积截断
     * @return 记录用响应体
     */
    private String recordedResponseBody(final StringBuilder contentBuilder, final boolean contentTruncated) {
        final String body = contentTruncated
                ? contentBuilder + TRUNCATION_MARKER
                : contentBuilder.toString();
        return truncate(body);
    }

    /**
     * 累积内容字符上限（配置优先，缺省 {@link #DEFAULT_MAX_CONTENT_CHARS}）.
     */
    private int maxContentChars() {
        if (streamingSafety != null && streamingSafety.getMaxContentChars() > 0) {
            return streamingSafety.getMaxContentChars();
        }
        return DEFAULT_MAX_CONTENT_CHARS;
    }

    /**
     * 流式缓存字符预算（配置优先，缺省 {@link #DEFAULT_MAX_CACHE_CHARS}）.
     */
    private int maxCacheChars() {
        if (streamingSafety != null && streamingSafety.getMaxCacheChars() > 0) {
            return streamingSafety.getMaxCacheChars();
        }
        return DEFAULT_MAX_CACHE_CHARS;
    }

    /**
     * 上游块间空闲超时（配置优先；PT0S/负值表示禁用）.
     */
    private Duration idleTimeout() {
        if (streamingSafety != null && streamingSafety.getIdleTimeout() != null) {
            return streamingSafety.getIdleTimeout();
        }
        return DEFAULT_IDLE_TIMEOUT;
    }

    /**
     * #721：判断一个 SSE 块是否携带终止标记。
     *
     * <p>认三种形态：OpenAI 的 {@code [DONE]}、任意协议的 {@code finish_reason} 非空块（由
     * {@link #extractFinishReason} 单独处理）、Anthropic 的 {@code message_stop}。</p>
     *
     * @param chunk 原始 SSE 块（可能含 {@code data: } 前缀）
     * @return 是否携带终止标记
     */
    static boolean isTerminatorChunk(final String chunk) {
        if (chunk == null) {
            return false;
        }
        final String payload = chunk.startsWith("data: ") ? chunk.substring(6) : chunk;
        final String trimmed = payload.trim();
        return "[DONE]".equals(trimmed) || trimmed.contains("\"message_stop\"");
    }

    /**
     * v2.9.10: 从 SSE 块提取 finish_reason（用于流式缓存）.
     *
     * <p>与 {@code extractUsageAndContent} 独立，避免修改其签名破坏既有反射测试。
     *
     * @param chunk           原始 SSE 块（可能含 {@code data: } 前缀）
     * @param finishReasonRef finish_reason 累积引用（最后非 null 值生效）
     */
    private void extractFinishReason(final String chunk, final AtomicReference<String> finishReasonRef) {
        try {
            String jsonPart = chunk;
            if (chunk.startsWith("data: ")) {
                jsonPart = chunk.substring(6);
            }
            if ("[DONE]".equals(jsonPart.trim())) {
                return;
            }
            JsonNode jsonNode = objectMapper.readTree(jsonPart);
            if (jsonNode.has("choices")) {
                JsonNode choices = jsonNode.get("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    JsonNode choice = choices.get(0);
                    if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
                        finishReasonRef.set(choice.get("finish_reason").asText());
                    }
                }
            }
        } catch (Exception e) {
            logger.trace("Failed to extract finish_reason from chunk: {}", e.getMessage());
        }
    }

    /**
     * v2.9.10: 写入流式响应缓存.
     *
     * <p>仅在完整成功流（doOnComplete 正常触发）时调用；
     * 键为空或数据不完整时不写。
     *
     * @param cacheKey         缓存键
     * @param cachedResponse   流式缓存值
     */
    void cacheStreamingResponse(final String cacheKey, final CachedStreamingResponse cachedResponse) {
        if (cacheKey == null || cachedResponse == null || responseCacheService == null) {
            return;
        }
        responseCacheService.store(cacheKey, cachedResponse);
    }

    // ==================== v2.9.2: 记录治理辅助方法 ====================

    /**
     * 解析当前记录级别（默认 METADATA_ONLY）
     */
    private RecordLevel resolveRecordLevel() {
        if (callHistoryProperties != null) {
            return callHistoryProperties.getRecordLevel();
        }
        return RecordLevel.METADATA_ONLY;
    }

    /**
     * 将对象序列化为 JSON 字符串并截断到 maxContentLength
     */
    private String serializeAndTruncate(final Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(obj);
            return truncate(json);
        } catch (Exception e) {
            logger.debug("序列化请求体失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 截断字符串到 maxContentLength
     */
    private String truncate(final String content) {
        if (content == null) {
            return null;
        }
        int maxLen = callHistoryProperties != null ? callHistoryProperties.getMaxContentLength() : 65536;
        if (content.length() > maxLen) {
            return content.substring(0, maxLen);
        }
        return content;
    }
}