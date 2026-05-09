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

package org.unreal.modelrouter.monitor.tracing.logger.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 性能日志字段DTO - v2.16.4
 *
 * 替代Map<String, Object>的强类型方案：
 * - operation: 操作名称
 * - duration: 持续时间（毫秒）
 * - durationMs: 持续时间毫秒（兼容字段）
 * - metrics: 额外指标数据
 * - threshold: 阈值（慢查询专用）
 * - slowQueryDetected: 是否慢查询（慢查询专用）
 * - exceededBy: 超出阈值（慢查询专用）
 *
 * @author JAiRouter Team
 * @since v2.16.4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PerformanceFields implements LogFields {

    /**
     * 操作名称
     */
    private String operation;

    /**
     * 持续时间（毫秒）
     */
    private Long duration;

    /**
     * 持续时间毫秒（兼容字段）
     */
    private Long durationMs;

    /**
     * 额外指标数据
     */
    private Map<String, Object> metrics;

    /**
     * 阈值（毫秒）- 慢查询专用
     */
    private Long threshold;

    /**
     * 是否检测到慢查询 - 慢查询专用
     */
    private Boolean slowQueryDetected;

    /**
     * 超出阈值（毫秒）- 慢查询专用
     */
    private Long exceededBy;

    @Override
    public String getFieldType() {
        return "performance";
    }

    /**
     * 创建性能日志字段
     */
    public static PerformanceFields forPerformance(final String operation,
                                                     final long duration,
                                                     final Map<String, Object> metrics) {
        return PerformanceFields.builder()
                .operation(operation)
                .duration(duration)
                .durationMs(duration)
                .metrics(metrics)
                .build();
    }

    /**
     * 创建慢查询日志字段
     */
    public static PerformanceFields forSlowQuery(final String operation,
                                                   final long duration,
                                                   final long threshold) {
        return PerformanceFields.builder()
                .operation(operation)
                .duration(duration)
                .durationMs(duration)
                .threshold(threshold)
                .slowQueryDetected(true)
                .exceededBy(duration - threshold)
                .build();
    }

    /**
     * 判断是否为慢查询
     */
    public boolean isSlowQuery() {
        return threshold != null && duration != null && duration > threshold;
    }
}