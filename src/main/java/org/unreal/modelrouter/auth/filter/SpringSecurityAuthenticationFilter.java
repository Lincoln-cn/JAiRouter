package org.unreal.modelrouter.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
        return authenticationConverter.convert(exchange)
                .flatMap(authentication -> {
                    // 使用认证管理器进行实际认证
                    if (authentication == null) {
                        return handleMissingAuthentication(exchange);
                    }

                    return authenticationManager.authenticate(authentication)
                            .flatMap(authenticated -> {
                                // 创建已认证的安全上下文
                                SecurityContextImpl securityContext = new SecurityContextImpl(authenticated);
                                // 在安全上下文中继续执行过滤器链
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
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 没有提供认证信息，返回401错误
                    log.warn("请求缺少认证信息: {}", exchange.getRequest().getPath().value());
                    return createAuthenticationErrorResponse(exchange,
                            "请求缺少认证信息，请提供API Key或JWT Token",
                            "AUTH_MISSING");
                }))
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

        // 对于multipart请求，直接从请求头中提取认证信息，避免读取请求体
        return authenticationConverter.convert(exchange)
                .flatMap(authentication -> {
                    if (authentication == null) {
                        return handleMissingAuthentication(exchange);
                    }

                    return authenticationManager.authenticate(authentication)
                            .flatMap(authenticated -> continueWithSecurityContext(authenticated, exchange, chain))
                            // 只捕获认证相关异常，其它异常放行
                            .onErrorResume(throwable -> {
                                if (isAuthException(throwable)) {
                                    return handleAuthenticationError(exchange, throwable);
                                }
                                return Mono.error(throwable);
                            });
                })
                .switchIfEmpty(handleMissingAuthentication(exchange))
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
     * 处理缺少认证信息的情况
     */
    private Mono<Void> handleMissingAuthentication(final ServerWebExchange exchange) {
        log.warn("请求缺少认证信息: {}", exchange.getRequest().getPath().value());
        return createAuthenticationErrorResponse(exchange,
                "请求缺少认证信息，请提供X-API-Key或Jairouter_token",
                "AUTH_MISSING");
    }

    /**
     * 处理认证错误
     */
    private Mono<Void> handleAuthenticationError(final ServerWebExchange exchange, final Throwable throwable) {
        log.warn("认证失败: {}", exchange.getRequest().getPath().value(), throwable);

        String message = "认证失败";
        String errorCode = "AUTH_FAILED";

        // 根据具体异常类型提供更具体的错误信息
        if (throwable instanceof AuthenticationException authException) {
            message = authException.getMessage();
            errorCode = authException.getErrorCode();
        } else if (throwable instanceof SecurityAuthenticationException authException) {
            message = authException.getMessage();
            errorCode = authException.getErrorCode();
        } else {
            message = "认证过程中发生未知错误";
            errorCode = "AUTH_ERROR";
        }

        return createAuthenticationErrorResponse(exchange, message, errorCode);
    }

    /**
     * 创建认证错误响应.
     *
     * <p>按请求路径选择错误体形状：{@code /v1/messages}（含子路径）→ Anthropic 形状；
     * 其余 {@code /v1/**} → OpenAI 形状；非 {@code /v1} 路径沿用既有硬编码形状。
     * 路径判定复用 {@link V1ErrorBodyMapper}（与 {@code SecurityExceptionHandler} 同源）。</p>
     */
    private Mono<Void> createAuthenticationErrorResponse(
            final ServerWebExchange exchange,
            final String message,
            final String errorCode) {
        ServerHttpResponse response = exchange.getResponse();

        // 检查响应是否已经提交
        if (response.isCommitted()) {
            log.warn("响应已提交，无法创建认证错误响应");
            return Mono.empty();
        }

        response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
        final String path = exchange.getRequest().getPath().value();
        final String errorResponse;

        if (V1ErrorBodyMapper.isAnthropicPath(path)) {
            errorResponse = buildAnthropicAuthErrorBody(message);
        } else if (V1ErrorBodyMapper.isV1Path(path)) {
            errorResponse = buildOpenAiAuthErrorBody(errorCode, message);
        } else {
            // 非 /v1 路径：沿用既有硬编码形状（逐字节不变）
            errorResponse = String.format(
                    "{\"error\": {\"message\": \"%s\", "
                            + "\"type\": \"authentication_error\", "
                            + "\"code\": \"%s\"}}",
                    message.replace("\"", "\\\""),
                    errorCode
            );
        }

        return response.writeWith(Mono.just(response.bufferFactory()
                        .wrap(errorResponse.getBytes(StandardCharsets.UTF_8))))
                .onErrorResume(throwable -> {
                    log.error("写入认证错误响应时发生异常: {}", throwable.getMessage(), throwable);
                    return Mono.empty();
                });
    }

    /**
     * 构建 Anthropic 协议形状的 401 认证错误体 JSON.
     *
     * @param message 错误消息
     * @return Anthropic 形状 JSON 文本
     */
    private String buildAnthropicAuthErrorBody(final String message) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "error");
        final Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", "authentication_error");
        error.put("message", message);
        body.put("error", error);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("序列化 Anthropic 认证错误体失败", e);
            return "{\"type\":\"error\","
                    + "\"error\":{\"type\":\"authentication_error\","
                    + "\"message\":\"internal serialization error\"}}";
        }
    }

    /**
     * 构建 OpenAI 协议形状的 401 认证错误体 JSON.
     *
     * @param errorCode 错误码（写入 code 字段）
     * @param message   错误消息
     * @return OpenAI 形状 JSON 文本
     */
    private String buildOpenAiAuthErrorBody(final String errorCode, final String message) {
        final Map<String, Object> body = new LinkedHashMap<>();
        final Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", message);
        error.put("type", "authentication_error");
        error.put("code", errorCode);
        body.put("error", error);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("序列化 OpenAI 认证错误体失败", e);
            return "{\"error\":{\"message\":\"internal serialization error\","
                    + "\"type\":\"authentication_error\",\"code\":\"INTERNAL\"}}";
        }
    }
}