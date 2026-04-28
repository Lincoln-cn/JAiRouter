package org.unreal.modelrouter.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 黑名单条目DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistEntryDTO {

    private Long id;
    private String blacklistType;
    private String targetValue;
    private String targetValueMasked;
    private String userId;
    private String reason;
    private String riskLevel;
    private String addedBy;
    private LocalDateTime addedAt;
    private LocalDateTime expiresAt;
    private Boolean permanent;
    private String status;
    private String source;
    private Boolean active;
    private Boolean expired;
    private Long remainingSeconds;
}