package org.unreal.modelrouter.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 缓存请求体的 WebFilter
 * 
 * 解决 WebFlux 中请求体只能读取一次的问题，通过在最外层缓存 body 到内存，
 * 使得后续的重试机制和其他过滤器可以重复读取请求体。
 * 
 * 优先级设置为最高（比 TracingWebFilter 更高），确保在所有其他过滤器之前执行。
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class CachedBodyWebFilter implements WebFilter, Ordered {
    
    /**
     * 设置比 HIGHEST_PRECEDENCE 更高的优先级，确保在所有过滤器之前执行
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE - 1;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 只对有 body 的请求进行缓存处理
        if (!hasBody(request)) {
            return chain.filter(exchange);
        }
        
        // 缓存请求体到内存
        return DataBufferUtils.join(request.getBody())
            .flatMap(dataBuffer -> {
                try {
                    // 读取字节数据
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    
                    // 创建可重复读取的请求装饰器
                    ServerHttpRequest cachedRequest = new ServerHttpRequestDecorator(request) {
                        @Override
                        public Flux<DataBuffer> getBody() {
                            return Flux.just(exchange.getResponse()
                                    .bufferFactory()
                                    .wrap(bytes));
                        }
                    };
                    
                    // 继续处理链
                    return chain.filter(exchange.mutate().request(cachedRequest).build());
                    
                } finally {
                    // 释放原始 DataBuffer
                    DataBufferUtils.release(dataBuffer);
                }
            })
            .onErrorResume(error -> {
                // 如果缓存失败，记录警告但不影响主流程
                log.warn("缓存请求体失败，继续使用原始请求: {}", error.getMessage());
                return chain.filter(exchange);
            });
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
}