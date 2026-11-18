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

package org.unreal.modelrouter.monitor.callhistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.auth.security.permission.DataScope;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.repository.ApiCallHistoryRepository;

/**
 * 统计入口不得自递归。
 *
 * <p>背景：引入「本人读」数据面后，2 参 {@code getStatistics(start, end)} 委托 3 参重载，
 * 而 3 参在 {@code scope.unrestricted()} 时又委托回 2 参 ⇒ 任何一次统计调用都会
 * {@code StackOverflowError}（2026-11-19 在运行态日志里发现，前端表现为页面一直停在骨架屏）。
 * 这类缺陷不会让编译失败、也不会让既有单测变红（没人调过 2 参入口），所以单独守在这里。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("调用历史：统计入口不得自递归")
class CallHistoryStatisticsRecursionTest {

    @Mock
    private ApiCallHistoryRepository repository;

    @InjectMocks
    private ApiCallHistoryService service;

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 12, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 19, 0, 0);

    @Test
    @DisplayName("2 参入口：不递归，返回统计结果")
    void twoArgEntryDoesNotRecurse() {
        when(repository.countAllInRange(any(), any())).thenReturn(3L);

        final CallHistoryStatisticsDTO dto = service.getStatistics(START, END);

        assertThat(dto).isNotNull();
        assertThat(dto.getTotalRequests()).isEqualTo(3L);
    }

    @Test
    @DisplayName("3 参入口（不受限）：不递归，且与 2 参入口同口径")
    void threeArgUnrestrictedDoesNotRecurse() {
        when(repository.countAllInRange(any(), any())).thenReturn(5L);

        final CallHistoryStatisticsDTO dto = service.getStatistics(START, END, DataScope.unrestricted());

        assertThat(dto).isNotNull();
        assertThat(dto.getTotalRequests()).isEqualTo(5L);
    }

    @Test
    @DisplayName("2 参入口在 null 时间下按默认区间计算，不抛异常")
    void twoArgEntryToleratesNullRange() {
        assertThatCode(() -> service.getStatistics(null, null)).doesNotThrowAnyException();
    }
}
