package org.unreal.modelrouter.auth.security.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.dto.AddBlacklistRequest;
import org.unreal.modelrouter.dto.BlacklistEntryDTO;
import org.unreal.modelrouter.dto.BlacklistStatsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity.BlacklistSource;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity.BlacklistStatus;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity.BlacklistType;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity.RiskLevel;
import org.unreal.modelrouter.persistence.jpa.repository.SecurityBlacklistRepository;
import org.unreal.modelrouter.auth.security.service.SecurityBlacklistService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 统一安全黑名单服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityBlacklistServiceImpl implements SecurityBlacklistService {

    private final SecurityBlacklistRepository repository;

    @Override
    @Transactional
    public BlacklistEntryDTO addToBlacklist(final AddBlacklistRequest request, final String addedBy) {
        log.info("添加到黑名单: type={}, value={}, addedBy={}",
                request.getBlacklistType(), maskTargetValue(request.getBlacklistType(), request.getTargetValue()), addedBy);

        BlacklistType type = BlacklistType.valueOf(request.getBlacklistType().toUpperCase());

        Optional<SecurityBlacklistEntity> existing = repository.findByBlacklistTypeAndTargetValue(type, request.getTargetValue());
        if (existing.isPresent()) {
            SecurityBlacklistEntity entity = existing.get();
            if (entity.getStatus() != BlacklistStatus.ACTIVE) {
                entity.setStatus(BlacklistStatus.ACTIVE);
                entity.setAddedAt(LocalDateTime.now());
                entity.setAddedBy(addedBy);
                entity.setReason(request.getReason());
                if (request.getExpiresInSeconds() != null) {
                    entity.setExpiresAt(LocalDateTime.now().plusSeconds(request.getExpiresInSeconds()));
                }
                entity.setUpdatedAt(LocalDateTime.now());
                repository.save(entity);
                log.info("黑名单条目已重新激活: id={}", entity.getId());
            }
            return convertToDTO(entity);
        }

        SecurityBlacklistEntity entity = SecurityBlacklistEntity.builder()
                .blacklistType(type)
                .targetValue(request.getTargetValue())
                .targetHash(generateHash(request.getTargetValue()))
                .userId(request.getUserId())
                .reason(request.getReason())
                .riskLevel(parseRiskLevel(request.getRiskLevel()))
                .addedBy(addedBy)
                .addedAt(LocalDateTime.now())
                .expiresAt(request.getExpiresInSeconds() != null
                        ? LocalDateTime.now().plusSeconds(request.getExpiresInSeconds())
                        : null) // null表示永久
                .status(BlacklistStatus.ACTIVE)
                .source(parseSource(request.getSource()))
                .metadata(request.getMetadata())
                .build();

        SecurityBlacklistEntity saved = repository.save(entity);
        log.info("黑名单条目创建成功: id={}, type={}", saved.getId(), type);

        return convertToDTO(saved);
    }

    @Override
    @Transactional
    public boolean removeFromBlacklist(final Long id) {
        log.info("从黑名单移除: id={}", id);

        Optional<SecurityBlacklistEntity> entity = repository.findById(id);
        if (entity.isEmpty()) {
            log.warn("黑名单条目不存在: id={}", id);
            return false;
        }

        SecurityBlacklistEntity blacklist = entity.get();
        blacklist.setStatus(BlacklistStatus.REMOVED);
        blacklist.setUpdatedAt(LocalDateTime.now());
        repository.save(blacklist);

        log.info("黑名单条目已移除: id={}", id);
        return true;
    }

    @Override
    @Transactional
    public boolean removeFromBlacklist(final BlacklistType type, final String targetValue) {
        log.info("从黑名单移除: type={}, value={}", type, maskTargetValue(type.name(), targetValue));

        Optional<SecurityBlacklistEntity> entity = repository.findByBlacklistTypeAndTargetValue(type, targetValue);
        if (entity.isEmpty()) {
            log.warn("黑名单条目不存在: type={}, value={}", type, maskTargetValue(type.name(), targetValue));
            return false;
        }

        return removeFromBlacklist(entity.get().getId());
    }

    @Override
    public boolean isInBlacklist(final BlacklistType type, final String targetValue) {
        return repository.isActiveInBlacklist(type, targetValue, BlacklistStatus.ACTIVE, LocalDateTime.now());
    }

    @Override
    public boolean isTokenHashInBlacklist(final String tokenHash) {
        if (tokenHash == null) {
            return false;
        }
        return repository.isHashInBlacklist(tokenHash, BlacklistStatus.ACTIVE, LocalDateTime.now());
    }

    @Override
    public boolean isIpInBlacklist(final String ipAddress) {
        if (ipAddress == null) {
            return false;
        }
        return isInBlacklist(BlacklistType.IP, ipAddress);
    }

    @Override
    public boolean isDeviceInBlacklist(final String deviceIdentifier) {
        if (deviceIdentifier == null) {
            return false;
        }
        return isInBlacklist(BlacklistType.DEVICE, deviceIdentifier);
    }

    @Override
    public BlacklistEntryDTO getBlacklistEntry(final Long id) {
        return repository.findById(id)
                .map(this::convertToDTO)
                .orElse(null);
    }

    @Override
    public Page<BlacklistEntryDTO> getBlacklistPage(final BlacklistType type, final String status,final int page,final int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "addedAt"));

        Page<SecurityBlacklistEntity> entityPage;
        if (type != null && status != null) {
            BlacklistStatus blacklistStatus = BlacklistStatus.valueOf(status.toUpperCase());
            entityPage = repository.findByBlacklistTypeAndStatus(type, blacklistStatus, pageRequest);
        } else if (type != null) {
            entityPage = repository.findByBlacklistType(type, pageRequest);
        } else if (status != null) {
            BlacklistStatus blacklistStatus = BlacklistStatus.valueOf(status.toUpperCase());
            entityPage = repository.findByStatus(blacklistStatus, pageRequest);
        } else {
            entityPage = repository.findAll(pageRequest);
        }

        return entityPage.map(this::convertToDTO);
    }

    @Override
    public BlacklistStatsDTO getBlacklistStats() {
        long totalActive = repository.countByStatus(BlacklistStatus.ACTIVE);

        List<Object[]> typeCounts = repository.countByType(BlacklistStatus.ACTIVE);
        Map<String, Long> typeMap = new HashMap<>();
        long tokenCount = 0, ipCount = 0, deviceCount = 0;

        for (Object[] row : typeCounts) {
            BlacklistType type = (BlacklistType) row[0];
            Long count = (Long) row[1];
            typeMap.put(type.name(), count);

            switch (type) {
                case TOKEN: tokenCount = count; break;
                case IP: ipCount = count; break;
                case DEVICE: deviceCount = count; break;
            }
        }

        return BlacklistStatsDTO.builder()
                .totalActive(totalActive)
                .typeCounts(typeMap)
                .tokenCount(tokenCount)
                .ipCount(ipCount)
                .deviceCount(deviceCount)
                .build();
    }

    @Override
    @Scheduled(fixedRate = 300000)
    @Transactional
    public int cleanupExpiredEntries() {
        log.info("开始清理过期黑名单条目");

        int marked = repository.markExpired(BlacklistStatus.ACTIVE, BlacklistStatus.EXPIRED, LocalDateTime.now());
        log.info("标记过期条目数量: {}", marked);

        int deleted = repository.cleanupExpired(BlacklistStatus.EXPIRED, LocalDateTime.now().minusDays(7));
        log.info("删除过期条目数量: {}", deleted);

        return marked + deleted;
    }

    @Override
    @Transactional
    public int batchAddToBlacklist(final Iterable<AddBlacklistRequest> requests, final String addedBy) {
        int count = 0;
        for (AddBlacklistRequest request : requests) {
            try {
                addToBlacklist(request, addedBy);
                count++;
            } catch (Exception e) {
                log.warn("批量添加黑名单失败: type={}, error={}",
                        request.getBlacklistType(), e.getMessage());
            }
        }
        log.info("批量添加黑名单完成: 成功数量={}", count);
        return count;
    }

    private String generateHash(final String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("生成哈希值失败", e);
            return null;
        }
    }

    private String maskTargetValue(final String type, final String value) {
        if (value == null || value.length() < 8) {
            return value == null ? "null" : value;
        }

        switch (type.toUpperCase()) {
            case "TOKEN":
                return value.substring(0, 8) + "..." + value.substring(value.length() - 8);
            case "IP":
                String[] parts = value.split("\\.");
                if (parts.length >= 2) {
                    return parts[0] + "." + parts[1] + ".xxx.xxx";
                }
                return value;
            case "DEVICE":
                return value.substring(0, Math.min(10, value.length())) + "...";
            default:
                return value.substring(0, 8) + "...";
        }
    }

    private RiskLevel parseRiskLevel(final String riskLevel) {
        if (riskLevel == null || riskLevel.isEmpty()) {
            return RiskLevel.MEDIUM;
        }
        try {
            return RiskLevel.valueOf(riskLevel.toUpperCase());
        } catch (IllegalArgumentException e) {
            return RiskLevel.MEDIUM;
        }
    }

    private BlacklistSource parseSource(final String source) {
        if (source == null || source.isEmpty()) {
            return BlacklistSource.MANUAL;
        }
        try {
            return BlacklistSource.valueOf(source.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BlacklistSource.MANUAL;
        }
    }

    private BlacklistEntryDTO convertToDTO(final SecurityBlacklistEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        boolean expired = entity.isExpired();
        boolean active = entity.isActive();

        // 永久 = expiresAt为null
        boolean permanent = entity.getExpiresAt() == null;

        Long remainingSeconds = null;
        if (entity.getExpiresAt() != null && !expired) {
            remainingSeconds = ChronoUnit.SECONDS.between(now, entity.getExpiresAt());
            if (remainingSeconds < 0) {
                remainingSeconds = 0L;
            }
        }

        return BlacklistEntryDTO.builder()
                .id(entity.getId())
                .blacklistType(entity.getBlacklistType().name())
                .targetValue(entity.getTargetValue())
                .targetValueMasked(maskTargetValue(entity.getBlacklistType().name(), entity.getTargetValue()))
                .userId(entity.getUserId())
                .reason(entity.getReason())
                .riskLevel(entity.getRiskLevel() != null ? entity.getRiskLevel().name() : "MEDIUM")
                .addedBy(entity.getAddedBy())
                .addedAt(entity.getAddedAt())
                .expiresAt(entity.getExpiresAt())
                .permanent(permanent)
                .status(entity.getStatus().name())
                .source(entity.getSource() != null ? entity.getSource().name() : "MANUAL")
                .active(active)
                .expired(expired)
                .remainingSeconds(remainingSeconds)
                .build();
    }
}