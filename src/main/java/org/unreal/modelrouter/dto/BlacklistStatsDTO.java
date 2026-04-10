package org.unreal.modelrouter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 黑名单统计DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistStatsDTO {

    private Long totalActive;
    private Map<String, Long> typeCounts;
    private Long tokenCount;
    private Long ipCount;
    private Long deviceCount;
    private Long highRiskCount;
    private Long criticalRiskCount;
    private String lastAddedAt;
    private String lastExpiredAt;
    private String lastCleanupAt;
}