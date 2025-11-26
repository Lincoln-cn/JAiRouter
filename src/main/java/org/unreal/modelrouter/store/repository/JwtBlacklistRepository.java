package org.unreal.modelrouter.store.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.store.entity.JwtBlacklistEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * JWT 黑名单数据仓库
 */
@Repository
public interface JwtBlacklistRepository extends R2dbcRepository<JwtBlacklistEntity, Long> {

    /**
     * 根据令牌哈希查找黑名单记录
     */
    Mono<JwtBlacklistEntity> findByTokenHash(String tokenHash);

    /**
     * 检查令牌是否在黑名单中
     */
    @Query("SELECT COUNT(*) > 0 FROM jwt_blacklist WHERE token_hash = :tokenHash")
    Mono<Boolean> existsByTokenHash(String tokenHash);

    /**
     * 根据用户ID查找黑名单记录
     */
    Flux<JwtBlacklistEntity> findByUserId(String userId);

    /**
     * 删除过期的黑名单记录
     */
    @Query("DELETE FROM jwt_blacklist WHERE expires_at < :now")
    Mono<Long> deleteExpiredTokens(LocalDateTime now);

    /**
     * 统计黑名单记录数
     */
    @Query("SELECT COUNT(*) FROM jwt_blacklist WHERE expires_at >= :now")
    Mono<Long> countActiveBlacklistEntries(LocalDateTime now);

    /**
     * 根据令牌哈希删除黑名单记录
     */
    @Query("DELETE FROM jwt_blacklist WHERE token_hash = :tokenHash")
    Mono<Long> deleteByTokenHash(String tokenHash);
}
