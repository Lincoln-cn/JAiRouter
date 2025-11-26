package org.unreal.modelrouter.store.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.store.entity.JwtAccountEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * JWT 账户数据仓库
 */
@Repository
public interface JwtAccountRepository extends R2dbcRepository<JwtAccountEntity, Long> {

    /**
     * 根据用户名查找
     */
    @Query("SELECT * FROM jwt_accounts WHERE username = :username")
    Mono<JwtAccountEntity> findByUsername(@Param("username") String username);

    /**
     * 查找所有启用的账户
     */
    @Query("SELECT * FROM jwt_accounts WHERE enabled = true")
    Flux<JwtAccountEntity> findAllEnabled();

    /**
     * 根据用户名删除
     */
    @Query("DELETE FROM jwt_accounts WHERE username = :username")
    Mono<Void> deleteByUsername(@Param("username") String username);

    /**
     * 检查用户名是否存在
     */
    @Query("SELECT COUNT(*) > 0 FROM jwt_accounts WHERE username = :username")
    Mono<Boolean> existsByUsername(@Param("username") String username);

    /**
     * 统计总数
     */
    @Query("SELECT COUNT(*) FROM jwt_accounts")
    Mono<Long> countAll();

    /**
     * 统计启用的数量
     */
    @Query("SELECT COUNT(*) FROM jwt_accounts WHERE enabled = true")
    Mono<Long> countEnabled();
}
