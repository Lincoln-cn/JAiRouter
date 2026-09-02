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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.util.IpUtils;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitor.tracing.TracingConstants;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.monitor.tracing.interceptor.ControllerTracingInterceptor;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.cache.ResponseCacheService;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.loadbalancer.AffinityContextHolder;
import org.unreal.modelrouter.router.loadbalancer.AffinityKeyResolver;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import reactor.core.publisher.Mono;

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
     * v2.9.9: ServerWebExchange attribute key for storing the original request DTO.
     * Controller 在调用 handleRequest 前放入，handler 认证后读取以构建缓存键。
     */
    public static final String REQUEST_DTO_ATTRIBUTE = "JAIR_REQUEST_DTO";

    /**
     * v2.9.9: ServerHttpRequest attribute key for storing the response cache key.
     * handler 认证后生成并放入，processor 写缓存前读取（仿 API_KEY_ID_ATTRIBUTE 传递先例）。
     */
    public static final String CACHE_KEY_ATTRIBUTE = "JAIR_RESPONSE_CACHE_KEY";

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
            .filter(auth -> auth instanceof ApiKeyAuthentication)
            .cast(ApiKeyAuthentication.class)
            .flatMap(auth -> {
                String keyId = (String) auth.getPrincipal();
                if (keyId == null) {
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "API Key authentication required"));
                }

                // 检查服务类型权限
                if (!hasServicePermission(auth, endpoint.getServiceType())) {
                    logger.warn("API Key '{}' does not have permission for service type: {}",
                        keyId, endpoint.getServiceType());
                    return Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "API Key does not have permission for service: " + endpoint.getServiceType()));
                }

                exchange.getAttributes().put(API_KEY_ID_ATTRIBUTE, keyId);
                httpRequest.getAttributes().put(API_KEY_ID_ATTRIBUTE, keyId);

                // v2.9.9: 认证通过后为可缓存请求生成响应缓存键并放入请求属性
                // （供 handleWithInstanceAdapter 缓存读短路与 processor 写缓存使用）
                prepareResponseCacheKey(exchange, httpRequest, keyId, endpoint);

                return handleWithInstanceAdapter(
                    endpoint,
                    modelName,
                    authorization,
                    httpRequest,
                    tracingContext,
                    executor
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
            executor
        );
    }

    /**
     * 支持实例级适配器选择的服务请求处理器.
     */
    private Mono<ResponseEntity<?>> handleWithInstanceAdapter(
            final ServiceEndpoint endpoint,
            final String modelName,
            final String authorization,
            final ServerHttpRequest httpRequest,
            final TracingContext tracingContext,
            final ServiceRequestExecutor executor) {

        String clientIp = IpUtils.getClientIp(httpRequest);
        ServiceType serviceType = endpoint.getServiceType();

        // v2.9.0: 存储亲和性上下文原始组件(apiKeyId, clientIp, serviceType, modelName)
        // 在 ModelServiceRegistry 中按 sticky.scope 配置动态解析为正确粒度的亲和性键
        String apiKeyId = extractApiKeyId(httpRequest);
        AffinityContextHolder.set(apiKeyId, clientIp, serviceType.name(), modelName);

        // v2.8.5: 提取请求头用于规则引擎路由(仅用于规则匹配,不影响出站转发)
        Map<String, String> requestHeaders = new HashMap<>();
        if (httpRequest.getHeaders() != null) {
            httpRequest.getHeaders().forEach((key, values) -> {
                if (!values.isEmpty()) {
                    requestHeaders.put(key, values.get(0));
                }
            });
        }

        // 1. 选择实例
        ModelRouterProperties.ModelInstance selectedInstance;
        try {
            selectedInstance = selectInstance(serviceType, modelName, clientIp, tracingContext, requestHeaders);
        } catch (Exception e) {
            logger.error("Failed to select instance for service: {}, model: {}", serviceType, modelName, e);
            return Mono.error(e);
        } finally {
            // v2.9.0: 请求实例选择完成后清理亲和性上下文
            AffinityContextHolder.clear();
        }

        // v2.9.9: 响应缓存读 — selectInstance 之后(服务级限流已在 ModelServiceRegistry 内执行,
        // 每请求恰一次,命中短路不绕过限流)、获取适配器之前(命中直接跳过下游调用)
        ResponseEntity<?> cachedResponse = tryReadCachedResponse(httpRequest, serviceType, modelName);
        if (cachedResponse != null) {
            return Mono.just(cachedResponse);
        }

        // 2. 获取适配器
        ServiceCapability adapter;
        String adapterName;
        try {
            // v2.8.5: 规则引擎 TARGET_ADAPTER 动作 — 规则指定适配器名时按名取用
            String ruleAdapterName = registry.resolveRuleAdapterName(serviceType, modelName, clientIp, requestHeaders);
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
     * 从请求属性中提取 API Key ID
     * v2.9.0: 用于会话亲和性键解析
     */
    private String extractApiKeyId(final ServerHttpRequest httpRequest) {
        if (httpRequest == null) {
            return null;
        }
        Object keyId = httpRequest.getAttributes().get(API_KEY_ID_ATTRIBUTE);
        if (keyId instanceof String key && !key.isBlank()) {
            return key;
        }
        return null;
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
     * @param apiKeyId 认证后的 API Key ID
     * @param endpoint 服务端点
     */
    private void prepareResponseCacheKey(final ServerWebExchange exchange, final ServerHttpRequest httpRequest,
                                         final String apiKeyId, final ServiceEndpoint endpoint) {
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
        // 租户键: apiKeyId 缺省回退 clientIp（复用 AffinityKeyResolver 语义，防跨租户泄漏）
        String tenantKey = AffinityKeyResolver.resolveTenantKey(apiKeyId, IpUtils.getClientIp(httpRequest));
        if (tenantKey == null) {
            return;
        }
        String cacheKey = responseCacheService.buildKey(tenantKey, endpoint.getServiceType(), requestDto);
        if (cacheKey != null) {
            httpRequest.getAttributes().put(CACHE_KEY_ATTRIBUTE, cacheKey);
        }
    }

    /**
     * v2.9.9: 响应缓存读（在 selectInstance 之后调用）.
     *
     * <p>从 httpRequest attribute 读取缓存键并查询缓存；命中时构造与正常路径同构的
     * RouterResponse 200 JSON 响应（hit 指标由 ResponseCacheService 记录），未命中返回 null
     * 继续原流程。
     *
     * @param httpRequest HTTP 请求
     * @param serviceType 服务类型
     * @param modelName 模型名称
     * @return 缓存命中时的响应实体；未命中或缓存不可用时返回 null
     */
    private ResponseEntity<?> tryReadCachedResponse(final ServerHttpRequest httpRequest,
                                                    final ServiceType serviceType,
                                                    final String modelName) {
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
        RouterResponse<Object> body = RouterResponse.success(cached.get(), "请求成功");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
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
     * 检查 API Key 是否具有访问指定服务类型的权限.
     *
     * <p>权限检查逻辑：
     * <ul>
     *   <li>如果 API Key 具有 ADMIN 权限，允许访问所有服务</li>
     *   <li>否则检查是否具有对应服务类型的权限（如 ROLE_CHAT, ROLE_EMBEDDING 等）</li>
     * </ul>
     *
     * @param authentication API Key 认证对象
     * @param serviceType 服务类型
     * @return 是否具有权限
     */
    private boolean hasServicePermission(final ApiKeyAuthentication authentication, final ServiceType serviceType) {
        String requiredRole = "ROLE_" + serviceType.name().toUpperCase();

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String authorityName = authority.getAuthority();

            // ADMIN 权限允许访问所有服务
            if ("ROLE_ADMIN".equals(authorityName)) {
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
