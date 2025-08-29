package org.unreal.modelrouter.security.authentication.impl;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.exception.exception.AuthenticationException;
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.model.JwtAuthentication;
import org.unreal.modelrouter.security.model.JwtPrincipal;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
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
            // 从令牌中提取JTI（JWT ID）用作黑名单键
            Claims claims = parseToken(token);
            String jti = claims.getId();
            
            if (jti == null) {
                // 如果没有JTI，使用令牌的哈希值
                jti = String.valueOf(token.hashCode());
            }
            
            String blacklistKey = BLACKLIST_KEY_PREFIX + jti;
            return redisTemplate.hasKey(blacklistKey)
                .onErrorReturn(false); // Redis连接失败时默认不在黑名单中
                
        } catch (Exception e) {
            log.warn("检查JWT令牌黑名单状态时发生错误: {}", e.getMessage());
            return Mono.just(false); // 出错时默认不在黑名单中
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
                String jti = claims.getId();
                
                if (jti == null) {
                    jti = String.valueOf(token.hashCode());
                }
                
                String blacklistKey = BLACKLIST_KEY_PREFIX + jti;
                
                // 计算令牌剩余有效期，用作Redis过期时间
                Date expiration = claims.getExpiration();
                long ttlSeconds = Math.max(0, (expiration.getTime() - System.currentTimeMillis()) / 1000);
                
                if (ttlSeconds > 0) {
                    return redisTemplate.opsForValue()
                        .set(blacklistKey, "blacklisted", Duration.ofSeconds(ttlSeconds))
                        .then();
                } else {
                    // 令牌已过期，无需加入黑名单
                    return Mono.<Void>empty();
                }
                
            } catch (Exception e) {
                log.warn("将JWT令牌加入黑名单时发生错误: {}", e.getMessage());
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
}