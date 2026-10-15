package org.unreal.modelrouter.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import org.unreal.modelrouter.common.exception.SecurityAuthenticationException;
import org.unreal.modelrouter.auth.security.config.ExcludedPathsConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import reactor.core.publisher.Mono;

import org.unreal.modelrouter.common.exceptionhandler.V1ErrorBodyMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Security集成的认证过滤器
 * 只处理认证相关异常，其他业务异常交由全局异常处理器处理
 *
 * <p>v3.1 PR-4d.1：401 错误体（消息可能为中文，如「请求缺少认证信息…」）显式按
 * {@link StandardCharsets#UTF_8} 编码写出，避免平台默认字符集非 UTF-8 时乱码；
 * 响应形状与状态码不变。</p>
 *
 * <p>issue #404：状态码不再硬编码 401 —— 本仓 {@link AuthenticationException} 携带的状态透传
 * （认证链上唯一非 401 的码是 {@code RATE_LIMITED_API_KEY} = 429），错误体里的 {@code type}
 * 按该状态经 {@link V1ErrorBodyMapper#errorTypeOf(int)} 推导。401 时的输出逐字节不变。</p>
 *
 * <p>issue #420 ②：状态渲染不变，只把**日志口径**分流 —— 限流拒绝（429）打独立文案且不带栈，
 * 不再被记成「认证失败」（见 {@link #logAuthenticationFailure}）。</p>
 */
@Slf4j
public class SpringSecurityAuthenticationFilter implements WebFilter {

    private final SecurityProperties securityProperties;
    private final ServerAuthenticationConverter authenticationConverter;
    private final ReactiveAuthenticationManager authenticationManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpringSecurityAuthenticationFilter(
            final SecurityProperties securityProperties,
            final ServerAuthenticationConverter authenticationConverter,
            final ReactiveAuthenticationManager authenticationManager) {
        this.securityProperties = securityProperties;
        this.authenticationConverter = authenticationConverter;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        // 首先检查是否已经认证，如果已认证则直接继续执行过滤器链
        return requiresAuthentication(exchange)
                .flatMap(authRequired -> {
                    if (!authRequired) {
                        return chain.filter(exchange);
                    }

                    // 如果API Key和JWT都未启用，则跳过认证
                    if (!securityProperties.getApiKey().isEnabled() && !securityProperties.getJwt().isEnabled()) {
                        return chain.filter(exchange);
                    }

                    // 对于multipart请求，使用特殊的处理逻辑
                    if (isMultipartRequest(exchange)) {
                        log.debug("检测到multipart请求，使用特殊处理逻辑: {}", exchange.getRequest().getPath().value());
                        return handleMultipartAuthentication(exchange, chain);
                    }

                    // 转换请求为认证对象并执行认证
                    return performAuthentication(exchange, chain);
                });
    }

    /**
     * 执行实际的认证逻辑
     */
    private Mono<Void> performAuthentication(final ServerWebExchange exchange, final WebFilterChain chain) {
        // 凭据缺失判定必须挂在 convert() 上：只有 convert() 返回空序列才代表"未提供凭据"。
        // 若挂在外层（即包含 chain.filter(exchange) 的那个 Mono 上），由于 Mono<Void> 只
        // complete empty、永不 emit 值，认证成功的请求同样会命中该分支并写出伪 401，覆盖
        // 尚未提交的真实响应（典型表现：/v1/** 代理与流式调用恒回 AUTH_MISSING）。
        return authenticationConverter.convert(exchange)
                .switchIfEmpty(writeMissingCredentialsError(exchange,
                        "请求缺少认证信息，请提供API Key或JWT Token"))
                // 使用认证管理器进行实际认证
                .flatMap(authentication -> authenticationManager.authenticate(authentication))
                .flatMap(authenticated -> {
                    // 创建已认证的安全上下文，并在该安全上下文中继续执行过滤器链
                    SecurityContextImpl securityContext = new SecurityContextImpl(authenticated);
                    return chain.filter(exchange).contextWrite(
                            ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
                })
                // 只捕获认证相关异常，其它异常放行
                .onErrorResume(throwable -> {
                    if (isAuthException(throwable)) {
                        return handleAuthenticationError(exchange, throwable);
                    }
                    return Mono.error(throwable);
                });
    }

    /**
     * 检查是否应该进行认证
     */
    private Mono<Boolean> requiresAuthentication(final ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        // 使用ExcludedPathsConfig.AUTH_EXCLUDED_PATHS判断是否需要认证
        boolean isExcluded = ExcludedPathsConfig.isAuthExcluded(path);
        log.debug("认证检查: path={}, isExcluded={}, requiresAuth={}", path, isExcluded, !isExcluded);
        // 如果路径不在排除列表中，则需要认证
        return Mono.just(!isExcluded);
    }

    /**
     * 检查是否为multipart请求
     */
    private boolean isMultipartRequest(final ServerWebExchange exchange) {
        MediaType contentType = exchange.getRequest().getHeaders().getContentType();
        return contentType != null && contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA);
    }

    /**
     * 处理multipart请求的认证
     * 只捕获认证相关异常，其它异常放行
     */
    private Mono<Void> handleMultipartAuthentication(final ServerWebExchange exchange, final WebFilterChain chain) {
        log.debug("开始处理multipart请求认证: {}", exchange.getRequest().getPath().value());

        // 对于multipart请求，直接从请求头中提取认证信息，避免读取请求体。
        // 凭据缺失判定同样只能挂在 convert() 上（原因见 performAuthentication），且必须用
        // Mono.defer 延迟构造：辅助方法内含"装配期不应执行"的告警与响应写入逻辑，若作为
        // switchIfEmpty 的实参被提前求值，认证成功的请求也会被打出"缺少认证信息"告警。
        return authenticationConverter.convert(exchange)
                .switchIfEmpty(writeMissingCredentialsError(exchange,
                        "请求缺少认证信息，请提供X-API-Key或Jairouter_token"))
                .flatMap(authentication -> authenticationManager.authenticate(authentication)
                        .flatMap(authenticated -> continueWithSecurityContext(authenticated, exchange, chain))
                        // 只捕获认证相关异常，其它异常放行
                        .onErrorResume(throwable -> {
                            if (isAuthException(throwable)) {
                                return handleAuthenticationError(exchange, throwable);
                            }
                            return Mono.error(throwable);
                        }))
                // 只捕获认证相关异常，其它异常放行
                .onErrorResume(throwable -> {
                    if (isAuthException(throwable)) {
                        log.error("Multipart请求认证过程中发生认证异常: {}", throwable.getMessage(), throwable);
                        return handleAuthenticationError(exchange, throwable);
                    }
                    log.error("Multipart请求认证过程中发生非认证异常: {}", throwable.getMessage(), throwable);
                    return Mono.error(throwable);
                });
    }

    /**
     * 工具方法：判断是否为认证相关异常
     */
    private boolean isAuthException(final Throwable throwable) {
        return throwable instanceof AuthenticationException
                || throwable instanceof SecurityAuthenticationException;
    }

    /**
     * 在安全上下文中继续执行过滤器链
     * 优化版本：设置上下文后直接继续，不会重复进入认证流程
     */
    private Mono<Void> continueWithSecurityContext(
            final Authentication authenticated,
            final ServerWebExchange exchange,
            final WebFilterChain chain) {
        log.debug("设置认证上下文并继续执行过滤器链: {} - 用户: {}",
                exchange.getRequest().getPath().value(),
                authenticated.getName());

        SecurityContextImpl securityContext = new SecurityContextImpl(authenticated);
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
    }

    /**
     * 写出"缺少认证信息"的 401 响应，并返回 {@link Authentication} 型空序列.
     *
     * <p>该值作为 {@code switchIfEmpty} 的实参直接挂在 {@code convert()} 上，类型必须与
     * {@code Mono<Authentication>} 一致，故不能返回 {@code Mono<Void>}；写出 401 属订阅期
     * 副作用，空序列会让后续 {@code flatMap} 自然跳过。</p>
     *
     * <p>整体用 {@code Mono.defer} 包裹：否则 {@code switchIfEmpty} 的实参在装配期即被求值，
     * 认证成功的请求也会打出"缺少认证信息"告警。</p>
     *
     * @param message 401 错误消息（区分 multipart 与标准请求的既有文案）
     */
    private Mono<Authentication> writeMissingCredentialsError(
            final ServerWebExchange exchange, final String message) {
        return Mono.defer(() -> {
            log.warn("请求缺少认证信息: {}", exchange.getRequest().getPath().value());
            return createAuthenticationErrorResponse(exchange, message, ErrorCode.AUTH_MISSING.code(),
                    HttpStatus.UNAUTHORIZED)
                    .then(Mono.<Authentication>empty());
        });
    }

    /**
     * 处理认证错误
     *
     * <p><b>本方法是认证链状态的唯一渲染点</b>（issue #404 取证结论）：{@code ApiKeyService.validateApiKey}
     * 抛出的 {@link AuthenticationException} 经 {@code CustomReactiveAuthenticationManager} 回到本过滤器、
     * 被下面的 {@code onErrorResume} 吃掉，因此 {@code /v1/**}、{@code /api/**} 与其它需认证路径的状态码
     * 都由这里决定（两个 {@code @RestControllerAdvice} 在这条链上到不了）。</p>
     *
     * <ul>
     *   <li>{@link AuthenticationException}（本仓类型，携带状态）⇒ 状态 = {@link AuthenticationException#getHttpStatus()}，
     *       于是 {@code RATE_LIMITED_API_KEY} 的 429 得以透出；</li>
     *   <li>{@link SecurityAuthenticationException}（Spring Security 类型，不携带状态）与未知异常
     *       ⇒ 固定 {@link HttpStatus#UNAUTHORIZED}（既有行为）。</li>
     * </ul>
     */
    private Mono<Void> handleAuthenticationError(final ServerWebExchange exchange, final Throwable throwable) {
        final String path = exchange.getRequest().getPath().value();

        String message = "认证失败";
        String errorCode = ErrorCode.AUTH_FAILED.code();
        HttpStatus status = HttpStatus.UNAUTHORIZED;

        // 根据具体异常类型提供更具体的错误信息
        if (throwable instanceof AuthenticationException authException) {
            message = authException.getMessage();
            errorCode = authException.getErrorCode();
            if (authException.getHttpStatus() != null) {
                status = authException.getHttpStatus();
            }
        } else if (throwable instanceof SecurityAuthenticationException authException) {
            message = authException.getMessage();
            errorCode = authException.getErrorCode();
        } else {
            message = "认证过程中发生未知错误";
            errorCode = ErrorCode.AUTH_ERROR.code();
        }

        logAuthenticationFailure(path, status, errorCode, message, throwable);

        return createAuthenticationErrorResponse(exchange, message, errorCode, status);
    }

    /**
     * 认证失败的日志分流（issue #420 ②）.
     *
     * <p><b>为什么分流</b>：限流拒绝（{@code RATE_LIMITED_API_KEY} = 429）此前与真正的认证失败共用
     * {@code log.warn("认证失败: {}", path, throwable)}（带完整栈）⇒ ① 按该文案做的告警/统计会把**限流**
     * 计入**认证失败**、② 高频限流会按「每次一条栈」放大日志量。这是**日志口径**问题，响应体与状态码
     * 一概不受影响（状态的唯一渲染点是 {@link #createAuthenticationErrorResponse}）。</p>
     *
     * <p><b>口径</b>：限流的判据 = 状态 {@link HttpStatus#TOO_MANY_REQUESTS} <b>或</b>码为
     * {@link ErrorCode#RATE_LIMITED_API_KEY}（防「码对但状态在构造时掉了」这类半边异常仍被记成认证失败）；
     * 命中则用独立文案（点明 429 与码，**不带栈**）且仍是 {@code WARN} —— 限流尖峰对运维是可行动信号
     * （配额配置错误 / 滥用），降成 INFO/DEBUG 会把它藏起来，去掉栈即消除了放大效应。其余认证失败保持
     * 原样（同文案、同栈），既有告警口径与日志断言不变。</p>
     *
     * <p><b>新文案刻意不含「认证失败」四个字</b>：按旧文案做的告警/统计多是子串匹配，
     * 写成「非认证失败」仍会被它们命中 —— 那就等于没分流。</p>
     *
     * @param path      请求路径
     * @param status    客户端可见状态码
     * @param errorCode 错误码
     * @param message   错误消息（仅限流文案使用）
     * @param throwable 原始异常（仅非限流路径用于打印栈）
     */
    private void logAuthenticationFailure(final String path, final HttpStatus status, final String errorCode,
                                          final String message, final Throwable throwable) {
        final boolean rateLimited = status == HttpStatus.TOO_MANY_REQUESTS
                || ErrorCode.RATE_LIMITED_API_KEY.code().equals(errorCode);
        if (rateLimited) {
            log.warn("限流拒绝（429）: {} - {}: {}", path, errorCode, message);
        } else {
            log.warn("认证失败: {}", path, throwable);
        }
    }

    /**
     * 创建认证错误响应.
     *
     * <p>按请求路径选择错误体形状：{@code /v1/messages}（含子路径）→ Anthropic 形状；
     * 其余 {@code /v1/**} → OpenAI 形状；非 {@code /v1} 路径沿用既有硬编码形状。
     * 路径判定复用 {@link V1ErrorBodyMapper}（与 {@code SecurityExceptionHandler} 同源）。</p>
     *
     * <p>状态码由调用方传入（认证异常携带的状态，见 {@link #handleAuthenticationError}）；
     * 三种形状里的 {@code type} 一律按该状态经 {@link V1ErrorBodyMapper#errorTypeOf(int)} 推导
     * —— 与 {@code /v1} 协议面映射表**同源**，不再各写一份。401 的取值仍是
     * {@code authentication_error}，因此既有 401 形状逐字节不变；429 则得到
     * {@code rate_limit_error}（issue #404：状态改了、type 必须跟着改，否则自相矛盾）。</p>
     *
     * @param exchange  当前交换对象
     * @param message   错误消息
     * @param errorCode 错误码
     * @param status    客户端可见状态码（认证异常携带；缺省为 401）
     */
    private Mono<Void> createAuthenticationErrorResponse(
            final ServerWebExchange exchange,
            final String message,
            final String errorCode,
            final HttpStatus status) {
        // 延迟到订阅时再写响应：本方法含 setStatusCode 等副作用，若在装配期被求值，
        // 会把认证成功请求的响应也改写成 401（见 handleMultipartAuthentication 注释）。
        return Mono.defer(() -> {
            ServerHttpResponse response = exchange.getResponse();

            // 检查响应是否已经提交
            if (response.isCommitted()) {
                log.warn("响应已提交，无法创建认证错误响应");
                return Mono.empty();
            }

            response.setStatusCode(status);
            final String errorType = V1ErrorBodyMapper.errorTypeOf(status.value());
            final String path = exchange.getRequest().getPath().value();
            final String errorResponse;

            if (V1ErrorBodyMapper.isAnthropicPath(path)) {
                errorResponse = buildAnthropicAuthErrorBody(message, errorType);
            } else if (V1ErrorBodyMapper.isV1Path(path)) {
                errorResponse = buildOpenAiAuthErrorBody(errorCode, message, errorType);
            } else {
                // 非 /v1 路径：沿用既有硬编码形状（401 时逐字节不变）
                errorResponse = String.format(
                        "{\"error\": {\"message\": \"%s\", "
                                + "\"type\": \"%s\", "
                                + "\"code\": \"%s\"}}",
                        message.replace("\"", "\\\""),
                        errorType,
                        errorCode
                );
            }

            return response.writeWith(Mono.just(response.bufferFactory()
                            .wrap(errorResponse.getBytes(StandardCharsets.UTF_8))))
                    .onErrorResume(throwable -> {
                        log.error("写入认证错误响应时发生异常: {}", throwable.getMessage(), throwable);
                        return Mono.empty();
                    });
        });
    }

    /**
     * 构建 Anthropic 协议形状的认证错误体 JSON.
     *
     * @param message   错误消息
     * @param errorType 状态映射出的错误类型（401 → {@code authentication_error}，429 → {@code rate_limit_error}）
     * @return Anthropic 形状 JSON 文本
     */
    private String buildAnthropicAuthErrorBody(final String message, final String errorType) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "error");
        final Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", errorType);
        error.put("message", message);
        body.put("error", error);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("序列化 Anthropic 认证错误体失败", e);
            return "{\"type\":\"error\","
                    + "\"error\":{\"type\":\"" + errorType + "\","
                    + "\"message\":\"internal serialization error\"}}";
        }
    }

    /**
     * 构建 OpenAI 协议形状的认证错误体 JSON.
     *
     * @param errorCode 错误码（写入 code 字段）
     * @param message   错误消息
     * @param errorType 状态映射出的错误类型（401 → {@code authentication_error}，429 → {@code rate_limit_error}）
     * @return OpenAI 形状 JSON 文本
     */
    private String buildOpenAiAuthErrorBody(final String errorCode, final String message,
                                            final String errorType) {
        final Map<String, Object> body = new LinkedHashMap<>();
        final Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", message);
        error.put("type", errorType);
        error.put("code", errorCode);
        body.put("error", error);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("序列化 OpenAI 认证错误体失败", e);
            return "{\"error\":{\"message\":\"internal serialization error\","
                    + "\"type\":\"" + errorType + "\",\"code\":\"INTERNAL\"}}";
        }
    }
}