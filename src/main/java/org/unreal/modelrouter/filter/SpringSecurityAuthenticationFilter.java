package org.unreal.modelrouter.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.WebFilterChainServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.security.model.JwtAuthentication;
import org.unreal.modelrouter.security.config.ExcludedPathsConfig;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.controller.response.RouterResponse;
import org.unreal.modelrouter.exception.exception.AuthenticationException;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Spring Security集成的API Key认证过滤器
 * 与Spring Security框架集成，支持API Key和JWT认证
 */
@Slf4j
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true", matchIfMissing = true)
public class SpringSecurityAuthenticationFilter implements WebFilter {
    
    private final SecurityProperties securityProperties;
    private final ReactiveAuthenticationManager authenticationManager;
    
    public SpringSecurityAuthenticationFilter(SecurityProperties securityProperties, 
                                            ReactiveAuthenticationManager authenticationManager) {
        this.securityProperties = securityProperties;
        this.authenticationManager = authenticationManager;
    }
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestPath = exchange.getRequest().getPath().value();
        log.debug("SpringSecurityAuthenticationFilter处理请求路径: {}", requestPath);
        
        // 检查是否需要认证
        boolean needsAuth = requiresAuthentication(exchange);
        
        if (!needsAuth) {
            log.debug("路径 {} 不需要认证，跳过认证", requestPath);
            return chain.filter(exchange);
        }
        
        log.debug("路径 {} 需要认证，开始认证流程", requestPath);
        
        // 如果API Key和JWT都未启用，则跳过认证
        if (!securityProperties.getApiKey().isEnabled() && !securityProperties.getJwt().isEnabled()) {
            log.warn("安全警告：API Key和JWT都未启用，路径 {} 将跳过认证", requestPath);
            return chain.filter(exchange);
        }
        
        // 检查是否为multipart请求，如果是，需要特殊处理
        MediaType contentType = exchange.getRequest().getHeaders().getContentType();
        boolean isMultipart = contentType != null && contentType.getType().equals("multipart");
        
        if (isMultipart) {
            log.debug("检测到multipart请求，使用早期头信息提取: {}", requestPath);
        }
        
        log.debug("开始调用认证转换器处理路径: {}", requestPath);
        
        // 创建认证转换器实例
        DefaultAuthenticationConverter converter = new DefaultAuthenticationConverter(securityProperties);
        
        // 对于multipart请求，立即尝试提取认证信息，不依赖请求体解析
        return converter.convert(exchange)
                .doOnNext(auth -> log.debug("认证转换器找到认证对象: {}", auth.getClass().getSimpleName()))
                .flatMap(authentication -> {
                    log.debug("开始认证过程: {}", authentication.getClass().getSimpleName());
                    // 使用认证管理器进行实际认证
                    return authenticationManager.authenticate(authentication)
                            .doOnNext(authenticated -> log.debug("认证成功: {}", authenticated.getName()))
                            .flatMap(authenticated -> {
                                // 创建已认证的安全上下文
                                SecurityContextImpl securityContext = new SecurityContextImpl(authenticated);
                                // 在安全上下文中继续执行过滤器链
                                return chain.filter(exchange)
                                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
                            })
                            .onErrorResume(throwable -> {
                                // 认证失败，传递给失败处理器
                                log.warn("认证失败: {}", throwable.getMessage());
                                return Mono.error(throwable);
                            });
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 没有提供认证信息，抛出异常让全局异常处理器处理
                    log.warn("认证转换器未找到认证信息: {}", exchange.getRequest().getPath().value());
                    return Mono.error(new AuthenticationException("请求缺少认证信息，请提供API Key或JWT Token", "AUTH_MISSING"));
                }));
    }
    
    /**
     * 检查请求是否需要认证
     */
    private boolean requiresAuthentication(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        
        // 排除不需要认证的路径
        return !ExcludedPathsConfig.isAuthExcluded(path);
    }
    
    /**
     * 默认的认证转换器
     */
    public static class DefaultAuthenticationConverter implements ServerAuthenticationConverter {
        
        private final SecurityProperties securityProperties;
        
        public DefaultAuthenticationConverter(SecurityProperties securityProperties) {
            this.securityProperties = securityProperties;
        }
        
        @Override
        public Mono<Authentication> convert(ServerWebExchange exchange) {
            log.debug("DefaultAuthenticationConverter开始转换认证信息，API Key启用: {}, JWT启用: {}", 
                    securityProperties.getApiKey().isEnabled(), 
                    securityProperties.getJwt().isEnabled());
            
            // 添加请求头调试信息
            log.debug("请求路径: {}", exchange.getRequest().getPath().value());
            log.debug("请求方法: {}", exchange.getRequest().getMethod());
            log.debug("Content-Type: {}", exchange.getRequest().getHeaders().getContentType());
            
            // 检查是否为multipart请求
            MediaType contentType = exchange.getRequest().getHeaders().getContentType();
            boolean isMultipart = contentType != null && contentType.getType().equals("multipart");
            
            if (isMultipart) {
                log.debug("处理multipart请求，使用早期头信息提取策略");
            }
            
            // 对于multipart请求，确保头信息完整可用
            HttpHeaders headers = exchange.getRequest().getHeaders();
            log.debug("可用请求头数量: {}", headers.size());
            
            String apiKey = null;
            String jwtToken = null;
            
            // 首先尝试提取API Key（如果启用）
            if (Boolean.TRUE.equals(securityProperties.getApiKey().isEnabled())) {
                log.debug("尝试提取API Key...");
                apiKey = extractApiKeyRobust(exchange);
                log.debug("API Key提取结果: {}", apiKey != null ? "成功" : "失败");
            }
            
            // 然后尝试提取JWT令牌（如果启用）
            if (Boolean.TRUE.equals(securityProperties.getJwt().isEnabled())) {
                log.debug("尝试提取JWT令牌...");
                jwtToken = extractJwtTokenRobust(exchange);
                log.debug("JWT令牌提取结果: {}", jwtToken != null ? "成功" : "失败");
            } else {
                log.debug("JWT已禁用，跳过JWT令牌提取");
            }
            
            // 如果同时提供了API Key和JWT令牌，则优先使用JWT
            if (jwtToken != null) {
                log.debug("提取到JWT令牌，创建JWT认证对象");
                return Mono.just(new JwtAuthentication(jwtToken));
            } else if (apiKey != null) {
                log.debug("提取到API Key，创建API Key认证对象");
                return Mono.just(new ApiKeyAuthentication(apiKey));
            }
            
            // 没有找到认证信息
            log.debug("DefaultAuthenticationConverter未找到任何认证信息");
            return Mono.empty();
        }
        
        /**
         * 提取API Key - 健壮版本，适用于multipart请求
         */
        private String extractApiKeyRobust(ServerWebExchange exchange) {
            String headerName = securityProperties.getApiKey().getHeaderName();
            
            try {
                // 尝试多种方式获取头信息
                HttpHeaders headers = exchange.getRequest().getHeaders();
                
                // 方式1：直接获取
                List<String> headerValues = headers.get(headerName);
                if (headerValues != null && !headerValues.isEmpty()) {
                    String apiKey = headerValues.get(0);
                    log.debug("从{}头提取到API Key (方式1): {}", headerName, apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
                    return apiKey;
                }
                
                // 方式2：忽略大小写获取
                String apiKey = headers.getFirst(headerName);
                if (apiKey != null && !apiKey.trim().isEmpty()) {
                    log.debug("从{}头提取到API Key (方式2): {}", headerName, apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
                    return apiKey.trim();
                }
                
                // 方式3：遍历所有头信息查找（忽略大小写）
                for (String key : headers.keySet()) {
                    if (key.equalsIgnoreCase(headerName)) {
                        List<String> values = headers.get(key);
                        if (values != null && !values.isEmpty() && values.get(0) != null) {
                            String foundApiKey = values.get(0).trim();
                            if (!foundApiKey.isEmpty()) {
                                log.debug("从{}头提取到API Key (方式3): {}", key, foundApiKey.substring(0, Math.min(8, foundApiKey.length())) + "...");
                                return foundApiKey;
                            }
                        }
                    }
                }
                
            } catch (Exception e) {
                log.warn("提取API Key时发生异常: {}", e.getMessage());
            }
            
            log.debug("未找到API Key，{}头为空或不存在", headerName);
            return null;
        }
        
        /**
         * 提取JWT令牌 - 健壮版本，适用于multipart请求
         */
        private String extractJwtTokenRobust(ServerWebExchange exchange) {
            String jwtHeader = securityProperties.getJwt().getJwtHeader();
            
            try {
                // 尝试多种方式获取头信息
                HttpHeaders headers = exchange.getRequest().getHeaders();
                
                // 方式1：直接获取
                List<String> jwtHeaders = headers.get(jwtHeader);
                if (jwtHeaders != null && !jwtHeaders.isEmpty()) {
                    String token = jwtHeaders.get(0);
                    log.debug("从{}头提取到JWT Token (方式1): {}", jwtHeader, token.substring(0, Math.min(8, token.length())) + "...");
                    return token;
                }
                
                // 方式2：忽略大小写获取
                String token = headers.getFirst(jwtHeader);
                if (token != null && !token.trim().isEmpty()) {
                    log.debug("从{}头提取到JWT Token (方式2): {}", jwtHeader, token.substring(0, Math.min(8, token.length())) + "...");
                    return token.trim();
                }
                
                // 方式3：遍历所有头信息查找（忽略大小写）
                for (String key : headers.keySet()) {
                    if (key.equalsIgnoreCase(jwtHeader)) {
                        List<String> values = headers.get(key);
                        if (values != null && !values.isEmpty() && values.get(0) != null) {
                            String foundToken = values.get(0).trim();
                            if (!foundToken.isEmpty()) {
                                log.debug("从{}头提取到JWT Token (方式3): {}", key, foundToken.substring(0, Math.min(8, foundToken.length())) + "...");
                                return foundToken;
                            }
                        }
                    }
                }
                
            } catch (Exception e) {
                log.warn("提取JWT Token时发生异常: {}", e.getMessage());
            }
            
            log.debug("未找到JWT Token，{}头为空或不存在", jwtHeader);
            return null;
        }
        
        /**
         * 提取API Key - 保留原方法以兼容
         */
        private String extractApiKey(ServerWebExchange exchange) {
            return extractApiKeyRobust(exchange);
        }
        
        /**
         * 提取JWT令牌 - 保留原方法以兼容
         */
        private String extractJwtToken(ServerWebExchange exchange) {
            return extractJwtTokenRobust(exchange);
        }
    }
}