package org.unreal.modelrouter.auth.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.common.dto.TokenStatus;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * JWT令牌查询服务
 * 提供令牌状态查询和统计信息聚合
 *
 * @since v2.17.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenQueryService {

    @Autowired(required = false)
    private JwtPersistenceService jwtPersistenceService;

    @Autowired(required = false)
    private JwtTokenManagementService jwtTokenManagementService;

    /**
     * 检查令牌的持久化状态
     *
     * @param token JWT令牌
     * @return Mono<Boolean> 令牌是否有效
     */
    public Mono<Boolean> checkTokenPersistenceStatus(final String token) {
        if (jwtPersistenceService == null || jwtTokenManagementService == null) {
            return Mono.just(true); // 服务不可用时默认有效
        }

        try {
            String tokenHash = jwtTokenManagementService.calculateTokenHash(token);
            return jwtPersistenceService.findByTokenHash(tokenHash)
                    .map(tokenInfo -> TokenStatus.ACTIVE.equals(tokenInfo.getStatus()) && !tokenInfo.isExpired())
                    .switchIfEmpty(Mono.just(true)) // 找不到记录时默认有效（可能是旧令牌）
                    .onErrorReturn(true); // 出错时默认有效
        } catch (Exception ex) {
            log.warn("检查令牌持久化状态异常: {}", ex.getMessage());
            return Mono.just(true);
        }
    }

    /**
     * 为黑名单统计信息添加持久化数据
     *
     * @param originalStats 原始统计信息
     * @return Mono<Map<String, Object>> 增强的统计信息
     */
    public Mono<Map<String, Object>> enhanceBlacklistStats(final Map<String, Object> originalStats) {
        if (jwtPersistenceService == null) {
            return Mono.just(originalStats);
        }

        return Mono.zip(
                jwtPersistenceService.countActiveTokens().onErrorReturn(0L),
                jwtPersistenceService.countTokensByStatus(TokenStatus.REVOKED).onErrorReturn(0L),
                jwtPersistenceService.countTokensByStatus(TokenStatus.EXPIRED).onErrorReturn(0L)
        ).map(tuple -> {
            Map<String, Object> enhancedStats = new HashMap<>(originalStats);
            enhancedStats.put("persistedActiveTokens", tuple.getT1());
            enhancedStats.put("persistedRevokedTokens", tuple.getT2());
            enhancedStats.put("persistedExpiredTokens", tuple.getT3());
            enhancedStats.put("totalPersistedTokens", tuple.getT1() + tuple.getT2() + tuple.getT3());
            return enhancedStats;
        }).onErrorReturn(originalStats);
    }

    /**
     * 获取持久化服务可用状态
     *
     * @return Mono<Boolean> 是否可用
     */
    public Mono<Boolean> isPersistenceAvailable() {
        return Mono.just(jwtPersistenceService != null);
    }

    /**
     * 获取活跃令牌数量
     *
     * @return Mono<Long> 活跃令牌数量
     */
    public Mono<Long> countActiveTokens() {
        if (jwtPersistenceService == null) {
            return Mono.just(0L);
        }
        return jwtPersistenceService.countActiveTokens().onErrorReturn(0L);
    }

    /**
     * 按状态统计令牌数量
     *
     * @param status 令牌状态
     * @return Mono<Long> 该状态的令牌数量
     */
    public Mono<Long> countTokensByStatus(final TokenStatus status) {
        if (jwtPersistenceService == null) {
            return Mono.just(0L);
        }
        return jwtPersistenceService.countTokensByStatus(status).onErrorReturn(0L);
    }
}