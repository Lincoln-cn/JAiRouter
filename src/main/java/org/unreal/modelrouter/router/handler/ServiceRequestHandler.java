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
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.common.util.IpUtils;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitor.tracing.TracingConstants;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.monitor.tracing.interceptor.ControllerTracingInterceptor;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;
import reactor.core.publisher.Mono;

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

    private final AdapterRegistry adapterRegistry;
    private final ModelServiceRegistry registry;
    private final ServiceStateManager serviceStateManager;
    private final MetricsCollector metricsCollector;
    private final ControllerTracingInterceptor tracingInterceptor;

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
            .map(auth -> ((ApiKeyAuthentication) auth).getPrincipal())
            .filter(principal -> principal != null)
            .doOnNext(keyId -> {
                exchange.getAttributes().put(API_KEY_ID_ATTRIBUTE, keyId);
                httpRequest.getAttributes().put(API_KEY_ID_ATTRIBUTE, keyId);
            })
            .then(Mono.defer(() -> handleWithInstanceAdapter(
                endpoint,
                modelName,
                authorization,
                httpRequest,
                tracingContext,
                executor
            )));
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

        // 1. 选择实例
        ModelRouterProperties.ModelInstance selectedInstance;
        try {
            selectedInstance = selectInstance(serviceType, modelName, clientIp, tracingContext);
        } catch (Exception e) {
            logger.error("Failed to select instance for service: {}, model: {}", serviceType, modelName, e);
            return Mono.error(e);
        }

        // 2. 获取适配器
        ServiceCapability adapter;
        String adapterName;
        try {
            adapter = adapterRegistry.getAdapter(serviceType, selectedInstance);
            adapterName = selectedInstance.getAdapter() != null
                ? selectedInstance.getAdapter()
                : "default";
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
            final TracingContext tracingContext) {

        ModelRouterProperties.ModelInstance instance = registry.selectInstance(serviceType, modelName, clientIp);

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
        return executeRequest(adapter, authorization, httpRequest, tracingContext, adapterName, serviceType, instance, requestExecutor)
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
     * 从 ServerWebExchange 获取追踪上下文.
     */
    private TracingContext getTracingContext(final ServerWebExchange exchange) {
        if (exchange != null) {
            return exchange.getAttribute(TracingConstants.ContextKeys.TRACING_CONTEXT);
        }
        return null;
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
