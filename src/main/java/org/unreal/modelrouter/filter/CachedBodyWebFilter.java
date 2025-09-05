package org.unreal.modelrouter.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 缓存请求体的 WebFilter - 改进版
 *
 * 解决 WebFlux 中请求体只能读取一次的问题，通过在最外层缓存 body 到内存，
 * 使得后续的重试机制和其他过滤器可以重复读取请求体。
 *
 * 特别优化了multipart请求的处理，确保边界信息不被破坏。
 *
 * 优先级设置为最高（比 TracingWebFilter 更高），确保在所有其他过滤器之前执行。
 *
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
public class CachedBodyWebFilter implements WebFilter, Ordered {

    private static final int MAX_CACHE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * 设置比 HIGHEST_PRECEDENCE 更高的优先级，确保在所有过滤器之前执行
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 只对有 body 的请求进行缓存处理
        if (!hasBody(request)) {
            log.debug("请求无body，跳过缓存: {}", request.getPath().value());
            return chain.filter(exchange);
        }

        // 检查是否为multipart请求
        boolean isMultipart = isMultipartRequest(request);

        if (isMultipart) {
            log.debug("检测到multipart请求，完全跳过 body 缓存: {}", request.getPath().value());
            // 绝对不能对 multipart 做任何缓存或 join
            return chain.filter(exchange);
        } else {
            log.debug("缓存标准请求体: {}", request.getPath().value());
            return handleStandardRequest(exchange, chain, request);
        }
    }

    /**
     * 处理multipart请求的缓存
     */
    private Mono<Void> handleMultipartRequest(ServerWebExchange exchange, WebFilterChain chain, ServerHttpRequest request) {
        // 对于multipart请求，我们需要特别小心处理边界信息
        return DataBufferUtils.join(request.getBody())
                .flatMap(dataBuffer -> {
                    try {
                        // 检查数据大小
                        int size = dataBuffer.readableByteCount();
                        if (size > MAX_CACHE_SIZE) {
                            log.warn("Multipart请求体过大({}MB)，跳过缓存", size / (1024 * 1024));
                            return chain.filter(exchange);
                        }

                        // 读取字节数据，保持原始格式
                        byte[] bytes = new byte[size];
                        dataBuffer.read(bytes);

                        log.debug("成功缓存multipart请求体，大小: {} bytes", bytes.length);

                        // 创建可重复读取的请求装饰器
                        ServerHttpRequest cachedRequest = new MultipartCachedRequestDecorator(request, bytes, exchange);

                        // 继续处理链
                        return chain.filter(exchange.mutate().request(cachedRequest).build());

                    } finally {
                        // 释放原始 DataBuffer
                        DataBufferUtils.release(dataBuffer);
                    }
                })
                .onErrorResume(error -> {
                    // 如果缓存失败，记录错误但继续处理
                    log.error("缓存multipart请求体失败: {} - {}", request.getPath().value(), error.getMessage());
                    return Mono.error(error);
                });
    }

    /**
     * 处理标准请求的缓存
     */
    private Mono<Void> handleStandardRequest(ServerWebExchange exchange, WebFilterChain chain, ServerHttpRequest request) {
        return DataBufferUtils.join(request.getBody())
                .flatMap(dataBuffer -> {
                    try {
                        int size = dataBuffer.readableByteCount();
                        if (size > MAX_CACHE_SIZE) {
                            log.warn("请求体过大({}MB)，跳过缓存", size / (1024 * 1024));
                            return chain.filter(exchange);
                        }
                        byte[] bytes = new byte[size];
                        dataBuffer.read(bytes);
                        ServerHttpRequest cachedRequest = new ServerHttpRequestDecorator(request) {
                            @Override
                            public Flux<DataBuffer> getBody() {
                                return Flux.just(exchange.getResponse()
                                        .bufferFactory()
                                        .wrap(bytes));
                            }
                        };
                        return chain.filter(exchange.mutate().request(cachedRequest).build());
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                })
                .onErrorResume(error -> {
                    log.warn("缓存请求体失败，直接抛出异常: {} - {}", request.getPath().value(), error.getMessage());
                    return Mono.error(error); // 让异常冒泡到全局异常处理器
                });
    }

    /**
     * 检查是否为multipart请求
     */
    private boolean isMultipartRequest(ServerHttpRequest request) {
        MediaType contentType = request.getHeaders().getContentType();
        return contentType != null &&
                (contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA) ||
                        contentType.getType().equals("multipart"));
    }

    /**
     * 检查请求是否有 body
     */
    private boolean hasBody(ServerHttpRequest request) {
        // 检查 Content-Length
        long contentLength = request.getHeaders().getContentLength();
        if (contentLength > 0) {
            return true;
        }

        // 检查 Transfer-Encoding
        String transferEncoding = request.getHeaders().getFirst("Transfer-Encoding");
        if ("chunked".equalsIgnoreCase(transferEncoding)) {
            return true;
        }

        // 检查 HTTP 方法，通常 POST、PUT、PATCH 可能有 body
        String method = request.getMethod() != null ? request.getMethod().name() : "";
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    /**
     * Multipart专用的缓存请求装饰器
     */
    private static class MultipartCachedRequestDecorator extends ServerHttpRequestDecorator {

        private final byte[] cachedBody;
        private final ServerWebExchange exchange;

        public MultipartCachedRequestDecorator(ServerHttpRequest delegate, byte[] cachedBody, ServerWebExchange exchange) {
            super(delegate);
            this.cachedBody = cachedBody;
            this.exchange = exchange;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            // 每次调用都创建新的DataBuffer，确保数据完整性
            return Flux.just(exchange.getResponse().bufferFactory().wrap(cachedBody.clone()));
        }
    }
}