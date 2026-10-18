/*
 * Copyright (c) 2025 JAiRouter Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.auth.security.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.common.dto.BlacklistStatsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity.BlacklistStatus;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity.BlacklistType;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity.RiskLevel;
import org.unreal.modelrouter.persistence.jpa.repository.SecurityBlacklistRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 黑名单统计的"契约面字段"测试（issue #501）。
 *
 * <p>回归点：{@code highRiskCount} / {@code criticalRiskCount} / {@code lastAddedAt} /
 * {@code lastExpiredAt} / {@code lastCleanupAt} 这 5 个字段曾在 DTO 里声明、**生产者从不赋值**，
 * 于是响应里恒为 null。这里断言它们现在有真实来源。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SecurityBlacklistServiceImpl 统计契约测试")
class SecurityBlacklistServiceImplTest {

    @Mock
    private SecurityBlacklistRepository repository;

    @InjectMocks
    private SecurityBlacklistServiceImpl service;

    private void stubEmptyBaseline() {
        when(repository.countByStatus(any())).thenReturn(0L);
        when(repository.countByType(any())).thenReturn(List.of());
        when(repository.countByStatusAndRiskLevelIn(any(), any())).thenReturn(0L);
        when(repository.findMaxAddedAt(any())).thenReturn(null);
        when(repository.findMaxExpiresAt(any())).thenReturn(null);
    }

    @Test
    @DisplayName("风险计数与最近时间来自仓库，不再恒为 null")
    void statsExposeRiskCountsAndTimestamps() {
        stubEmptyBaseline();
        when(repository.countByStatus(BlacklistStatus.ACTIVE)).thenReturn(3L);
        when(repository.countByType(BlacklistStatus.ACTIVE)).thenReturn(List.of(
                new Object[]{BlacklistType.TOKEN, 2L},
                new Object[]{BlacklistType.IP, 1L}));
        when(repository.countByStatusAndRiskLevelIn(
                BlacklistStatus.ACTIVE, List.of(RiskLevel.HIGH))).thenReturn(2L);
        when(repository.countByStatusAndRiskLevelIn(
                BlacklistStatus.ACTIVE, List.of(RiskLevel.CRITICAL))).thenReturn(1L);
        when(repository.findMaxAddedAt(BlacklistStatus.ACTIVE))
                .thenReturn(LocalDateTime.of(2026, 1, 1, 10, 0, 0));
        when(repository.findMaxExpiresAt(BlacklistStatus.EXPIRED))
                .thenReturn(LocalDateTime.of(2026, 1, 2, 11, 30, 0));

        BlacklistStatsDTO stats = service.getBlacklistStats();

        assertEquals(3L, stats.getTotalActive());
        assertEquals(2L, stats.getHighRiskCount(), "高风险计数必须来自仓库统计");
        assertEquals(1L, stats.getCriticalRiskCount(), "严重风险计数必须来自仓库统计");
        assertEquals("2026-01-01T10:00:00", stats.getLastAddedAt());
        assertEquals("2026-01-02T11:30:00", stats.getLastExpiredAt());
        assertNull(stats.getLastCleanupAt(), "尚未发生有效清理时应为 null，而不是伪造时间");
    }

    @Test
    @DisplayName("清理确实标记了过期条目后，lastCleanupAt 才有值")
    void cleanupSetsLastCleanupAtOnlyWhenItMarksEntries() {
        stubEmptyBaseline();
        when(repository.markExpired(any(), any(), any())).thenReturn(0);
        when(repository.cleanupExpired(any(), any())).thenReturn(0);

        service.cleanupExpiredEntries();
        assertNull(service.getBlacklistStats().getLastCleanupAt(),
                "没有条目被标记为过期时不应更新时间");

        when(repository.markExpired(any(), any(), any())).thenReturn(2);
        service.cleanupExpiredEntries();
        assertNotNull(service.getBlacklistStats().getLastCleanupAt(),
                "标记了过期条目后应记录清理时间");
    }
}
