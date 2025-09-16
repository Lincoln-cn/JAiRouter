package org.unreal.modelrouter.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.exception.AuthenticationException;
import org.unreal.modelrouter.exception.SecurityAuthenticationException;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Spring Security集成的认证过滤器
 * 只处理认证相关异常，其他业务异常交由全局异常处理器处理
 */
@Slf4j
public class SpringSecurityAuthenticationFilter implements WebFilter {

    private final SecurityProperties securityProperties;
    private final ServerAuthenticationConverter authenticationConverter;
    private final ReactiveAuthenticationManager authenticationManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpringSecurityAuthenticationFilter(
            SecurityProperties securityProperties,
            ServerAuthenticationConverter authenticationConverter,
            ReactiveAuthenticationManager authenticationManager) {
        this.securityProperties = securityProperties;
        this.authenticationConverter = authenticationConverter;
        this.authenticationManager = authenticationManager;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 首先检查是否已经认证，如果已认证则直接继续执行过滤器链
        return ReactiveSecurityContextHolder.getContext()
                .cast(SecurityContext.class)
                .map(SecurityContext::getAuthentication)
                .filter(auth ->
                        auth != null && auth.isAuthenticated())
                .flatMap(auth -> {
                    log.debug("检测到已认证的上下文，跳过认证步骤: {}", exchange.getRequest().getPath().value());
                    return chain.filter(exchange);
                })
                .switchIfEmpty(
                        // 如果没有已认证的上下文，则进行认证检查
                        requiresAuthentication(exchange)
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
                                })
                );
    }

    /**
     * 执行实际的认证逻辑
     */
    private Mono<Void> performAuthentication(ServerWebExchange exchange, WebFilterChain chain) {
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
                                return chain.filter(exchange)
                                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
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
    private Mono<Boolean> requiresAuthentication(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        // 使用ExcludedPathsConfig.AUTH_EXCLUDED_PATHS判断是否需要认证
        boolean isExcluded = org.unreal.modelrouter.security.config.ExcludedPathsConfig.isAuthExcluded(path);
        // 如果路径不在排除列表中，则需要认证
        return Mono.just(!isExcluded);
    }

    /**
     * 检查是否为multipart请求
     */
    private boolean isMultipartRequest(ServerWebExchange exchange) {
        MediaType contentType = exchange.getRequest().getHeaders().getContentType();
        return contentType != null && contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA);
    }

    /**
     * 处理multipart请求的认证
     * 只捕获认证相关异常，其它异常放行
     */
    private Mono<Void> handleMultipartAuthentication(ServerWebExchange exchange, WebFilterChain chain) {
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
    private boolean isAuthException(Throwable throwable) {
        return throwable instanceof AuthenticationException
                || throwable instanceof SecurityAuthenticationException;
    }

    /**
     * 在安全上下文中继续执行过滤器链
     * 优化版本：设置上下文后直接继续，不会重复进入认证流程
     */
    private Mono<Void> continueWithSecurityContext(Authentication authenticated, ServerWebExchange exchange, WebFilterChain chain) {
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
    private Mono<Void> handleMissingAuthentication(ServerWebExchange exchange) {
        log.warn("请求缺少认证信息: {}", exchange.getRequest().getPath().value());
        return createAuthenticationErrorResponse(exchange,
                "请求缺少认证信息，请提供X-API-Key或Jairouter_token",
                "AUTH_MISSING");
    }

    /**
     * 处理认证错误
     */
    private Mono<Void> handleAuthenticationError(ServerWebExchange exchange, Throwable throwable) {
        log.warn("认证失败: {}", exchange.getRequest().getPath().value(), throwable);

        String message = "认证失败";
        String errorCode = "AUTH_FAILED";

        // 根据具体异常类型提供更具体的错误信息
        if (throwable instanceof AuthenticationException) {
            AuthenticationException authException = (AuthenticationException) throwable;
            message = authException.getMessage();
            errorCode = authException.getErrorCode();
        } else if (throwable instanceof SecurityAuthenticationException) {
            SecurityAuthenticationException authException = (SecurityAuthenticationException) throwable;
            message = authException.getMessage();
            errorCode = authException.getErrorCode();
        } else {
            message = "认证过程中发生未知错误";
            errorCode = "AUTH_ERROR";
        }

        return createAuthenticationErrorResponse(exchange, message, errorCode);
    }

    /**
     * 创建认证错误响应
     */
    private Mono<Void> createAuthenticationErrorResponse(ServerWebExchange exchange, String message, String errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        
        // 检查响应是否已经提交
        if (response.isCommitted()) {
            log.warn("响应已提交，无法创建认证错误响应");
            return Mono.empty();
        }
        
        response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
        String errorResponse = String.format(
                "{\"error\": {\"message\": \"%s\", \"type\": \"authentication_error\", \"code\": \"%s\"}}",
                message.replace("\"", "\\\""),
                errorCode
        );

        return response.writeWith(Mono.just(response.bufferFactory().wrap(errorResponse.getBytes())))
                .onErrorResume(throwable -> {
                    log.error("写入认证错误响应时发生异常: {}", throwable.getMessage(), throwable);
                    return Mono.empty();
                });
    }
}