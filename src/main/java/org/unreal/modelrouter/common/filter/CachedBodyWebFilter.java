package org.unreal.modelrouter.common.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ResponseStatusException;
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
 * <p>超过 {@link #MAX_CACHE_SIZE} 的请求体不再「跳过缓存后放行」：body 此时已被
 * {@code DataBufferUtils.join} 消费，放行只会让下游读到空体。该分支以
 * {@link HttpStatus#PAYLOAD_TOO_LARGE}（413）终止，错误体由
 * {@link org.unreal.modelrouter.common.exceptionhandler.ReactiveGlobalExceptionHandler}
 * 统一渲染（{@code /v1/**} 走协议形状，其余路径走 {@code RouterResponse}）。（issue #681）</p>
 *
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
public class CachedBodyWebFilter implements WebFilter, Ordered {

    private static final int MAX_CACHE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * 请求体超过缓存阈值时返回给客户端的提示（issue #681）.
     */
    private static final String PAYLOAD_TOO_LARGE_MESSAGE =
            "请求体超过网关允许的最大大小（" + MAX_CACHE_SIZE / (1024 * 1024)
                    + "MB），请缩小请求体后重试";

    /**
     * 设置比 HIGHEST_PRECEDENCE 更高的优先级，确保在所有过滤器之前执行
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
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
            if (!hasBody(request)) {
                log.debug("请求无body，跳过缓存: {}", request.getPath().value());
                return chain.filter(exchange);
            }
            log.debug("缓存标准请求体: {}", request.getPath().value());
            return handleStandardRequest(exchange, chain, request);
        }
    }

    /**
     * 处理标准请求的缓存
     */
    private Mono<Void> handleStandardRequest(
            final ServerWebExchange exchange, final WebFilterChain chain,
            final ServerHttpRequest request) {
        return DataBufferUtils.join(request.getBody())
                .flatMap(dataBuffer -> {
                    try {
                        int size = dataBuffer.readableByteCount();
                        if (size > MAX_CACHE_SIZE) {
                            // issue #681：body 已在这里被 join 消费，不能再把原 exchange 交给 chain ——
                            // 下游只会读到空体，客户端拿到的是 400/500 而不是 413。
                            // 以 413 终止，错误体交给全局异常处理器统一渲染。
                            log.warn("请求体过大({}MB > {}MB)，返回 413: {}",
                                    size / (1024 * 1024), MAX_CACHE_SIZE / (1024 * 1024),
                                    request.getPath().value());
                            return Mono.error(new ResponseStatusException(
                                    HttpStatus.PAYLOAD_TOO_LARGE, PAYLOAD_TOO_LARGE_MESSAGE));
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
                        if (dataBuffer != null) {
                            DataBufferUtils.release(dataBuffer);
                        }
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
    private boolean isMultipartRequest(final ServerHttpRequest request) {
        MediaType contentType = request.getHeaders().getContentType();
        return contentType != null
                && (contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)
                        || contentType.getType().equals("multipart"));
    }

    /**
     * 检查请求是否有 body
     */
    private boolean hasBody(final ServerHttpRequest request) {
        // 检查 Content-Length
        long contentLength = request.getHeaders().getContentLength();
        if (contentLength > 0) {
            return true;
        }

        // 检查 Transfer-Encoding
        String transferEncoding = request.getHeaders().getFirst("Transfer-Encoding");
        return "chunked".equalsIgnoreCase(transferEncoding);

    }

}