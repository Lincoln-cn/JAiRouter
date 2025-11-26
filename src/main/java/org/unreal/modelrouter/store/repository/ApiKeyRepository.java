package org.unreal.modelrouter.store.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.store.entity.ApiKeyEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * API Key 数据仓库
 */
@Repository
public interface ApiKeyRepository extends R2dbcRepository<ApiKeyEntity, Long> {

    /**
     * 根据 keyId 查找
     */
    @Query("SELECT * FROM api_keys WHERE key_id = :keyId")
    Mono<ApiKeyEntity> findByKeyId(@Param("keyId") String keyId);

    /**
     * 根据 keyValue 查找
     */
    @Query("SELECT * FROM api_keys WHERE key_value = :keyValue")
    Mono<ApiKeyEntity> findByKeyValue(@Param("keyValue") String keyValue);

    /**
     * 查找所有启用的 API Key
     */
    @Query("SELECT * FROM api_keys WHERE enabled = true")
    Flux<ApiKeyEntity> findAllEnabled();

    /**
     * 查找所有未过期的 API Key
     */
    @Query("SELECT * FROM api_keys WHERE expires_at IS NULL OR expires_at > :now")
    Flux<ApiKeyEntity> findAllNotExpired(@Param("now") LocalDateTime now);

    /**
     * 查找所有有效的 API Key（启用且未过期）
     */
    @Query("SELECT * FROM api_keys WHERE enabled = true AND (expires_at IS NULL OR expires_at > :now)")
    Flux<ApiKeyEntity> findAllValid(@Param("now") LocalDateTime now);

    /**
     * 根据 keyId 删除
     */
    @Query("DELETE FROM api_keys WHERE key_id = :keyId")
    Mono<Void> deleteByKeyId(@Param("keyId") String keyId);

    /**
     * 检查 keyId 是否存在
     */
    @Query("SELECT COUNT(*) > 0 FROM api_keys WHERE key_id = :keyId")
    Mono<Boolean> existsByKeyId(@Param("keyId") String keyId);

    /**
     * 检查 keyValue 是否存在
     */
    @Query("SELECT COUNT(*) > 0 FROM api_keys WHERE key_value = :keyValue")
    Mono<Boolean> existsByKeyValue(@Param("keyValue") String keyValue);

    /**
     * 统计总数
     */
    @Query("SELECT COUNT(*) FROM api_keys")
    Mono<Long> countAll();

    /**
     * 统计启用的数量
     */
    @Query("SELECT COUNT(*) FROM api_keys WHERE enabled = true")
    Mono<Long> countEnabled();
}
