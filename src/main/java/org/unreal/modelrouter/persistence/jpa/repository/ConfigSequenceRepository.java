package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.ConfigSequenceEntity;

/**
 * 配置版本号序列仓储（#1135）。
 *
 * <p>分配走 CAS（比较并交换）而不是「先 UPDATE 再 SELECT 读回」，也不依赖数据库行锁语义：
 * 读出当前值后，只有「表里仍是这个值」时才写入新值，写入行数为 0 说明被别的副本抢先，
 * 由分配器重试。这样在 H2 / PostgreSQL 上语义一致，不会因某条读回链路缺少原子性而撞号。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
@Repository
public interface ConfigSequenceRepository extends JpaRepository<ConfigSequenceEntity, String> {

    /**
     * 仅当分配器行仍为期望值时写入新值（CAS）。
     *
     * @param configKey       配置键
     * @param expectedVersion 期望的当前值
     * @param newVersion      要写入的新值
     * @return 1 = 交换成功；0 = 期间被其它副本改动，调用方应重试
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ConfigSequenceEntity s SET s.nextVersion = :newVersion "
            + "WHERE s.configKey = :configKey AND s.nextVersion = :expectedVersion")
    int compareAndSet(@Param("configKey") String configKey,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("newVersion") long newVersion);
}
