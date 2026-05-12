package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.ConfigVersionHistoryEntity;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ConfigVersionHistoryEntity.
 *
 * @since v2.6.12
 */
@Repository
public interface ConfigVersionHistoryRepository extends JpaRepository<ConfigVersionHistoryEntity, Long> {

    List<ConfigVersionHistoryEntity> findByServiceType(String serviceType);

    Optional<ConfigVersionHistoryEntity> findByVersionNumber(String versionNumber);

    @Query("SELECT v FROM ConfigVersionHistoryEntity v WHERE v.serviceType = :serviceType ORDER BY v.timestamp DESC")
    List<ConfigVersionHistoryEntity> findByServiceTypeOrderByTimestampDesc(String serviceType);

    @Query("SELECT v FROM ConfigVersionHistoryEntity v ORDER BY v.timestamp DESC LIMIT :limit")
    List<ConfigVersionHistoryEntity> findLatest(int limit);

    boolean existsByVersionNumber(String versionNumber);
}
