package org.unreal.modelrouter.security.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.security.service.JwtTokenRefreshService;
import reactor.core.publisher.Mono;

// Validation imports removed - using Spring Boot 3.x validation
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * JWT令牌管理控制器
 * 提供令牌刷新、撤销等管理功能的REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/jwt")
@RequiredArgsConstructor
@Tag(name = "JWT Token Management", description = "JWT令牌管理API")
@ConditionalOnProperty(name = "jairouter.security.jwt.enabled", havingValue = "true")
public class JwtTokenController {
    
    private final JwtTokenRefreshService tokenRefreshService;
    private final JwtTokenValidator jwtTokenValidator;
    
    /**
     * 刷新JWT令牌
     */
    @PostMapping("/refresh")
    @Operation(summary = "刷新JWT令牌", description = "使用当前令牌获取新的JWT令牌")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "令牌刷新成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "当前令牌无效或已过期"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误")
    })
    public Mono<ResponseEntity<TokenResponse>> refreshToken(
            @Parameter(description = "令牌刷新请求") @RequestBody TokenRefreshRequest request,
            Authentication authentication) {
        
        log.debug("收到JWT令牌刷新请求: user={}", authentication != null ? authentication.getName() : "anonymous");
        
        return tokenRefreshService.refreshToken(request.getToken())
            .map(newToken -> {
                TokenResponse response = new TokenResponse();
                response.setToken(newToken);
                response.setTokenType("Bearer");
                response.setMessage("令牌刷新成功");
                response.setTimestamp(LocalDateTime.now());
                
                return ResponseEntity.ok(response);
            })
            .onErrorResume(ex -> {
                log.warn("JWT令牌刷新失败: {}", ex.getMessage());
                
                ErrorResponse errorResponse = new ErrorResponse();
                errorResponse.setError("Token Refresh Failed");
                errorResponse.setMessage(ex.getMessage());
                errorResponse.setTimestamp(LocalDateTime.now());
                
                return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    new TokenResponse(null, null, errorResponse.getMessage(), errorResponse.getTimestamp())
                ));
            });
    }
    
    /**
     * 撤销JWT令牌
     */
    @PostMapping("/revoke")
    @Operation(summary = "撤销JWT令牌", description = "将指定的JWT令牌加入黑名单")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "令牌撤销成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足")
    })
    @PreAuthorize("hasRole('ADMIN') or authentication.name == #request.userId")
    public Mono<ResponseEntity<JwtApiResponse>> revokeToken(
            @Parameter(description = "令牌撤销请求") @RequestBody TokenRevokeRequest request,
            Authentication authentication) {
        
        log.debug("收到JWT令牌撤销请求: user={}, targetToken={}", 
            authentication != null ? authentication.getName() : "anonymous",
            request.getToken().substring(0, Math.min(10, request.getToken().length())) + "...");
        
        return tokenRefreshService.revokeToken(request.getToken())
            .then(Mono.fromCallable(() -> {
                JwtApiResponse response = new JwtApiResponse();
                response.setSuccess(true);
                response.setMessage("令牌撤销成功");
                response.setTimestamp(LocalDateTime.now());
                
                return ResponseEntity.ok(response);
            }))
            .onErrorResume(ex -> {
                log.warn("JWT令牌撤销失败: {}", ex.getMessage());
                
                JwtApiResponse response = new JwtApiResponse();
                response.setSuccess(false);
                response.setMessage("令牌撤销失败: " + ex.getMessage());
                response.setTimestamp(LocalDateTime.now());
                
                return Mono.just(ResponseEntity.badRequest().body(response));
            });
    }
    
    /**
     * 批量撤销JWT令牌
     */
    @PostMapping("/revoke/batch")
    @Operation(summary = "批量撤销JWT令牌", description = "批量将多个JWT令牌加入黑名单")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "批量撤销成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<JwtApiResponse>> revokeTokensBatch(
            @Parameter(description = "批量令牌撤销请求") @RequestBody BatchTokenRevokeRequest request,
            Authentication authentication) {
        
        log.debug("收到批量JWT令牌撤销请求: user={}, tokenCount={}", 
            authentication != null ? authentication.getName() : "anonymous",
            request.getTokens().size());
        
        return tokenRefreshService.revokeTokens(request.getTokens())
            .then(Mono.fromCallable(() -> {
                JwtApiResponse response = new JwtApiResponse();
                response.setSuccess(true);
                response.setMessage(String.format("成功撤销%d个令牌", request.getTokens().size()));
                response.setTimestamp(LocalDateTime.now());
                
                return ResponseEntity.ok(response);
            }))
            .onErrorResume(ex -> {
                log.warn("批量JWT令牌撤销失败: {}", ex.getMessage());
                
                JwtApiResponse response = new JwtApiResponse();
                response.setSuccess(false);
                response.setMessage("批量撤销失败: " + ex.getMessage());
                response.setTimestamp(LocalDateTime.now());
                
                return Mono.just(ResponseEntity.badRequest().body(response));
            });
    }
    
    /**
     * 验证JWT令牌
     */
    @PostMapping("/validate")
    @Operation(summary = "验证JWT令牌", description = "检查JWT令牌的有效性")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "令牌验证完成"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数错误")
    })
    public Mono<ResponseEntity<TokenValidationResponse>> validateToken(
            @Parameter(description = "令牌验证请求") @RequestBody TokenValidationRequest request) {
        
        log.debug("收到JWT令牌验证请求");
        
        return tokenRefreshService.isTokenValid(request.getToken())
            .flatMap(isValid -> {
                if (isValid) {
                    // 令牌有效，提取用户信息
                    return jwtTokenValidator.extractUserId(request.getToken())
                        .map(userId -> {
                            TokenValidationResponse response = new TokenValidationResponse();
                            response.setValid(true);
                            response.setUserId(userId);
                            response.setMessage("令牌有效");
                            response.setTimestamp(LocalDateTime.now());
                            
                            return ResponseEntity.ok(response);
                        });
                } else {
                    // 令牌无效
                    TokenValidationResponse response = new TokenValidationResponse();
                    response.setValid(false);
                    response.setMessage("令牌无效或已被撤销");
                    response.setTimestamp(LocalDateTime.now());
                    
                    return Mono.just(ResponseEntity.ok(response));
                }
            })
            .onErrorResume(ex -> {
                log.warn("JWT令牌验证失败: {}", ex.getMessage());
                
                TokenValidationResponse response = new TokenValidationResponse();
                response.setValid(false);
                response.setMessage("令牌验证失败: " + ex.getMessage());
                response.setTimestamp(LocalDateTime.now());
                
                return Mono.just(ResponseEntity.ok(response));
            });
    }
    
    /**
     * 获取黑名单统计信息
     */
    @GetMapping("/blacklist/stats")
    @Operation(summary = "获取黑名单统计", description = "获取JWT令牌黑名单的统计信息")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "统计信息获取成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "权限不足")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Map<String, Object>>> getBlacklistStats(Authentication authentication) {
        
        log.debug("收到黑名单统计信息请求: user={}", 
            authentication != null ? authentication.getName() : "anonymous");
        
        return tokenRefreshService.getBlacklistStats()
            .map(ResponseEntity::ok)
            .onErrorResume(ex -> {
                log.warn("获取黑名单统计信息失败: {}", ex.getMessage());
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
            });
    }
    
    // DTO类定义
    
    @Data
    public static class TokenRefreshRequest {
        private String token;
    }
    
    @Data
    public static class TokenRevokeRequest {
        private String token;
        
        private String userId; // 可选，用于权限检查
        private String reason; // 可选，撤销原因
    }
    
    @Data
    public static class BatchTokenRevokeRequest {
        private List<String> tokens;
        
        private String reason; // 可选，撤销原因
    }
    
    @Data
    public static class TokenValidationRequest {
        private String token;
    }
    
    @Data
    public static class TokenResponse {
        private String token;
        private String tokenType;
        private String message;
        private LocalDateTime timestamp;
        
        public TokenResponse() {}
        
        public TokenResponse(String token, String tokenType, String message, LocalDateTime timestamp) {
            this.token = token;
            this.tokenType = tokenType;
            this.message = message;
            this.timestamp = timestamp;
        }
    }
    
    @Data
    public static class TokenValidationResponse {
        private boolean valid;
        private String userId;
        private String message;
        private LocalDateTime timestamp;
    }
    
    @Data
    public static class JwtApiResponse {
        private boolean success;
        private String message;
        private LocalDateTime timestamp;
    }
    
    @Data
    public static class ErrorResponse {
        private String error;
        private String message;
        private LocalDateTime timestamp;
    }
}