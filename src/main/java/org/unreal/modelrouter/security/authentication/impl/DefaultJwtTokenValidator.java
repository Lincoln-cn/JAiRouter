package org.unreal.modelrouter.security.authentication.impl;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.exception.AuthenticationException;
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.security.model.JwtAuthentication;
import org.unreal.modelrouter.security.model.JwtPrincipal;
import org.unreal.modelrouter.security.service.EnhancedJwtBlacklistService;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * JWT令牌验证器默认实现
 * 提供JWT令牌的验证、解析、刷新和黑名单管理功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.jwt.enabled", havingValue = "true")
public class DefaultJwtTokenValidator implements JwtTokenValidator {
    
    private static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";
    private static final String ROLES_CLAIM = "roles";
    private static final String USER_ID_CLAIM = "userId";
    
    private final SecurityProperties securityProperties;
    private final ReactiveStringRedisTemplate redisTemplate;
    
    // 使用增强的黑名单服务（可选依赖）
    @Autowired(required = false)
    private EnhancedJwtBlacklistService enhancedBlacklistService;
    
    @Override
    public Mono<Authentication> validateToken(String token) {
        // 检查令牌是否在黑名单中
        return isTokenBlacklisted(token)
            .flatMap(isBlacklisted -> {
                if (isBlacklisted) {
                    return Mono.error(new AuthenticationException("JWT令牌已被列入黑名单", "JWT_BLACKLISTED"));
                }
                
                try {
                    // 解析和验证JWT令牌
                    Claims claims = parseToken(token);
                    
                    // 检查令牌是否过期
                    if (claims.getExpiration().before(new Date())) {
                        return Mono.error(new AuthenticationException("JWT令牌已过期", "JWT_EXPIRED"));
                    }
                    
                    // 提取用户信息
                    String subject = claims.getSubject();
                    String issuer = claims.getIssuer();
                    List<String> roles = extractRoles(claims);
                    
                    LocalDateTime issuedAt = convertToLocalDateTime(claims.getIssuedAt());
                    LocalDateTime expiresAt = convertToLocalDateTime(claims.getExpiration());
                    
                    // 创建JWT主体对象
                    JwtPrincipal principal = new JwtPrincipal(
                        subject,
                        issuer,
                        roles,
                        issuedAt,
                        expiresAt,
                        new HashMap<>(claims)
                    );
                    
                    // 创建认证对象
                    JwtAuthentication authentication = new JwtAuthentication(subject, token, roles);
                    authentication.setAuthenticated(true);
                    authentication.setDetails(principal);
                    
                    log.debug("JWT令牌验证成功: subject={}, roles={}", subject, roles);
                    return Mono.just((Authentication) authentication);
                    
                } catch (JwtException e) {
                    log.warn("JWT令牌验证失败: {}", e.getMessage());
                    return Mono.error(new AuthenticationException("JWT令牌验证失败: " + e.getMessage(), "JWT_INVALID"));
                } catch (Exception e) {
                    log.error("JWT令牌验证过程中发生错误", e);
                    return Mono.error(new AuthenticationException("JWT令牌验证过程中发生错误", "JWT_VALIDATION_ERROR"));
                }
            });
    }
    
    @Override
    public Mono<String> refreshToken(String token) {
        try {
            // 解析当前令牌
            Claims claims = parseToken(token);
            
            // 检查是否可以刷新（通常检查是否在刷新窗口内）
            Date now = new Date();
            Date expiration = claims.getExpiration();
            long refreshWindowMs = Duration.ofDays(securityProperties.getJwt().getRefreshExpirationDays()).toMillis();
            
            if (now.getTime() - expiration.getTime() > refreshWindowMs) {
                return Mono.error(new AuthenticationException("JWT令牌超出刷新窗口", "JWT_REFRESH_EXPIRED"));
            }
            
            // 将旧令牌加入黑名单
            return blacklistToken(token)
                .then(Mono.fromCallable(() -> {
                    // 创建新令牌
                    String subject = claims.getSubject();
                    List<String> roles = extractRoles(claims);
                    Map<String, Object> additionalClaims = new HashMap<>();
                    
                    // 保留原有的自定义声明
                    claims.forEach((key, value) -> {
                        if (!key.equals("sub") && !key.equals("iss") && 
                            !key.equals("exp") && !key.equals("iat") && 
                            !key.equals("nbf")) {
                            additionalClaims.put(key, value);
                        }
                    });
                    
                    return generateToken(subject, roles, additionalClaims);
                }));
                
        } catch (JwtException e) {
            log.warn("JWT令牌刷新失败: {}", e.getMessage());
            return Mono.error(new AuthenticationException("JWT令牌刷新失败: " + e.getMessage(), "JWT_REFRESH_FAILED"));
        } catch (Exception e) {
            log.error("JWT令牌刷新过程中发生错误", e);
            return Mono.error(new AuthenticationException("JWT令牌刷新过程中发生错误", "JWT_REFRESH_ERROR"));
        }
    }
    
    @Override
    public Mono<Boolean> isTokenBlacklisted(String token) {
        if (!securityProperties.getJwt().isBlacklistEnabled()) {
            return Mono.just(false);
        }
        
        try {
            // 优先使用增强的黑名单服务
            if (enhancedBlacklistService != null) {
                String tokenId = extractTokenId(token);
                return enhancedBlacklistService.isBlacklisted(tokenId)
                    .doOnNext(isBlacklisted -> {
                        if (isBlacklisted) {
                            log.warn("令牌在增强黑名单中被发现: tokenId={}", tokenId);
                        }
                    });
            }
            
            // 降级到原有的Redis检查
            Claims claims = parseToken(token);
            String jti = claims.getId();
            
            if (jti == null) {
                // 如果没有JTI，使用令牌的哈希值
                jti = calculateTokenHash(token);
            }
            
            final String finalJti = jti; // 创建final变量供lambda使用
            String blacklistKey = BLACKLIST_KEY_PREFIX + finalJti;
            return redisTemplate.hasKey(blacklistKey)
                .doOnNext(isBlacklisted -> {
                    if (isBlacklisted) {
                        log.warn("令牌在Redis黑名单中被发现: jti={}", finalJti);
                    }
                })
                .onErrorResume(ex -> {
                    log.error("检查Redis黑名单时发生错误: {}", ex.getMessage());
                    // Redis连接失败时，为了安全起见，应该拒绝令牌
                    // 但这可能影响正常用户，所以记录严重警告
                    log.error("Redis黑名单检查失败，令牌状态未知，存在安全风险: jti={}", finalJti);
                    return Mono.just(false); // 默认允许，但记录错误
                });
                
        } catch (Exception e) {
            log.error("检查JWT令牌黑名单状态时发生严重错误: {}", e.getMessage(), e);
            return Mono.just(false); // 出错时默认不在黑名单中，但记录错误
        }
    }
    
    @Override
    public Mono<Void> blacklistToken(String token) {
        if (!securityProperties.getJwt().isBlacklistEnabled()) {
            return Mono.empty();
        }
        
        return Mono.fromCallable(() -> {
            try {
                Claims claims = parseToken(token);
                
                // 计算令牌剩余有效期
                Date expiration = claims.getExpiration();
                long ttlSeconds = Math.max(0, (expiration.getTime() - System.currentTimeMillis()) / 1000);
                
                if (ttlSeconds <= 0) {
                    log.debug("令牌已过期，无需加入黑名单");
                    return Mono.<Void>empty();
                }
                
                // 优先使用增强的黑名单服务
                if (enhancedBlacklistService != null) {
                    String tokenId = extractTokenId(token);
                    return enhancedBlacklistService.addToBlacklist(tokenId, ttlSeconds)
                        .doOnNext(success -> {
                            if (success) {
                                log.info("令牌已通过增强服务加入黑名单: tokenId={}, ttl={}s", tokenId, ttlSeconds);
                            } else {
                                log.error("通过增强服务加入黑名单失败: tokenId={}", tokenId);
                            }
                        })
                        .then();
                }
                
                // 降级到原有的Redis方式
                String jti = claims.getId();
                if (jti == null) {
                    jti = calculateTokenHash(token);
                }
                
                final String finalJti = jti; // 创建final变量供lambda使用
                String blacklistKey = BLACKLIST_KEY_PREFIX + finalJti;
                
                return redisTemplate.opsForValue()
                    .set(blacklistKey, "blacklisted", Duration.ofSeconds(ttlSeconds))
                    .doOnNext(success -> {
                        if (success) {
                            log.info("令牌已通过Redis加入黑名单: jti={}, ttl={}s", finalJti, ttlSeconds);
                        } else {
                            log.error("通过Redis加入黑名单失败: jti={}", finalJti);
                        }
                    })
                    .then();
                
            } catch (Exception e) {
                log.error("将JWT令牌加入黑名单时发生错误: {}", e.getMessage(), e);
                return Mono.<Void>empty(); // 出错时静默失败
            }
        }).flatMap(mono -> mono);
    }
    
    @Override
    public Mono<String> extractUserId(String token) {
        return Mono.fromCallable(() -> {
            try {
                Claims claims = parseToken(token);
                String userId = claims.get(USER_ID_CLAIM, String.class);
                
                if (userId == null) {
                    userId = claims.getSubject(); // 如果没有userId声明，使用subject
                }
                
                return userId;
                
            } catch (JwtException e) {
                throw new AuthenticationException("无法从JWT令牌中提取用户ID: " + e.getMessage(), "JWT_USER_ID_EXTRACTION_FAILED");
            }
        });
    }
    
    /**
     * 生成新的JWT令牌
     */
    public String generateToken(String subject, List<String> roles, Map<String, Object> additionalClaims) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + 
            Duration.ofMinutes(securityProperties.getJwt().getExpirationMinutes()).toMillis());
        
        JwtBuilder builder = Jwts.builder()
            .setSubject(subject)
            .setIssuer(securityProperties.getJwt().getIssuer())
            .setIssuedAt(now)
            .setExpiration(expiration)
            .setId(UUID.randomUUID().toString()) // 设置JTI用于黑名单管理
            .claim(ROLES_CLAIM, roles)
            .signWith(getSigningKey(), SignatureAlgorithm.valueOf(securityProperties.getJwt().getAlgorithm()));
        
        // 添加额外的声明
        if (additionalClaims != null) {
            additionalClaims.forEach(builder::claim);
        }
        
        return builder.compact();
    }
    
    /**
     * 解析JWT令牌
     */
    private Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
    
    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        String secret = securityProperties.getJwt().getSecret();
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("JWT签名密钥未配置");
        }
        
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    
    /**
     * 从Claims中提取角色列表
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRoles(Claims claims) {
        Object rolesObj = claims.get(ROLES_CLAIM);
        
        if (rolesObj instanceof List) {
            return (List<String>) rolesObj;
        } else if (rolesObj instanceof String) {
            return Arrays.asList(((String) rolesObj).split(","));
        } else {
            return new ArrayList<>();
        }
    }
    
    /**
     * 将Date转换为LocalDateTime
     */
    private LocalDateTime convertToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
    
    /**
     * 提取令牌ID（用于黑名单）
     */
    private String extractTokenId(String token) {
        try {
            Claims claims = parseToken(token);
            String jti = claims.getId();
            
            if (jti != null && !jti.trim().isEmpty()) {
                return jti.trim();
            }
            
            // 如果没有JTI，使用令牌的哈希值
            return calculateTokenHash(token);
            
        } catch (Exception e) {
            log.warn("提取令牌ID失败，使用哈希值: {}", e.getMessage());
            return calculateTokenHash(token);
        }
    }
    
    /**
     * 计算令牌哈希值
     */
    private String calculateTokenHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            log.warn("计算令牌SHA-256哈希失败，使用简单哈希: {}", ex.getMessage());
            return String.valueOf(token.hashCode());
        }
    }
}