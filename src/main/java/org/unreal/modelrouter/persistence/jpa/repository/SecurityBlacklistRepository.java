package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity.BlacklistStatus;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity.BlacklistType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 统一安全黑名单仓库
 */
@Repository
public interface SecurityBlacklistRepository extends JpaRepository<SecurityBlacklistEntity, Long> {

    Optional<SecurityBlacklistEntity> findByBlacklistTypeAndTargetValue(
            BlacklistType blacklistType, String targetValue);

    Optional<SecurityBlacklistEntity> findByTargetHash(String targetHash);

    List<SecurityBlacklistEntity> findByBlacklistTypeAndStatus(
            BlacklistType blacklistType, BlacklistStatus status);

    List<SecurityBlacklistEntity> findByUserId(String userId);

    List<SecurityBlacklistEntity> findByStatus(BlacklistStatus status);

    Page<SecurityBlacklistEntity> findByBlacklistType(
            BlacklistType blacklistType, Pageable pageable);

    Page<SecurityBlacklistEntity> findByStatus(
            BlacklistStatus status, Pageable pageable);

    Page<SecurityBlacklistEntity> findByBlacklistTypeAndStatus(
            BlacklistType blacklistType, BlacklistStatus status, Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END "
           + "FROM SecurityBlacklistEntity e "
           + "WHERE e.blacklistType = :type AND e.targetValue = :value "
           + "AND e.status = :status "
           + "AND (e.expiresAt IS NULL OR e.expiresAt > :now)")
    boolean isActiveInBlacklist(
            @Param("type") BlacklistType type,
            @Param("value") String value,
            @Param("status") BlacklistStatus status,
            @Param("now") LocalDateTime now);

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END "
           + "FROM SecurityBlacklistEntity e "
           + "WHERE e.targetHash = :hash "
           + "AND e.status = :status "
           + "AND (e.expiresAt IS NULL OR e.expiresAt > :now)")
    boolean isHashInBlacklist(
            @Param("hash") String hash,
            @Param("status") BlacklistStatus status,
            @Param("now") LocalDateTime now);

    @Query("SELECT e.blacklistType, COUNT(e) "
           + "FROM SecurityBlacklistEntity e "
           + "WHERE e.status = :status "
           + "GROUP BY e.blacklistType")
    List<Object[]> countByType(@Param("status") BlacklistStatus status);

    @Query("SELECT COUNT(e) FROM SecurityBlacklistEntity e WHERE e.status = :status")
    long countByStatus(@Param("status") BlacklistStatus status);

    @Modifying
    @Query("UPDATE SecurityBlacklistEntity e SET e.status = :expired "
           + "WHERE e.status = :active AND e.expiresAt IS NOT NULL AND e.expiresAt < :now")
    int markExpired(@Param("active") BlacklistStatus active,
                    @Param("expired") BlacklistStatus expired,
                    @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM SecurityBlacklistEntity e "
           + "WHERE e.status = :expired AND e.expiresAt < :threshold")
    int cleanupExpired(@Param("expired") BlacklistStatus expired,
                       @Param("threshold") LocalDateTime threshold);
}