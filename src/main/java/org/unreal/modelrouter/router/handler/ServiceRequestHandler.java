/*
 * Copyright 2024 JAiRouter Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.router.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;
import org.unreal.modelrouter.auth.security.quota.QuotaEnforcementService;
import org.unreal.modelrouter.auth.security.quota.QuotaLimitViolation;
import org.unreal.modelrouter.auth.security.quota.QuotaTokenEstimator;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.util.IpUtils;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitor.tracing.TracingConstants;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.monitor.tracing.interceptor.ControllerTracingInterceptor;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.cache.CachedStreamingResponse;
import org.unreal.modelrouter.router.cache.ResponseCacheService;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.ratelimit.RateLimitManager;
import org.unreal.modelrouter.router.ratelimit.ServiceRateLimitHolder;
import org.unreal.modelrouter.router.loadbalancer.AffinityContextHolder;
import org.unreal.modelrouter.router.loadbalancer.AffinityKeyResolver;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 通用服务请求处理器.
 *
 * <p>封装所有服务端点的通用处理逻辑，包括：
 * <ul>
 *   <li>服务健康状态检查</li>
 *   <li>实例选择与负载均衡</li>
 *   <li>适配器获取与调用</li>
 *   <li>追踪信息记录</li>
 *   <li>指标收集</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since 2.10.0
 */
@Component
public class ServiceRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRequestHandler.class);

    /**
     * ServerWebExchange attribute key for storing the authenticated API Key ID.
     */
    public static final String API_KEY_ID_ATTRIBUTE = "API_KEY_ID";

    /**
     * issue #77: 请求属性 key —— 解析后的调用主体标识.
     *
     * <p>API Key 调用 = {@code keyId}；控制台 JWT 登录态 = {@code jwt:<用户名>}。仅供粘性路由键
     * 与响应缓存租户键使用（避免控制台用户间共用缓存租户）。</p>
     *
     * <p>JWT 身份<b>不</b>写入 {@link #API_KEY_ID_ATTRIBUTE}：该属性会被用量归因链路
     * （{@code TokenUsageExtractor} / {@code StreamingRequestProcessor}）当作 API Key ID
     * 读取，而 JWT 用户名在 API Key 存储中不存在，会产生无意义的 WARN 与错误归属。</p>
     */
    public static final String CALLER_ID_ATTRIBUTE = "JAIR_CALLER_ID";

    /**
     * issue #77: JWT 调用主体标识前缀（与 API Key ID 的键空间隔离）。
     */
    private static final String JWT_CALLER_ID_PREFIX = "jwt:";

    /**
     * issue #77: 控制台 AI 面准入权限码.
     *
     * <p>与 URL 层 RBAC 对 {@code /api/**} 的门槛同源（{@code PermissionRuleRegistry} 的
     * {@code ai:playground:use}）。控制台登录态不再要求 {@code ROLE_<SERVICE>}，否则非 ADMIN
     * 角色（持有该权限码但无服务角色）使用 Playground 会被判 403。</p>
     */
    private static final String AI_PLAYGROUND_PERMISSION = "ai:playground:use";

    /**
     * ADMIN 角色 authority（JwtAuthentication/ApiKeyAuthentication 均以 {@code ROLE_} 前缀写入）。
     */
    private static final String ROLE_ADMIN_AUTHORITY = "ROLE_ADMIN";

    /**
     * v2.9.9: ServerWebExchange attribute key for storing the original request DTO.
     * Controller 在调用 handleRequest 前放入，handler 认证后读取以构建缓存键。
     */
    public static final String REQUEST_DTO_ATTRIBUTE = "JAIR_REQUEST_DTO";

    /**
     * v2.9.9: ServerHttpRequest attribute key for storing the response cache key.
     * handler 认证后生成并放入，processor 写缓存前读取（仿 API_KEY_ID_ATTRIBUTE 传递先例）。
     */
    public static final String CACHE_KEY_ATTRIBUTE = "JAIR_RESPONSE_CACHE_KEY";

    /**
     * v3.1: OpenAI 原生响应标记（{@code /v1} 原生面）.
     *
     * <p>由 {@code OpenAiNativeController} 在 exchange 上置为 {@link Boolean#TRUE}。非流式处理器
     * 读到该标记时不再包 {@code RouterResponse}，直接返回下游原生 JSON，使 {@code /v1/**} 可直接
     * 作为 OpenAI SDK / LangChain / LlamaIndex 的 {@code base_url}；{@code /api/v1/**}（控制台面）
     * 不受影响。</p>
     */
    public static final String NATIVE_RESPONSE_ATTRIBUTE = "JAIR_NATIVE_RESPONSE";

    /**
     * v3.1 PR-2: 429 响应头 — 本次命中的限额值（{@code ApiKey} 限额配置值）。
     */
    public static final String QUOTA_HEADER_LIMIT = "X-Quota-Limit";

    /**
     * v3.1 PR-2: 429 响应头 — 判定时该窗口剩余额度（非负钳制）。
     */
    public static final String QUOTA_HEADER_REMAINING = "X-Quota-Remaining";

    /**
     * v3.1 PR-2: 429 响应头 — 被命中的窗口（{@code DAY} / {@code MINUTE}）。
     */
    public static final String QUOTA_HEADER_WINDOW = "X-Quota-Window";

    private final AdapterRegistry adapterRegistry;
    private final ModelServiceRegistry registry;
    private final ServiceStateManager serviceStateManager;
    private final MetricsCollector metricsCollector;
    private final ControllerTracingInterceptor tracingInterceptor;

    /**
     * v2.9.9: 响应缓存门面（可选注入，监控/缓存未装配时为空则跳过缓存路径）
     */
    @Autowired(required = false)
    private ResponseCacheService responseCacheService;

    /**
     * v2.9.10: 限流管理器（可选注入，用于缓存命中前服务级限流预扣）。
     * 未注入时缓存路径不执行预扣——限流由 selectInstance 内部执行（行为与现状一致）。
     */
    @Autowired(required = false)
    private RateLimitManager rateLimitManager;

    /**
     * v3.1 PR-2: 配额限额判定服务（可选注入）。
     *
     * <p>未注入或账本未启用（{@code jairouter.quota.enabled=false}，默认）时不做任何判定、
     * 不记账、不设置响应头——请求行为与 v3.0.x 完全一致。判定开启且超限时返回 429 +
     * {@code Retry-After} / {@code X-Quota-*} 响应头（响应体沿用全局异常处理器的既有错误格式）。</p>
     */
    @Autowired(required = false)
    private QuotaEnforcementService quotaEnforcementService;

    /**
     * v3.1 PR-4c: 全局 ObjectMapper（可选注入）。
     *
     * <p>仅用于「原生面（{@code /v1/**}）+ 响应缓存命中」时把缓存值序列化为<b>原生 JSON</b>
     * （而非 {@code RouterResponse} 包裹体）。未注入（如直接 new 的单测）时该分支退化为
     * 既有包裹行为，控制台面（{@code /api/**}）行为完全不受影响。</p>
     */
    @Autowired(required = false)
    private ObjectMapper objectMapper;

    /**
     * 构造函数.
     *
     * @param adapterRegistry 适配器注册表
     * @param registry 模型服务注册表
     * @param serviceStateManager 服务状态管理器
     * @param metricsCollector 指标收集器（可选）
     * @param tracingInterceptor 追踪拦截器（可选）
     */
    public ServiceRequestHandler(
            final AdapterRegistry adapterRegistry,
            final ModelServiceRegistry registry,
            final ServiceStateManager serviceStateManager,
            @Autowired(required = false) final MetricsCollector metricsCollector,
            @Autowired(required = false) final ControllerTracingInterceptor tracingInterceptor) {
        this.adapterRegistry = adapterRegistry;
        this.registry = registry;
        this.serviceStateManager = serviceStateManager;
        this.metricsCollector = metricsCollector;
        this.tracingInterceptor = tracingInterceptor;
    }

    /**
     * 处理服务请求（模板方法）.
     *
     * <p>统一的请求处理流程，适用于所有服务类型。
     *
     * @param endpoint 服务端点配置
     * @param modelName 模型名称
     * @param authorization 认证头信息
     * @param exchange ServerWebExchange对象
     * @param executor 服务请求执行器
     * @return 响应实体的Mono
     */
    public Mono<ResponseEntity<?>> handleRequest(
            final ServiceEndpoint endpoint,
            final String modelName,
            final String authorization,
            final ServerWebExchange exchange,
            final ServiceRequestExecutor executor) {

        ServerHttpRequest httpRequest = exchange.getRequest();
        TracingContext tracingContext = getTracingContext(exchange);

        return ReactiveSecurityContextHolder.getContext()
            .map(ctx -> ctx.getAuthentication())
            .flatMap(auth -> {
                // issue #77: 控制台 JWT 登录态与 API Key 同为合法调用主体，此处不再只认
                // ApiKeyAuthentication（曾用 instanceof 过滤，导致页面请求全部走不通）。
                String callerId = resolveCallerId(auth);
                if (callerId == null) {
                    logger.warn("请求缺少可识别的认证主体: {}",
                        httpRequest.getPath().value());
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authentication required"));
                }

                // 检查服务类型权限
                if (!hasServicePermission(auth, endpoint.getServiceType())) {
                    logger.warn("调用主体 '{}' 无权访问服务类型: {}",
                        callerId, endpoint.getServiceType());
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Caller does not have permission for service: " + endpoint.getServiceType()));
                }

                // API Key 调用保留原属性（用量归因链路依赖）；JWT 登录态只写 CALLER_ID_ATTRIBUTE，
                // 避免用户名被当作不存在的 API Key ID 记账。
                if (!isJwtCaller(callerId)) {
                    exchange.getAttributes().put(API_KEY_ID_ATTRIBUTE, callerId);
                    httpRequest.getAttributes().put(API_KEY_ID_ATTRIBUTE, callerId);
                }
                exchange.getAttributes().put(CALLER_ID_ATTRIBUTE, callerId);
                httpRequest.getAttributes().put(CALLER_ID_ATTRIBUTE, callerId);

                // v2.9.9: 认证通过后为可缓存请求生成响应缓存键并放入请求属性
                // （供 handleWithInstanceAdapter 缓存读短路与 processor 写缓存使用）
                prepareResponseCacheKey(exchange, httpRequest, callerId, endpoint);

                return handleWithInstanceAdapter(
                    endpoint,
                    modelName,
                    authorization,
                    httpRequest,
                    tracingContext,
                    executor,
                    exchange
                );
            });
    }

    /**
     * 处理服务请求（简化版本，不带ServerWebExchange）.
     *
     * @param endpoint 服务端点配置
     * @param modelName 模型名称
     * @param authorization 认证头信息
     * @param httpRequest HTTP请求对象
     * @param executor 服务请求执行器
     * @return 响应实体的Mono
     */
    public Mono<ResponseEntity<?>> handleRequest(
            final ServiceEndpoint endpoint,
            final String modelName,
            final String authorization,
            final ServerHttpRequest httpRequest,
            final ServiceRequestExecutor executor) {

        return handleWithInstanceAdapter(
            endpoint,
            modelName,
            authorization,
            httpRequest,
            null,
            executor,
            null
        );
    }

    /**
     * 支持实例级适配器选择的服务请求处理器.
     *
     * <p>v3.1 PR-2: 在 {@code selectInstance} 之前插入配额限额判定与预留；超限时以
     * {@link HttpStatus#TOO_MANY_REQUESTS} 短路（响应头 {@code Retry-After} /
     * {@code X-Quota-Limit} / {@code X-Quota-Remaining} / {@code X-Quota-Window}
     * 直接写在响应上，响应体由全局异常处理器按既有错误格式渲染）。
     * 判定发生在响应缓存读之后，因此缓存命中不消耗配额、也不产生预留凭据。</p>
     *
     * @param exchange 原始交换对象（读取原始 DTO 属性、写 429 响应头），可为 {@code null}
     */
    private Mono<ResponseEntity<?>> handleWithInstanceAdapter(
            final ServiceEndpoint endpoint,
            final String modelName,
            final String authorization,
            final ServerHttpRequest httpRequest,
            final TracingContext tracingContext,
            final ServiceRequestExecutor executor,
            final ServerWebExchange exchange) {

        // 限流/缓存/配额/实例选择均为同步逻辑（含可能的 JPA/Redis block），订阅时放到
        // boundedElastic，避免在 Netty EventLoop 上执行并卡死整条 IO 线程
        return Mono.defer(() -> handleWithInstanceAdapterSync(
                        endpoint, modelName, authorization, httpRequest,
                        tracingContext, executor, exchange))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<ResponseEntity<?>> handleWithInstanceAdapterSync(
            final ServiceEndpoint endpoint,
            final String modelName,
            final String authorization,
            final ServerHttpRequest httpRequest,
            final TracingContext tracingContext,
            final ServiceRequestExecutor executor,
            final ServerWebExchange exchange) {

        String clientIp = IpUtils.getClientIp(httpRequest);
        ServiceType serviceType = endpoint.getServiceType();

        // v2.9.0: 存储亲和性上下文原始组件(callerId, clientIp, serviceType, modelName)
        // 在 ModelServiceRegistry 中按 sticky.scope 配置动态解析为正确粒度的亲和性键
        String callerId = extractCallerId(httpRequest);
        AffinityContextHolder.set(callerId, clientIp, serviceType.name(), modelName);

        // v2.8.5: 提取请求头用于规则引擎路由(仅用于规则匹配,不影响出站转发)
        Map<String, String> requestHeaders = new HashMap<>();
        if (httpRequest.getHeaders() != null) {
            httpRequest.getHeaders().forEach((key, values) -> {
                if (!values.isEmpty()) {
                    requestHeaders.put(key, values.get(0));
                }
            });
        }

        // v2.9.10: 服务级限流预扣 + 缓存提前短路
        // 当缓存键存在时（缓存启用+请求可缓存），在 selectInstance 前：
        //   1. 预扣服务级限流（限流是硬边界，超限 429 优先于缓存——即使缓存有值也不放行）
        //   2. 标记已限流（selectInstance 内部跳过重复扣减，保持恰一次语义）
        //   3. 查缓存：命中直接返回（省去实例选择开销）；未命中继续 selectInstance
        // 无缓存键时（disabled/非确定性/无 DTO）：不预扣、不标记——selectInstance 内部限流（行为与现状完全一致）
        Object cacheKey = httpRequest != null
                ? httpRequest.getAttributes().get(CACHE_KEY_ATTRIBUTE) : null;
        boolean cachePathActive = cacheKey instanceof String key && !key.isBlank();

        if (cachePathActive && rateLimitManager != null) {
            if (!rateLimitManager.tryAcquireService(serviceType, clientIp, modelName)) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Service rate limit exceeded for service type '" + serviceType + "'"));
            }
            ServiceRateLimitHolder.markAcquired();
        }

        try {
            // v2.9.10: 响应缓存读 — 限流预扣后、selectInstance 之前(命中直接跳过实例选择)
            ResponseEntity<?> cachedResponse =
                    tryReadCachedResponse(httpRequest, serviceType, modelName, exchange);
            if (cachedResponse != null) {
                return Mono.just(cachedResponse);
            }

            // v3.1 PR-2: 配额限额判定 + 预留 — selectInstance 之前（缓存命中已先行短路）
            Optional<QuotaLimitViolation> quotaViolation =
                    reserveQuota(exchange, httpRequest, callerId);
            if (quotaViolation.isPresent()) {
                return rejectByQuota(exchange, quotaViolation.get());
            }

            // 1. 选择实例
            ModelRouterProperties.ModelInstance selectedInstance;
            try {
                selectedInstance = selectInstance(
                        serviceType, modelName, clientIp, tracingContext, requestHeaders);
            } catch (Exception e) {
                logger.error("Failed to select instance for service: {}, model: {}",
                        serviceType, modelName, e);
                return Mono.error(e);
            } finally {
                // v2.9.0: 请求实例选择完成后清理亲和性上下文
                AffinityContextHolder.clear();
            }

            // 2. 获取适配器
            ServiceCapability adapter;
            String adapterName;
            try {
                // v2.8.5: 规则引擎 TARGET_ADAPTER 动作 — 规则指定适配器名时按名取用
                String ruleAdapterName = registry.resolveRuleAdapterName(
                        serviceType, modelName, clientIp, requestHeaders);
                if (ruleAdapterName != null && !ruleAdapterName.isBlank()) {
                    ServiceCapability ruleAdapter = adapterRegistry.getAdapterByName(ruleAdapterName);
                    if (ruleAdapter != null) {
                        adapter = ruleAdapter;
                        adapterName = ruleAdapterName;
                        logger.info("Rule selected adapter '{}' for instance '{}' in service '{}'",
                                adapterName, selectedInstance.getName(), serviceType);
                    } else {
                        logger.warn("Rule target adapter '{}' not registered, fallback to instance adapter",
                                ruleAdapterName);
                        adapter = adapterRegistry.getAdapter(serviceType, selectedInstance);
                        adapterName = selectedInstance.getAdapter() != null
                                ? selectedInstance.getAdapter()
                                : "default";
                    }
                } else {
                    adapter = adapterRegistry.getAdapter(serviceType, selectedInstance);
                    adapterName = selectedInstance.getAdapter() != null
                        ? selectedInstance.getAdapter()
                        : "default";
                }
                logger.info("Selected adapter '{}' for instance '{}' in service '{}'",
                           adapterName, selectedInstance.getName(), serviceType);
            } catch (Exception e) {
                logger.error("Failed to get adapter for instance: {}", selectedInstance.getName(), e);
                return Mono.error(e);
            }

            // 3. 执行请求（带追踪和指标收集）
            return executeWithTracingAndMetrics(
                endpoint,
                adapter,
                adapterName,
                authorization,
                httpRequest,
                tracingContext,
                selectedInstance,
                executor
            );
        } finally {
            // v2.9.10: 清理服务级限流预扣标志（防 ThreadLocal 泄漏）
            ServiceRateLimitHolder.clear();
            // 缓存命中提前返回时 AffinityHolder 未被 selectInstance finally 清理，此处防御性清理
            AffinityContextHolder.clear();
        }
    }

    /**
     * 选择实例.
     */
    private ModelRouterProperties.ModelInstance selectInstance(
            final ServiceType serviceType,
            final String modelName,
            final String clientIp,
            final TracingContext tracingContext,
            final Map<String, String> requestHeaders) {

        ModelRouterProperties.ModelInstance instance = registry.selectInstance(
                serviceType, modelName, clientIp, requestHeaders);
        // 追踪实例选择
        if (tracingInterceptor != null && tracingContext != null && tracingContext.isActive()) {
            tracingInterceptor.traceInstanceSelection(tracingContext, serviceType, modelName, clientIp, instance);
        }

        return instance;
    }

    /**
     * 执行请求（带追踪和指标收集）.
     */
    private Mono<ResponseEntity<?>> executeWithTracingAndMetrics(
            final ServiceEndpoint endpoint,
            final ServiceCapability adapter,
            final String adapterName,
            final String authorization,
            final ServerHttpRequest httpRequest,
            final TracingContext tracingContext,
            final ModelRouterProperties.ModelInstance instance,
            final ServiceRequestExecutor requestExecutor) {

        ServiceType serviceType = endpoint.getServiceType();
        String serviceName = serviceType.name();
        long startTime = System.currentTimeMillis();
        String method = httpRequest.getMethod().name();

        // 检查服务健康状态
        if (!serviceStateManager.isServiceHealthy(serviceName)) {
            long duration = System.currentTimeMillis() - startTime;
            recordRequestMetrics(serviceName, method, duration, "503", 0, 0);
            return Mono.error(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                serviceName + " service is currently unavailable"
            ));
        }

        // 执行请求
        return executeRequest(adapter, authorization, httpRequest,
                tracingContext, adapterName, serviceType, instance, requestExecutor)
            .doOnSuccess(response -> {
                long duration = System.currentTimeMillis() - startTime;
                String status = getResponseStatus(response);
                long requestSize = estimateRequestSize(httpRequest);
                long responseSize = estimateResponseSize(response);
                recordRequestMetrics(serviceName, method, duration, status, requestSize, responseSize);
            })
            .doOnError(error -> {
                long duration = System.currentTimeMillis() - startTime;
                String status = getErrorStatus(error);
                long requestSize = estimateRequestSize(httpRequest);
                recordRequestMetrics(serviceName, method, duration, status, requestSize, 0);
            })
            .onErrorMap(UnsupportedOperationException.class, e ->
                new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "Service not supported by current adapter: " + e.getMessage()))
            .onErrorMap(IllegalArgumentException.class, e ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Adapter configuration error: " + e.getMessage()));
    }

    /**
     * 执行请求（带追踪包装）.
     */
    private Mono<ResponseEntity<?>> executeRequest(
            final ServiceCapability adapter,
            final String authorization,
            final ServerHttpRequest httpRequest,
            final TracingContext tracingContext,
            final String adapterName,
            final ServiceType serviceType,
            final ModelRouterProperties.ModelInstance instance,
            final ServiceRequestExecutor requestExecutor) {

        try {
            if (tracingInterceptor != null && tracingContext != null && tracingContext.isActive()) {
                return tracingInterceptor.traceAdapterCall(
                    tracingContext,
                    adapterName,
                    serviceType,
                    instance,
                    () -> {
                        try {
                            return requestExecutor.execute(adapter, authorization, httpRequest);
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    }
                );
            } else {
                return requestExecutor.execute(adapter, authorization, httpRequest);
            }
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    /**
     * 从请求属性中提取调用主体标识.
     *
     * <p>v2.9.0: 用于会话亲和性键解析；issue #77 起主体可能是 API Key ID 或
     * {@code jwt:<用户名>}（{@link #CALLER_ID_ATTRIBUTE} 优先，兼容仍只写
     * {@link #API_KEY_ID_ATTRIBUTE} 的调用路径）。</p>
     */
    private String extractCallerId(final ServerHttpRequest httpRequest) {
        if (httpRequest == null) {
            return null;
        }
        Object callerId = httpRequest.getAttributes().get(CALLER_ID_ATTRIBUTE);
        if (!(callerId instanceof String value) || value.isBlank()) {
            callerId = httpRequest.getAttributes().get(API_KEY_ID_ATTRIBUTE);
        }
        if (callerId instanceof String key && !key.isBlank()) {
            return key;
        }
        return null;
    }

    /**
     * 解析调用主体标识（issue #77）.
     *
     * <p>只接受网关实际产生的两种认证对象：API Key 取 {@code keyId}（原语义不变）；控制台 JWT
     * 登录态取用户名并加 {@link #JWT_CALLER_ID_PREFIX} 前缀，与 API Key ID 的键空间隔离。
     * 其他认证类型一律返回 {@code null}（拒绝），不因 principal 恰为字符串而放宽准入。</p>
     *
     * @param authentication 已认证主体
     * @return 调用主体标识；不可识别时返回 {@code null}
     */
    private static String resolveCallerId(final Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof String value) || value.isBlank()) {
            return null;
        }
        if (authentication instanceof JwtAuthentication) {
            return JWT_CALLER_ID_PREFIX + value;
        }
        return authentication instanceof ApiKeyAuthentication ? value : null;
    }

    /**
     * 判断调用主体是否来自控制台 JWT 登录态（issue #77）.
     */
    private static boolean isJwtCaller(final String callerId) {
        return callerId != null && callerId.startsWith(JWT_CALLER_ID_PREFIX);
    }

    /**
     * v3.1 PR-2: 配额限额判定与预留.
     *
     * <p>开关前置判断：配额服务未装配或账本未启用（默认）时立即返回，不估算 token、
     * 不访问账本、不挂载结算凭据——零行为变更。判定与记账过程中的异常由
     * {@link QuotaEnforcementService} 内部按 fail-open 吞掉，本方法不会抛出。</p>
     *
     * <p>估算输入取认证前 Controller 放入的原始请求 DTO（{@link #REQUEST_DTO_ATTRIBUTE}）；
     * 简化入口（无 exchange）或不可估算的服务类型按 0 处理，预留只计请求数。</p>
     *
     * @param exchange   原始交换对象（读取原始 DTO），可为 {@code null}
     * @param httpRequest HTTP 请求（挂载结算凭据）
     * @param callerId   调用主体标识（API Key ID 或 {@code jwt:<用户名>}）
     * @return 超限结果；放行（含降级放行）时为 {@link Optional#empty()}
     */
    private Optional<QuotaLimitViolation> reserveQuota(final ServerWebExchange exchange,
                                                       final ServerHttpRequest httpRequest,
                                                       final String callerId) {
        if (quotaEnforcementService == null || !quotaEnforcementService.isEnabled()) {
            return Optional.empty();
        }
        // issue #77: 控制台 JWT 登录态（页面请求，无 API Key）不参与 API Key 配额账本，
        // 既不预留也不产生结算凭据；调用历史仍按 jwt:<用户名> 记归属。
        if (isJwtCaller(callerId)) {
            return Optional.empty();
        }
        Object requestDto = exchange != null ? exchange.getAttribute(REQUEST_DTO_ATTRIBUTE) : null;
        long estimatedTokens = QuotaTokenEstimator.estimate(requestDto);
        return quotaEnforcementService.tryReserve(httpRequest, callerId, estimatedTokens);
    }

    /**
     * v3.1 PR-2: 配额超限响应（429 + Retry-After + X-Quota-*）.
     *
     * <p>响应头直接写在响应对象上（全局异常处理器只设置状态码与 Content-Type，不清空已有头），
     * 响应体沿用仓库既有 429 惯例——{@link ResponseStatusException} 交给
     * {@code ReactiveGlobalExceptionHandler} 渲染为 {@code RouterResponse.error(...)}。
     * 无 exchange（简化入口）时降级为“无响应头 + 相同状态码/响应体”。</p>
     *
     * @param exchange  原始交换对象，可为 {@code null}
     * @param violation 超限结果
     * @return 429 错误的 Mono
     */
    private Mono<ResponseEntity<?>> rejectByQuota(final ServerWebExchange exchange,
                                                  final QuotaLimitViolation violation) {
        if (exchange != null) {
            try {
                HttpHeaders headers = exchange.getResponse().getHeaders();
                headers.set(QUOTA_HEADER_LIMIT, String.valueOf(violation.limit()));
                headers.set(QUOTA_HEADER_REMAINING, String.valueOf(violation.remaining()));
                headers.set(QUOTA_HEADER_WINDOW, violation.window().name());
                headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(violation.retryAfterSeconds()));
            } catch (Exception e) {
                logger.debug("设置配额超限响应头失败: {}", e.getMessage());
            }
        }
        logger.warn("API Key 配额超限，返回 429: {}", violation.describe());
        return Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, violation.describe()));
    }

    /**
     * v2.9.9: 响应缓存键生成（读挂载前置准备）.
     *
     * <p>认证通过后调用：从 exchange attribute 读取 Controller 放入的原始 DTO；
     * 缓存启用 + 请求可缓存（确定性/非流式，由 ResponseCacheService 判定）时
     * 构建键并放入 httpRequest attribute，供处理器缓存读与 processor 写缓存使用。
     * 任一条件不满足则不生成键（缓存读写天然关闭）。
     *
     * @param exchange ServerWebExchange（含原始 DTO attribute）
     * @param httpRequest HTTP 请求（缓存键存放处）
     * @param callerId 调用主体标识（API Key ID 或 {@code jwt:<用户名>}）
     * @param endpoint 服务端点
     */
    private void prepareResponseCacheKey(final ServerWebExchange exchange, final ServerHttpRequest httpRequest,
                                         final String callerId, final ServiceEndpoint endpoint) {
        if (responseCacheService == null || !responseCacheService.isEnabled()) {
            return;
        }
        if (exchange == null || httpRequest == null) {
            return;
        }
        Object requestDto = exchange.getAttribute(REQUEST_DTO_ATTRIBUTE);
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
            httpRequest.getAttributes().put(CACHE_KEY_ATTRIBUTE, cacheKey);
        }
    }

    /**
     * v2.9.9/v2.9.10: 响应缓存读（在 selectInstance 之后调用）.
     *
     * <p>从 httpRequest attribute 读取缓存键并查询缓存；命中时按缓存值类型分支：
     * <ul>
     *   <li>{@link CachedStreamingResponse}（v2.9.10）：构造同构
     *       {@code text/event-stream + Flux<ServerSentEvent<String>>} 逐块 SSE 回放</li>
     *   <li>原生面（{@code /v1/**}，v3.1 PR-4c）：缓存值直接序列化为<b>原生 JSON</b>
     *       ——OpenAI/Anthropic 客户端拿不到 {@code RouterResponse} 包裹体</li>
     *   <li>控制台面（{@code /api/**}）：构造 {@code RouterResponse} 200 JSON 响应</li>
     * </ul>
     * 未命中返回 null 继续原流程。
     *
     * @param httpRequest HTTP 请求
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @param exchange 原始交换对象（读取原生面标记），可为 {@code null}
     * @return 缓存命中时的响应实体；未命中或缓存不可用时返回 null
     */
    private ResponseEntity<?> tryReadCachedResponse(final ServerHttpRequest httpRequest,
                                                    final ServiceType serviceType,
                                                    final String modelName,
                                                    final ServerWebExchange exchange) {
        if (responseCacheService == null || httpRequest == null) {
            return null;
        }
        Object cacheKey = httpRequest.getAttributes().get(CACHE_KEY_ATTRIBUTE);
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
     * v3.1 PR-4c: 是否原生面请求（{@code /v1/**}）.
     *
     * <p>标记由 {@code OpenAiNativeController} / {@code AnthropicMessagesController} 置入
     * exchange attribute（{@link #NATIVE_RESPONSE_ATTRIBUTE}）；同时兼容处理器侧读
     * {@code httpRequest} attribute 的既有语义（非流式处理器即从此处读取），
     * 两处任一命中即为原生面。</p>
     *
     * @param exchange 原始交换对象，可为 {@code null}
     * @param httpRequest HTTP 请求，可为 {@code null}
     * @return 原生面返回 true
     */
    private boolean isNativeResponse(final ServerWebExchange exchange, final ServerHttpRequest httpRequest) {
        if (exchange != null && Boolean.TRUE.equals(exchange.getAttribute(NATIVE_RESPONSE_ATTRIBUTE))) {
            return true;
        }
        return httpRequest != null
                && Boolean.TRUE.equals(httpRequest.getAttributes().get(NATIVE_RESPONSE_ATTRIBUTE));
    }

    /**
     * v3.1 PR-4c: 原生面缓存命中响应（原生 JSON 文本）.
     *
     * @param value 缓存值（下游转换后的数据：JSON 文本或结构化对象）
     * @return 原生 JSON 响应；无法序列化（未注入 ObjectMapper 或序列化失败）时返回 {@code null}
     *         ——由调用方回退包裹体，保证不会返回破损响应
     */
    private ResponseEntity<?> buildNativeCacheResponse(final Object value) {
        String json = toNativeJson(value);
        if (json == null) {
            return null;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    /**
     * v3.1 PR-4c: 缓存值 → 原生 JSON 文本.
     *
     * <p>与 {@code NonStreamingRequestProcessor#nativeJson} 同口径：已是合法 JSON 文本时原样返回
     * （保留下游原始字段/顺序），否则以 ObjectMapper 序列化；未注入 ObjectMapper 时仅文本可原样返回。</p>
     *
     * @param value 缓存值
     * @return JSON 文本；不可得返回 {@code null}
     */
    private String toNativeJson(final Object value) {
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
     * v2.9.10: 将流式缓存值构建为 SSE 响应.
     *
     * <p>逐块回放 {@link CachedStreamingResponse#chunks()} 中的变换后 data 串，
     * 每块包装为 {@code ServerSentEvent}，由 Spring WebFlux SSE 序列化器
     * 自动添加 {@code data:} 前缀和事件分隔，与正常流式路径出站结构一致。
     *
     * @param cachedResponse 流式缓存值
     * @return {@code text/event-stream} 响应实体
     */
    private ResponseEntity<?> buildStreamingCacheResponse(final CachedStreamingResponse cachedResponse) {
        Flux<ServerSentEvent<String>> flux = Flux.fromIterable(cachedResponse.chunks())
                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build());
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(flux);
    }

    /**
     * 从 ServerWebExchange 获取追踪上下文.
     */
    private TracingContext getTracingContext(final ServerWebExchange exchange) {
        if (exchange != null) {
            return exchange.getAttribute(TracingConstants.ContextKeys.TRACING_CONTEXT);
        }
        return null;
    }

    /**
     * 检查调用主体是否具有访问指定服务类型的权限.
     *
     * <p>权限检查逻辑：
     * <ul>
     *   <li>API Key：具有 ADMIN 权限则放行所有服务；否则要求对应服务类型权限
     *       （如 ROLE_CHAT, ROLE_EMBEDDING 等）</li>
     *   <li>控制台 JWT（issue #77）：ADMIN 或 {@code ai:playground:use} 放行
     *       （与 URL 层 RBAC 对 {@code /api/**} 的门槛同源）</li>
     * </ul>
     *
     * @param authentication 已认证主体（API Key 或 JWT）
     * @param serviceType 服务类型
     * @return 是否具有权限
     */
    private boolean hasServicePermission(final Authentication authentication, final ServiceType serviceType) {
        // issue #77: 控制台登录态（JWT）按控制台语义判定 —— 与 URL 层 RBAC 对 /api/** 的门槛同源
        // （ROLE_ADMIN 或 ai:playground:use），不要求 ROLE_<SERVICE>，否则持有该权限码但无服务
        // 角色的控制台用户使用 Playground 会被误判 403。
        if (authentication instanceof JwtAuthentication) {
            return hasAuthority(authentication, ROLE_ADMIN_AUTHORITY)
                    || hasAuthority(authentication, AI_PLAYGROUND_PERMISSION);
        }

        String requiredRole = "ROLE_" + serviceType.name().toUpperCase();

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String authorityName = authority.getAuthority();

            // ADMIN 权限允许访问所有服务
            if (ROLE_ADMIN_AUTHORITY.equals(authorityName)) {
                return true;
            }

            // 检查是否具有对应服务类型的权限
            if (requiredRole.equals(authorityName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断已认证主体是否携带指定 authority.
     */
    private static boolean hasAuthority(final Authentication authentication, final String authority) {
        if (authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
            if (authority.equals(grantedAuthority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 记录请求指标.
     */
    private void recordRequestMetrics(
            final String service,
            final String method,
            final long duration,
            final String status,
            final long requestSize,
            final long responseSize) {
        if (metricsCollector == null) {
            return;
        }
        try {
            metricsCollector.recordRequest(service, method, duration, status);
            if (requestSize > 0 || responseSize > 0) {
                metricsCollector.recordRequestSize(service, requestSize, responseSize);
            }
        } catch (Exception e) {
            logger.debug("Failed to record metrics: {}", e.getMessage());
        }
    }

    /**
     * 获取响应状态码.
     */
    private String getResponseStatus(final ResponseEntity<?> response) {
        if (response == null) {
            return "unknown";
        }
        return String.valueOf(response.getStatusCode().value());
    }

    /**
     * 获取错误状态码.
     */
    private String getErrorStatus(final Throwable error) {
        if (error instanceof ResponseStatusException) {
            return String.valueOf(((ResponseStatusException) error).getStatusCode().value());
        }
        if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException webEx) {
            return String.valueOf(webEx.getStatusCode().value());
        }
        if (error instanceof org.unreal.modelrouter.common.exception.DownstreamServiceException dsEx) {
            return String.valueOf(dsEx.getStatusCode().value());
        }
        return "500";
    }

    /**
     * 估算请求大小.
     */
    private long estimateRequestSize(final ServerHttpRequest request) {
        try {
            String contentLength = request.getHeaders().getFirst("Content-Length");
            if (contentLength != null) {
                return Long.parseLong(contentLength);
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 估算响应大小.
     */
    private long estimateResponseSize(final ResponseEntity<?> response) {
        try {
            if (response == null || response.getBody() == null) {
                return 0;
            }
            String body = response.getBody().toString();
            return body.getBytes().length;
        } catch (Exception e) {
            return 0;
        }
    }
}
