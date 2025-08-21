package org.unreal.modelrouter.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.context.annotation.Conditional;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.monitoring.config.MonitoringEnabledCondition;
import org.unreal.modelrouter.monitoring.config.MonitoringProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebFlux指标拦截器
 * 拦截所有HTTP请求，收集请求指标、响应时间、请求大小和响应大小等信息
 */
@Component
@Conditional(MonitoringEnabledCondition.class)
public class WebFluxMetricsInterceptor implements WebFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(WebFluxMetricsInterceptor.class);

    private final MetricsCollector metricsCollector;
    private final MonitoringProperties monitoringProperties;
    private final SlowQueryDetector slowQueryDetector;

    public WebFluxMetricsInterceptor(MetricsCollector metricsCollector, 
                                   MonitoringProperties monitoringProperties,
                                   SlowQueryDetector slowQueryDetector) {
        this.metricsCollector = metricsCollector;
        this.monitoringProperties = monitoringProperties;
        this.slowQueryDetector = slowQueryDetector;
        logger.info("WebFluxMetricsInterceptor initialized");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 跳过非业务请求（如actuator端点）
        String path = exchange.getRequest().getPath().value();
        if (shouldSkipMetrics(path)) {
            return chain.filter(exchange);
        }

        // 记录请求开始时间
        long startTime = System.currentTimeMillis();
        
        // 获取请求信息
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String serviceName = extractServiceName(path);
        
        // 计算请求大小
        AtomicLong requestSize = new AtomicLong(0);
        calculateRequestSize(request, requestSize);

        // 创建响应装饰器来捕获响应大小
        AtomicLong responseSize = new AtomicLong(0);
        ServerHttpResponse decoratedResponse = new MetricsServerHttpResponseDecorator(
            exchange.getResponse(), responseSize);
        
        // 创建新的exchange
        ServerWebExchange decoratedExchange = exchange.mutate()
            .response(decoratedResponse)
            .build();

        return chain.filter(decoratedExchange)
            .doOnSuccess(aVoid -> recordMetrics(startTime, method, serviceName, 
                                              decoratedResponse.getStatusCode().value(), 
                                              requestSize.get(), responseSize.get(), path))
            .doOnError(throwable -> recordMetrics(startTime, method, serviceName, 
                                                500, requestSize.get(), responseSize.get(), path))
            .doOnCancel(() -> recordMetrics(startTime, method, serviceName, 
                                          499, requestSize.get(), responseSize.get(), path));
    }

    /**
     * 判断是否应该跳过指标收集
     */
    private boolean shouldSkipMetrics(String path) {
        // 跳过actuator端点
        if (path.startsWith("/actuator")) {
            return true;
        }
        
        // 跳过静态资源
        if (path.startsWith("/static") || path.startsWith("/css") || 
            path.startsWith("/js") || path.startsWith("/images")) {
            return true;
        }
        
        // 跳过favicon
        if (path.equals("/favicon.ico")) {
            return true;
        }
        
        return false;
    }

    /**
     * 从请求路径提取服务名称
     */
    private String extractServiceName(String path) {
        // 解析OpenAI兼容的API路径
        if (path.startsWith("/v1/")) {
            String[] pathParts = path.split("/");
            if (pathParts.length >= 3) {
                String endpoint = pathParts[2];
                switch (endpoint) {
                    case "chat":
                        return "chat";
                    case "embeddings":
                        return "embedding";
                    case "rerank":
                        return "rerank";
                    case "audio":
                        if (pathParts.length >= 4) {
                            return "speech".equals(pathParts[3]) ? "tts" : "stt";
                        }
                        return "audio";
                    case "images":
                        if (pathParts.length >= 4) {
                            return "generations".equals(pathParts[3]) ? "imgGen" : "imgEdit";
                        }
                        return "image";
                    default:
                        return endpoint;
                }
            }
        }
        
        // 对于其他路径，使用第一个路径段作为服务名
        String[] pathParts = path.split("/");
        if (pathParts.length >= 2) {
            return pathParts[1];
        }
        
        return "unknown";
    }

    /**
     * 计算请求大小
     */
    private void calculateRequestSize(ServerHttpRequest request, AtomicLong requestSize) {
        try {
            // 从Content-Length头获取请求大小
            String contentLength = request.getHeaders().getFirst("Content-Length");
            if (contentLength != null && !contentLength.isEmpty()) {
                requestSize.set(Long.parseLong(contentLength));
            } else {
                // 如果没有Content-Length，估算请求头大小
                long headerSize = request.getHeaders().entrySet().stream()
                    .mapToLong(entry -> entry.getKey().length() + 
                              entry.getValue().stream().mapToInt(String::length).sum() + 4) // +4 for ": " and "\r\n"
                    .sum();
                
                // 添加请求行大小
                String requestLine = request.getMethod().name() + " " + 
                                   request.getPath().value() + " HTTP/1.1";
                headerSize += requestLine.length() + 2; // +2 for "\r\n"
                
                requestSize.set(headerSize);
            }
        } catch (Exception e) {
            logger.debug("Failed to calculate request size: {}", e.getMessage());
            requestSize.set(0);
        }
    }

    /**
     * 记录指标
     */
    private void recordMetrics(long startTime, String method, String serviceName, 
                             int statusCode, long requestSize, long responseSize, String path) {
        try {
            long duration = System.currentTimeMillis() - startTime;
            String status = String.valueOf(statusCode);
            
            // 记录请求指标
            metricsCollector.recordRequest(serviceName, method, duration, status);
            
            // 记录请求和响应大小
            if (requestSize > 0 || responseSize > 0) {
                metricsCollector.recordRequestSize(serviceName, requestSize, responseSize);
            }
            
            // 检测慢查询
            Map<String, String> context = new HashMap<>();
            context.put("method", method);
            context.put("path", path);
            context.put("status", status);
            slowQueryDetector.detectSlowQuery(serviceName, duration, context);
            
            logger.debug("Recorded request metrics: service={}, method={}, duration={}ms, " +
                        "status={}, requestSize={}, responseSize={}", 
                        serviceName, method, duration, status, requestSize, responseSize);
        } catch (Exception e) {
            logger.warn("Failed to record request metrics: {}", e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        // 设置较高优先级，确保在其他过滤器之前执行
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    /**
     * 自定义响应装饰器，用于捕获响应大小
     */
    private static class MetricsServerHttpResponseDecorator extends ServerHttpResponseDecorator {
        
        private final AtomicLong responseSize;

        public MetricsServerHttpResponseDecorator(ServerHttpResponse delegate, AtomicLong responseSize) {
            super(delegate);
            this.responseSize = responseSize;
        }

        @Override
        public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
            return super.writeWith(
                Flux.from(body)
                    .doOnNext(dataBuffer -> {
                        // 累加响应数据大小
                        responseSize.addAndGet(dataBuffer.readableByteCount());
                    })
            );
        }

        @Override
        public Mono<Void> writeAndFlushWith(org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends DataBuffer>> body) {
            return super.writeAndFlushWith(
                Flux.from(body)
                    .map(publisher -> 
                        Flux.from(publisher)
                            .doOnNext(dataBuffer -> {
                                // 累加响应数据大小
                                responseSize.addAndGet(dataBuffer.readableByteCount());
                            })
                    )
            );
        }
    }
}