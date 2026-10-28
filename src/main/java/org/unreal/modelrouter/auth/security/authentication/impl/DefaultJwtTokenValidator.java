package org.unreal.modelrouter.auth.security.authentication.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import org.unreal.modelrouter.auth.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;
import org.unreal.modelrouter.auth.security.model.JwtPrincipal;
import org.unreal.modelrouter.auth.security.service.EnhancedJwtBlacklistService;
import org.unreal.modelrouter.auth.security.service.JwtAccountStatusVerifier;
import org.unreal.modelrouter.common.exception.SecurityException;
import org.unreal.modelrouter.monitor.monitoring.security.SecurityMetrics;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final String PERMISSIONS_CLAIM = "permissions";
    private static final String USER_ID_CLAIM = "userId";

    /** 黑名单存储不可用后的短路窗口默认值（issue #76）。 */
    private static final long DEFAULT_DEGRADED_RETRY_INTERVAL_MILLIS = 30_000L;

    private final SecurityProperties securityProperties;
    private final ReactiveStringRedisTemplate redisTemplate;

    /** 降级告警是否已打过（issue #76：只打一次，避免每请求刷屏）。 */
    private final AtomicBoolean legacyDegradationLogged = new AtomicBoolean(false);

    /** 降级短路窗口截止时间戳；0 表示未处于降级状态。 */
    private volatile long legacyRedisRetryAfterMillis = 0L;

    // 使用增强的黑名单服务（可选依赖）
    @Autowired(required = false)
    private EnhancedJwtBlacklistService enhancedBlacklistService;

    /**
     * 安全指标（issue #456）。可选依赖：单元测试直接 new 出本类时该字段为 null，埋点自动跳过。
     */
    @Autowired(required = false)
    private SecurityMetrics securityMetrics;

    /**
     * 账户状态复核器（issue #704）。验签通过不代表签发对象仍是可用账户：账户被删除/禁用后，
     * 旧令牌必须在剩余有效期内立刻失效。容器内为必需依赖（{@code @Autowired} 默认
     * {@code required=true}，缺失即启动失败）；单元测试直接 new 出本类时该字段为 null，
     * 复核会被跳过——与 {@link #enhancedBlacklistService} 同一惯例。
     */
    @Autowired
    private JwtAccountStatusVerifier accountStatusVerifier;

    @Override
    public Mono<Authentication> validateToken(final String token) {
        // issue #456：每个受保护请求都会经过这里，是 JWT 指标的唯一天然埋点。
        final Timer.Sample sample = securityMetrics != null ? securityMetrics.startJwtValidationTimer() : null;
        // 检查令牌是否在黑名单中
        return isTokenBlacklisted(token)
            .flatMap(isBlacklisted -> {
                if (isBlacklisted) {
                    return Mono.error(new AuthenticationException("JWT令牌已被列入黑名单", ErrorCode.JWT_BLACKLISTED.code()));
                }

                try {
                    // 解析和验证JWT令牌
                    Claims claims = parseToken(token);

                    // 检查令牌是否过期
                    if (claims.getExpiration().before(new Date())) {
                        return Mono.error(new AuthenticationException("JWT令牌已过期", ErrorCode.JWT_EXPIRED.code()));
                    }

                    // 提取用户信息
                    String subject = claims.getSubject();
                    String issuer = claims.getIssuer();
                    List<String> roles = extractRoles(claims);
                    // v2.9.8 RBAC: 提取权限码声明（与 roles 声明同模式，可能缺失）
                    List<String> permissions = extractPermissions(claims);

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

                    // 创建认证对象（角色→ROLE_ 前缀 authority，权限码→无前缀 authority）
                    JwtAuthentication authentication =
                            new JwtAuthentication(subject, token, roles, permissions);
                    authentication.setAuthenticated(true);
                    authentication.setDetails(principal);

                    // issue #704：验签 + 未过期只证明「令牌是本站签发的」，不证明「账户此刻仍可用」。
                    // 账户被删除/禁用后旧令牌必须立刻失效，而不是等 expiration-minutes 自然过期。
                    return verifyAccountActive(subject)
                            .flatMap(active -> {
                                if (!active) {
                                    log.warn("JWT令牌对应的账户不存在或已被禁用: subject={}", subject);
                                    return Mono.error(new AuthenticationException(
                                            "JWT令牌对应的账户不存在或已被禁用",
                                            ErrorCode.JWT_INVALID.code()));
                                }
                                log.debug("JWT令牌验证成功: subject={}, roles={}, permissions={}",
                                        subject, roles, permissions);
                                return Mono.just((Authentication) authentication);
                            });

                } catch (JwtException e) {
                    log.warn("JWT令牌验证失败: {}", e.getMessage());
                    return Mono.error(new AuthenticationException(
                            "JWT令牌验证失败: " + e.getMessage(),
                            ErrorCode.JWT_INVALID.code()));
                } catch (Exception e) {
                    log.error("JWT令牌验证过程中发生错误", e);
                    return Mono.error(new AuthenticationException(
                            "JWT令牌验证过程中发生错误",
                            ErrorCode.JWT_VALIDATION_ERROR.code()));
                }
            })
            .doOnSuccess(authentication -> {
                if (securityMetrics != null) {
                    securityMetrics.recordJwtValidation(true);
                }
            })
            .doOnError(error -> {
                if (securityMetrics != null) {
                    securityMetrics.recordJwtValidation(false);
                    // 过期与无效要分开：过期数能反映 token 寿命配置是否合理
                    if (error instanceof SecurityException se
                            && ErrorCode.JWT_EXPIRED.code().equals(se.getErrorCode())) {
                        securityMetrics.recordJwtExpired();
                    }
                }
            })
            .doFinally(signal -> {
                if (securityMetrics != null && sample != null) {
                    securityMetrics.recordJwtValidationDuration(sample);
                }
            });
    }

    /**
     * 复核令牌所属账户当前是否可用（issue #704）。
     *
     * <p>拒绝时复用 {@code JWT_INVALID} 而未新增错误码：新增注册表条目会触发
     * {@code scripts/docs/check-error-codes.py} 的双向对账（必须同步 docs 的错误码主表，
     * 本批不改文档）。</p>
     *
     * <p>复核失败按 <b>fail-closed</b> 处理：宁可拒绝这次请求，也不放行一个可能已被撤销的令牌
     * （与黑名单存储不可用时的 fail-closed 口径一致）。容器外手工构造本类时复核器为 null，
     * 保持历史行为、不额外查库。</p>
     *
     * @param subject 令牌 subject
     * @return {@code true} 表示账户仍可用
     */
    private Mono<Boolean> verifyAccountActive(final String subject) {
        if (accountStatusVerifier == null) {
            return Mono.just(true);
        }
        return accountStatusVerifier.isActive(subject)
                .onErrorResume(error -> {
                    log.error("JWT令牌账户状态复核失败（按 fail-closed 拒绝）: subject={}", subject, error);
                    return Mono.error(new AuthenticationException(
                            "JWT令牌验证过程中发生错误", ErrorCode.JWT_VALIDATION_ERROR.code()));
                });
    }

    @Override
    public Mono<String> refreshToken(final String token) {
        // #694：刷新必须先过黑名单——否则已吊销但未过期的令牌仍能换出新令牌，
        // 「吊销/登出」对未过期令牌形同虚设（校验路径 :85 有这一步，刷新路径此前漏了）。
        return isTokenBlacklisted(token)
            .flatMap(isBlacklisted -> {
                if (Boolean.TRUE.equals(isBlacklisted)) {
                    log.warn("拒绝刷新：令牌已在黑名单中");
                    return Mono.error(new AuthenticationException(
                            "JWT令牌已被列入黑名单", ErrorCode.JWT_BLACKLISTED.code()));
                }
                return doRefreshToken(token);
            });
    }

    /**
     * 刷新令牌的实际实现（不含黑名单判定，判定在 {@link #refreshToken(String)}）。
     *
     * @param token 旧令牌
     * @return 新令牌
     */
    private Mono<String> doRefreshToken(final String token) {
        try {
            // 解析当前令牌
            Claims claims = parseToken(token);

            // 检查是否可以刷新（通常检查是否在刷新窗口内）
            Date now = new Date();
            Date expiration = claims.getExpiration();
            long refreshWindowMs = Duration.ofDays(securityProperties.getJwt().getRefreshExpirationDays()).toMillis();

            if (now.getTime() - expiration.getTime() > refreshWindowMs) {
                return Mono.error(new AuthenticationException("JWT令牌超出刷新窗口", ErrorCode.JWT_REFRESH_EXPIRED.code()));
            }

            // 创建新令牌（#704：账户被删除/禁用后连刷新也不许通过，否则撤销窗口可被无限续期）
            final String subject = claims.getSubject();
            final List<String> roles = extractRoles(claims);
            return verifyAccountActive(subject)
                .flatMap(active -> {
                    if (!active) {
                        log.warn("拒绝刷新：令牌对应的账户不存在或已被禁用: subject={}", subject);
                        return Mono.error(new AuthenticationException(
                                "JWT令牌对应的账户不存在或已被禁用",
                                ErrorCode.JWT_INVALID.code()));
                    }
                    Map<String, Object> additionalClaims = new HashMap<>();

                    // 保留原有的自定义声明。
                    // #694：必须排除 jti —— generateToken 已经生成新的 jti，若把旧 jti 当普通声明再写一遍，
                    // 会把新令牌的 jti 覆盖回旧值：同一秒内刷新会产出与旧令牌完全相同的字符串，
                    // 而旧 jti 刚刚被拉黑 ⇒ 新令牌立刻不可用。
                    claims.forEach((key, value) -> {
                        if (!key.equals("sub") && !key.equals("iss") 
                        && !key.equals("exp") && !key.equals("iat")
                        && !key.equals("nbf") && !key.equals("jti")) {
                            additionalClaims.put(key, value);
                        }
                    });

                    // 将旧令牌加入黑名单，然后签发新令牌
                    return blacklistToken(token)
                            .then(Mono.fromCallable(() -> generateToken(subject, roles, additionalClaims)));
                })
                .doOnSuccess(refreshed -> {
                    if (securityMetrics != null) {
                        securityMetrics.recordJwtRefresh();
                    }
                });

        } catch (JwtException e) {
            log.warn("JWT令牌刷新失败: {}", e.getMessage());
            return Mono.error(new AuthenticationException(
                    "JWT令牌刷新失败: " + e.getMessage(),
                    ErrorCode.JWT_REFRESH_FAILED.code()));
        } catch (Exception e) {
            log.error("JWT令牌刷新过程中发生错误", e);
            return Mono.error(new AuthenticationException("JWT令牌刷新过程中发生错误", ErrorCode.JWT_REFRESH_ERROR.code()));
        }
    }

    @Override
    public Mono<Boolean> isTokenBlacklisted(final String token) {
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

            // 存储已判定不可用时短路，避免每个请求都付一次连接超时（issue #76）
            if (System.currentTimeMillis() < legacyRedisRetryAfterMillis) {
                boolean failClosed = isBlacklistFailClosedWhenUnavailable();
                log.debug("黑名单存储处于降级短路窗口，跳过 Redis 查询: jti={}, failClosed={}",
                        finalJti, failClosed);
                // failClosed=false：历史行为，短路窗口内放行；true：按黑名单拒绝（issue #118）
                return Mono.just(failClosed);
            }

            return redisTemplate.hasKey(blacklistKey)
                .doOnNext(isBlacklisted -> {
                    if (isBlacklisted) {
                        log.warn("令牌在Redis黑名单中被发现: jti={}", finalJti);
                    }
                })
                // 查询成功即证明存储可用：清除降级状态，恢复后重新生效
                .doOnNext(ignored -> clearLegacyDegradation())
                .onErrorResume(ex -> {
                    legacyRedisRetryAfterMillis =
                            System.currentTimeMillis() + DEFAULT_DEGRADED_RETRY_INTERVAL_MILLIS;
                    boolean failClosed = isBlacklistFailClosedWhenUnavailable();
                    // 降级语义必须让运维可见，但只在首次明确告警，其后降为 DEBUG
                    // （原实现每个请求打两条 ERROR，会掩盖真正的鉴权失败——issue #76）
                    if (legacyDegradationLogged.compareAndSet(false, true)) {
                        log.error("JWT 黑名单校验已降级：黑名单存储不可达，failClosed={}，"
                                + "撤销/登出的令牌在存储恢复前{}，存在安全风险。error={}, jti={}",
                                failClosed,
                                failClosed ? "会被拦截（当前按黑名单拒绝）" : "不会被拦截",
                                ex.getMessage(), finalJti);
                    } else {
                        log.debug("黑名单存储仍不可用，继续降级放行: jti={}, failClosed={}, error={}",
                                finalJti, failClosed, ex.getMessage());
                    }
                    // 默认允许（failClosed=false，降级已在首次告警中说明）；
                    // failClosed=true 时按黑名单拒绝（issue #118）
                    return Mono.just(failClosed);
                });

        } catch (Exception e) {
            log.error("检查JWT令牌黑名单状态时发生严重错误: {}", e.getMessage(), e);
            return Mono.just(false); // 出错时默认不在黑名单中，但记录错误
        }
    }

    /**
     * 黑名单存储恢复可用时清除降级状态（幂等）.
     */
    private void clearLegacyDegradation() {
        legacyRedisRetryAfterMillis = 0L;
        if (legacyDegradationLogged.compareAndSet(true, false)) {
            log.info("JWT 黑名单存储已恢复，Redis 黑名单校验重新生效");
        }
    }

    @Override
    public Mono<Void> blacklistToken(final String token) {
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
    public Mono<String> extractUserId(final String token) {
        return Mono.fromCallable(() -> {
            try {
                Claims claims = parseToken(token);
                String userId = claims.get(USER_ID_CLAIM, String.class);

                if (userId == null) {
                    userId = claims.getSubject(); // 如果没有userId声明，使用subject
                }

                return userId;

            } catch (JwtException e) {
                throw new AuthenticationException(
                        "无法从JWT令牌中提取用户ID: " + e.getMessage(),
                        ErrorCode.JWT_USER_ID_EXTRACTION_FAILED.code());
            }
        });
    }

    /**
     * 生成新的JWT令牌（使用JJWT 0.12.x API）
     */
    public String generateToken(
            final String subject, final List<String> roles,
            final Map<String, Object> additionalClaims) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() 
        + Duration.ofMinutes(securityProperties.getJwt().getExpirationMinutes()).toMillis());

        JwtBuilder builder = Jwts.builder()
            .subject(subject)
            .issuer(securityProperties.getJwt().getIssuer())
            .issuedAt(now)
            .expiration(expiration)
            .id(UUID.randomUUID().toString()) // 设置JTI用于黑名单管理
            .claim(ROLES_CLAIM, roles)
            .signWith(getSigningKey());

        // 添加额外的声明
        if (additionalClaims != null) {
            additionalClaims.forEach(builder::claim);
        }

        return builder.compact();
    }

    /**
     * 解析JWT令牌
     */
    private Claims parseToken(final String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            // 签发端始终写入 iss（见 generateToken / AccountManager）；解析端强制校验（issue #117）
            .requireIssuer(securityProperties.getJwt().getIssuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * 黑名单存储不可用时是否拒绝令牌（fail-closed）。默认 false 保持历史放行语义（issue #118）。
     */
    private boolean isBlacklistFailClosedWhenUnavailable() {
        return securityProperties.getJwt().isBlacklistFailClosedWhenUnavailable();
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
    private List<String> extractRoles(final Claims claims) {
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
     * 从Claims中提取权限码列表（v2.9.8 RBAC）
     *
     * <p>与 extractRoles 同模式：兼容 List 与逗号分隔字符串两种形态；
     * 旧版本签发的令牌无 permissions 声明时返回空列表。
     */
    @SuppressWarnings("unchecked")
    private List<String> extractPermissions(final Claims claims) {
        Object permissionsObj = claims.get(PERMISSIONS_CLAIM);

        if (permissionsObj instanceof List) {
            return (List<String>) permissionsObj;
        } else if (permissionsObj instanceof String) {
            return Arrays.asList(((String) permissionsObj).split(","));
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * 将Date转换为LocalDateTime
     */
    private LocalDateTime convertToLocalDateTime(final Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * 提取令牌ID（用于黑名单）
     */
    private String extractTokenId(final String token) {
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
    private String calculateTokenHash(final String token) {
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