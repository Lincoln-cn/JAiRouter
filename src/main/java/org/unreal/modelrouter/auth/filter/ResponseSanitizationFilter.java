package org.unreal.modelrouter.auth.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.exception.SanitizationException;
import org.unreal.modelrouter.common.util.ClientIpResolver;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.config.DataMaskingScope;
import org.unreal.modelrouter.auth.security.config.properties.SanitizationConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 响应数据脱敏过滤器（网关 HTTP JSON 响应路径，可选）。
 *
 * <p><b>设计边界</b>：聊天/调用历史/日志中的 PII 由
 * {@code SanitizationService.sanitizeForStorage} 在记录链路处理，不依赖本过滤器。
 * 本过滤器默认关闭（{@code jairouter.security.sanitization.response.enabled=false}），
 * 且 {@link ExcludedPathsConfig#isDataMaskExcluded(String)} 排除管理台 {@code /api/**}
 * 与运维端点。{@code /v1/**} 的<b>非流式 JSON</b> 响应自 issue #560（方案 C 第一阶段）起
 * <b>纳入</b>脱敏；其 SSE（{@code text/event-stream}）响应自 issue #560 第二阶段起由
 * {@link SseResponseSanitizer} <b>逐帧</b>脱敏（{@code response.sanitize-sse} 可关，
 * 关闭后按原内容类型判定跳过）。</p>
 */
@Slf4j
@Component
@Order(20) // 在请求处理之后执行
@ConditionalOnProperty(name = "jairouter.security.sanitization.response.enabled",
        havingValue = "true", matchIfMissing = false)
public class ResponseSanitizationFilter implements WebFilter {
    
    private final SanitizationService sanitizationService;
    private final SecurityAuditService auditService;
    private final SecurityProperties securityProperties;

    /** 生效范围缓存：配置热改后下一次请求自动重建（issue #560 A 档）。 */
    private volatile DataMaskingScope cachedScope;
    private volatile String cachedMode;
    private volatile List<String> cachedPatterns;
    
    @Autowired
    public ResponseSanitizationFilter(final SanitizationService sanitizationService,
                                    final SecurityAuditService auditService,
                                    final SecurityProperties securityProperties) {
        this.sanitizationService = sanitizationService;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
    }
    
    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // 运行时防御：即使 @ConditionalOnProperty 未阻止 Bean 创建，配置为 false 时也跳过
        if (!securityProperties.getSanitization().getResponse().isEnabled()) {
            return chain.filter(exchange);
        }
        
        // 跳过不需要脱敏的路径
        String path = request.getPath().value();
        boolean isExcluded = isExcludedPath(path);
        log.debug("ResponseSanitizationFilter处理路径: {}, 是否排除: {}", path, isExcluded);
        
        if (isExcluded) {
            log.debug("路径 {} 被排除，跳过响应脱敏处理", path);
            return chain.filter(exchange);
        }
        
        // 创建响应装饰器来拦截响应数据
        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            /**
             * SSE 在 WebFlux 里走 {@code writeAndFlushWith}（外层每次 flush 一个 Publisher），
             * 不是 {@code writeWith}。只覆盖 {@code writeWith} 会漏掉全部流式响应 ——
             * 健康状态这类 {@code Flux<ServerSentEvent>} 就属于这种情况。
             */
            @Override
            public Mono<Void> writeAndFlushWith(
                    final org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends DataBuffer>> body) {
                if (getDelegate().isCommitted()) {
                    return super.writeAndFlushWith(body);
                }
                final String contentType = resolveContentType(getResponseContentType(), request);
                if (!isSseContentType(contentType)
                        || !securityProperties.getSanitization().getResponse().isSanitizeSse()) {
                    return super.writeAndFlushWith(body);
                }
                log.debug("SSE 响应逐帧脱敏（writeAndFlushWith）: {}", request.getPath().value());
                final int maxLineBytes = (int) Math.min(resolveMaxBufferedBytes(), Integer.MAX_VALUE);
                // 同一个实例跨 flush 复用：一个事件可能被拆到两次 flush，残行必须延续
                final SseResponseSanitizer sseSanitizer = new SseResponseSanitizer(
                        sanitizationService,
                        originalResponse.bufferFactory(),
                        maxLineBytes,
                        message -> recordSanitizationEvent(request, contentType, false, message));
                final Flux<Flux<DataBuffer>> sanitized = Flux.from(body)
                        .map(chunk -> sseSanitizer.sanitizeSegment(Flux.from(chunk)))
                        .concatWith(Flux.just(sseSanitizer.tail()));
                return super.writeAndFlushWith(sanitized);
            }

            @Override
            public Mono<Void> writeWith(final org.reactivestreams.Publisher<? extends DataBuffer> body) {
                // 检查响应是否已经提交
                if (getDelegate().isCommitted()) {
                    log.warn("响应已提交，跳过脱敏处理");
                    return super.writeWith(body);
                }
                
                // 检查是否需要处理响应内容
                String contentType = resolveContentType(getResponseContentType(), request);
                // issue #560 第二阶段：SSE 是无限流，绝不能走下面的整段缓冲，必须按行流式处理
                if (isSseContentType(contentType)) {
                    if (!securityProperties.getSanitization().getResponse().isSanitizeSse()) {
                        log.debug("SSE 逐帧脱敏未开启，跳过: {}", request.getPath().value());
                        return super.writeWith(body);
                    }
                    log.debug("SSE 响应逐帧脱敏: {}", request.getPath().value());
                    // 单行上限复用 max-buffered-bytes：整段 JSON 只允许占这么多内存，单帧也不该更大
                    final int maxLineBytes = (int) Math.min(resolveMaxBufferedBytes(), Integer.MAX_VALUE);
                    final SseResponseSanitizer sseSanitizer = new SseResponseSanitizer(
                            sanitizationService,
                            originalResponse.bufferFactory(),
                            maxLineBytes,
                            message -> recordSanitizationEvent(request, contentType, false, message));
                    return super.writeWith(sseSanitizer.sanitize(Flux.from(body)));
                }
                if (!shouldSanitizeContentType(contentType)) {
                    return super.writeWith(body);
                }

                // issue #594：脱敏必须把响应体整体读进内存，先给缓冲设上限。
                // 长度已知且超限时直接原样放行（宁可漏脱敏，也不把响应读进堆）。
                final long maxBufferedBytes = resolveMaxBufferedBytes();
                final long declaredLength = getDelegate().getHeaders().getContentLength();
                if (declaredLength > maxBufferedBytes) {
                    log.warn("响应体长度 {} 字节超过脱敏缓冲上限 {} 字节，跳过脱敏并原样放行: {}",
                            declaredLength, maxBufferedBytes, request.getPath().value());
                    recordSanitizationEvent(request, contentType, false,
                            "响应体超过脱敏缓冲上限，已跳过脱敏: " + declaredLength + " > " + maxBufferedBytes);
                    return super.writeWith(body);
                }
                
                // 处理响应体脱敏
                // DataBufferUtils.join 的上限参数是 int，先夹到 int 范围（配置侧已限下限 1 KiB）
                final int joinLimit = (int) Math.min(maxBufferedBytes, Integer.MAX_VALUE);
                return DataBufferUtils.join(Flux.from(body), joinLimit)
                        .flatMap(dataBuffer -> {
                            try {
                                // 读取原始响应内容
                                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(bytes);
                                final byte[] originalBytes = bytes; // 创建final变量供lambda使用
                                String originalContent = new String(originalBytes, StandardCharsets.UTF_8);
                                
                                // 执行脱敏
                                return sanitizationService.sanitizeResponse(originalContent, contentType)
                                        .flatMap(sanitizedContent -> {
                                            // 记录脱敏操作
                                            recordSanitizationEvent(request, contentType, 
                                                                   !originalContent.equals(sanitizedContent));
                                            
                                            // 检查响应是否已经提交
                                            if (getDelegate().isCommitted()) {
                                                log.warn("响应已提交，无法写入脱敏后的内容");
                                                return Mono.empty();
                                            }
                                            
                                            // 创建新的响应体
                                            DataBufferFactory bufferFactory = originalResponse.bufferFactory();
                                            DataBuffer sanitizedBuffer = bufferFactory.wrap(
                                                    sanitizedContent.getBytes(StandardCharsets.UTF_8));
                                            
                                            return super.writeWith(Mono.just(sanitizedBuffer));
                                        })
                                        .onErrorResume(SanitizationException.class, ex -> {
                                            log.error("响应脱敏失败: {}", ex.getMessage(), ex);
                                            recordSanitizationEvent(request, contentType, false, ex.getMessage());
                                            
                                            // 根据配置决定是否返回原始内容还是错误
                                            if (securityProperties.getSanitization().getResponse().isFailOnError()) {
                                                return handleSanitizationFailure(ex);
                                            } else {
                                                log.warn("响应脱敏失败但配置为继续处理，返回原始内容: {}", ex.getMessage());
                                                // 检查响应是否已经提交
                                                if (getDelegate().isCommitted()) {
                                                    log.warn("响应已提交，无法写入原始内容");
                                                    return Mono.empty();
                                                }
                                                // 重新创建原始内容的DataBuffer
                                                DataBufferFactory bufferFactory = originalResponse.bufferFactory();
                                                DataBuffer originalBuffer = bufferFactory.wrap(originalBytes);
                                                return super.writeWith(Mono.just(originalBuffer));
                                            }
                                        })
                                        .onErrorResume(Exception.class, ex -> {
                                            // 记录更详细的错误信息
                                            String errorMessage = "未知错误: "
                                                    + (ex.getMessage() != null
                                                    ? ex.getMessage()
                                                    : ex.getClass().getName());
                                            if (ex.getCause() != null) {
                                                errorMessage += ", 原因: "
                                                        + (ex.getCause().getMessage() != null
                                                        ? ex.getCause().getMessage()
                                                        : ex.getCause().getClass().getName());
                                            }
                                            
                                            log.error("响应脱敏过程中发生未知错误: {}", errorMessage, ex);
                                            recordSanitizationEvent(request, contentType, false, errorMessage);
                                            
                                            // 发生未知错误时，根据配置决定处理方式
                                            if (securityProperties.getSanitization().getResponse().isFailOnError()) {
                                                return handleSanitizationFailure(
                                                        new SanitizationException("响应脱敏过程中发生未知错误", ex, 
                                                                ErrorCode.CONTENT_PROCESSING_FAILED.code()));
                                            } else {
                                                // 检查响应是否已经提交
                                                if (getDelegate().isCommitted()) {
                                                    log.warn("响应已提交，无法写入原始内容");
                                                    return Mono.empty();
                                                }
                                                // 返回原始内容
                                                DataBufferFactory bufferFactory = originalResponse.bufferFactory();
                                                DataBuffer originalBuffer = bufferFactory.wrap(originalBytes);
                                                return super.writeWith(Mono.just(originalBuffer));
                                            }
                                        });
                            } catch (Exception e) {
                                log.error("响应内容读取失败", e);
                                return Mono.error(new SanitizationException("响应内容读取失败", e, 
                                        ErrorCode.CONTENT_PROCESSING_FAILED.code()));
                            } finally {
                                // 确保DataBuffer被释放（唯一释放点，issue #594：错误分支不得重复释放）
                                DataBufferUtils.release(dataBuffer);
                            }
                        })
                        .onErrorResume(DataBufferLimitException.class, ex -> {
                            // 长度未知（chunked）且超限：原始字节已被消费，无法回放，
                            // 只能按脱敏失败终止，由中央异常处理器渲染错误体（绝不输出半截内容）。
                            log.error("响应体超过脱敏缓冲上限 {} 字节且未声明 Content-Length，"
                                            + "无法回放原始内容，终止响应: {}",
                                    maxBufferedBytes, request.getPath().value());
                            recordSanitizationEvent(request, contentType, false,
                                    "响应体超过脱敏缓冲上限且长度未知: " + maxBufferedBytes);
                            return Mono.error(new SanitizationException("响应体超过脱敏缓冲上限", ex,
                                    ErrorCode.CONTENT_PROCESSING_FAILED.code()));
                        })
                        // issue #594：body 已被 join 消费，空体时不能再把同一个 body 写一次
                        .switchIfEmpty(Mono.defer(() -> super.writeWith(Flux.empty())));
            }
            
            /**
             * 获取响应内容类型；尚未确定时返回 {@code null}。
             *
             * <p>不能回退成 {@code application/octet-stream}：那会让「编解码器稍后才写
             * {@code Content-Type}」的 SSE 响应在判定阶段看起来「既不是 JSON 也不是事件流」，
             * 直接被放行（健康状态 SSE 就是这样漏掉的）。</p>
             */
            private String getResponseContentType() {
                try {
                    MediaType mediaType = getHeaders().getContentType();
                    return mediaType != null ? mediaType.toString() : null;
                } catch (Exception e) {
                    log.warn("获取响应内容类型失败: {}", e.getMessage());
                    return null;
                }
            }
        };
        
        // 使用装饰后的响应继续处理链
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    /**
     * 检查路径是否落在「不脱敏」一侧。
     *
     * <p>判定交给 {@link DataMaskingScope}：默认仍用内置排除集合，配置了
     * {@code jairouter.security.sanitization.response.scope} 时按配置生效（issue #560 A 档）。
     * 生效范围按 (mode, patterns) 缓存，避免每个请求都重建匹配器。</p>
     */
    private boolean isExcludedPath(final String path) {
        return !currentScope().shouldSanitize(path);
    }

    private DataMaskingScope currentScope() {
        final SanitizationConfig.ResponseSanitization config = securityProperties.getSanitization().getResponse();
        final SanitizationConfig.Scope scopeConfig = config == null ? null : config.getScope();
        final String mode = scopeConfig == null ? null : scopeConfig.getMode();
        final List<String> patterns = scopeConfig == null || scopeConfig.getPatterns() == null
                ? null
                : List.copyOf(scopeConfig.getPatterns());

        final DataMaskingScope cached = this.cachedScope;
        if (cached != null
                && java.util.Objects.equals(mode, this.cachedMode)
                && java.util.Objects.equals(patterns, this.cachedPatterns)) {
            return cached;
        }

        final DataMaskingScope rebuilt = DataMaskingScope.from(config);
        this.cachedMode = mode;
        this.cachedPatterns = patterns;
        this.cachedScope = rebuilt;
        if (rebuilt.warning() != null) {
            log.warn("{}", rebuilt.warning());
        }
        return rebuilt;
    }

    /**
     * 响应缓冲上限（issue #594）。
     *
     * <p>取配置值并夹到下限 1 KiB —— 配置成 0 或负数时不能让上限失效（那正是要防的 OOM 场景）。</p>
     */
    private long resolveMaxBufferedBytes() {
        final SanitizationConfig.ResponseSanitization config = securityProperties.getSanitization().getResponse();
        final long configured = config == null ? 0L : config.getMaxBufferedBytes();
        return Math.max(1024L, configured);
    }

    /**
     * 检查是否应该对该内容类型进行脱敏
     */
    private boolean shouldSanitizeContentType(final String contentType) {
        if (contentType == null) {
            return false;
        }
        
        // 只处理JSON类型的内容
        return contentType.startsWith("application/json");
    }

    /**
     * 解析响应的有效内容类型。
     *
     * <p>SSE 有一类写法（控制器返回 {@code Flux<ServerSentEvent<T>>}、未显式声明 produces）由
     * 编解码器在写响应时才设置 {@code Content-Type}，此时装饰器拿到的响应头还没带类型 ——
     * 只看响应头会把这类流式响应误判成「未知类型」而放过。因此再回退看请求的
     * {@code Accept: text/event-stream}：客户端要的就是事件流，按 SSE 处理语义一致。</p>
     */
    private String resolveContentType(final String responseType, final ServerHttpRequest request) {
        if (responseType != null && !responseType.isBlank()) {
            return responseType;
        }
        final String accept = request.getHeaders().getFirst(HttpHeaders.ACCEPT);
        return isSseContentType(accept) ? accept : null;
    }

    /**
     * 是否为 SSE 响应（{@code text/event-stream}）。
     */
    private boolean isSseContentType(final String contentType) {
        return contentType != null && contentType.startsWith("text/event-stream");
    }
    
    /**
     * 记录脱敏事件
     */
    private void recordSanitizationEvent(
            final ServerHttpRequest request, final String contentType,
            final boolean sanitized) {
        recordSanitizationEvent(request, contentType, sanitized, null);
    }
    
    /**
     * 记录脱敏事件（带错误信息）
     */
    private void recordSanitizationEvent(
            final ServerHttpRequest request,
            final String contentType,
            final boolean sanitized,
            final String errorMessage) {
        if (!securityProperties.getSanitization().getResponse().isLogSanitization()) {
            return;
        }
        
        try {
            SecurityAuditEvent.SecurityAuditEventBuilder eventBuilder = SecurityAuditEvent.builder()
                    .eventType(AuditEventType.RESPONSE_SANITIZATION.name())
                    .userId("system") // 响应脱敏是系统行为
                    .clientIp(getClientIp(request))
                    .userAgent(getUserAgent(request))
                    .timestamp(LocalDateTime.now())
                    .resource(request.getPath().value())
                    .action("SANITIZE_RESPONSE")
                    .success(sanitized && errorMessage == null);
            
            if (errorMessage != null) {
                eventBuilder.failureReason(errorMessage);
            }
            
            // 添加额外信息
            eventBuilder.additionalData(java.util.Map.of(
                    "contentType", contentType,
                    "sanitized", sanitized,
                    "method", request.getMethod().name()
            ));
            
            SecurityAuditEvent event = eventBuilder.build();
            
            auditService.recordEvent(event).subscribe(
                    unused -> log.debug("记录响应脱敏审计事件成功"),
                    error -> log.error("记录响应脱敏审计事件失败", error)
            );
        } catch (Exception e) {
            log.error("创建响应脱敏审计事件失败", e);
        }
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIp(final ServerHttpRequest request) {
        // #151：转发头可被任意客户端伪造，信任决策统一交给共享 resolver——
        // 仅当直连对端在 trusted-proxies 中时才采信，并按右向左走跳取真实客户端。
        return ClientIpResolver.resolve(request);
    }
    
    /**
     * 获取用户代理
     */
    private String getUserAgent(final ServerHttpRequest request) {
        return request.getHeaders().getFirst("User-Agent");
    }
    
    /**
     * 终止请求，把错误体交给中央异常处理器（issue #317）。
     *
     * <p><b>本方法只负责终止，不负责响应体。</b>返回的 {@link SanitizationException} 会逃出 WebFilter 链，
     * 由 {@code ReactiveGlobalExceptionHandler} 按请求路径渲染：{@code /v1/**} 走客户端协议形状
     * （经 {@code V1ErrorBodyMapper}），其余路径走 {@code RouterResponse}；两条路径的消息都被固定为
     * 「数据处理失败」，**协议面不得暴露脱敏细节**（该形状由 {@code SecurityExceptionHandlerTest} 的断言守着）。</p>
     *
     * <p>⚠️ 不要再让本方法自己拼错误体（这里此前算过一个 {@code errorResponse} 却从未使用）：那样会
     * ① 把 {@code ex.getMessage()} 直接发给客户端 —— 违反上面那条已测契约；
     * ② 产出第三套既非 OpenAI/Anthropic 协议形状、也非 {@code RouterResponse} 的形状，客户端解析不了；
     * ③ 绕过中央处理器上的 {@code ErrorTracker} 记录与 {@code isCommitted} 竞态守卫。</p>
     *
     * @param ex 脱敏失败异常
     * @return 以该异常终止的 {@code Mono}（不写任何响应体）
     */
    private Mono<Void> handleSanitizationFailure(final SanitizationException ex) {
        return Mono.error(ex);
    }
}
