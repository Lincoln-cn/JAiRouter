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

package org.unreal.modelrouter.monitor.tracing.logger.builder;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 性能日志构建器 - v2.16.2
 *
 * 从DefaultStructuredLogger提取的性能日志数据构建逻辑：
 * - 构建性能指标日志字段
 * - 构建慢查询日志字段
 *
 * 设计模式：Builder Pattern（构建器模式）
 * 注意：只负责构建日志数据，不负责输出
 *
 * @author JAiRouter Team
 * @since v2.16.2
 */
@Component
public class PerformanceLogBuilder {

    /**
     * 构建性能日志字段
     *
     * @param operation 操作名称
     * @param duration 持续时间（毫秒）
     * @param metrics 指标数据
     * @return 日志字段Map
     */
    public Map<String, Object> buildPerformanceFields(final String operation,
                                                       final long duration,
                                                       final Map<String, Object> metrics) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("operation", operation);
        fields.put("duration", duration);
        fields.put("durationMs", duration);

        if (metrics != null && !metrics.isEmpty()) {
            fields.put("metrics", metrics);
        }

        return fields;
    }

    /**
     * 构建慢查询日志字段
     *
     * @param operation 操作名称
     * @param duration 持续时间（毫秒）
     * @param threshold 阈值（毫秒）
     * @return 日志字段Map
     */
    public Map<String, Object> buildSlowQueryFields(final String operation,
                                                     final long duration,
                                                     final long threshold) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("threshold", threshold);
        metrics.put("slowQueryDetected", true);
        metrics.put("exceededBy", duration - threshold);

        return buildPerformanceFields(operation, duration, metrics);
    }

    /**
     * 构建性能日志消息
     *
     * @param operation 操作名称
     * @param duration 持续时间（毫秒）
     * @return 日志消息
     */
    public String buildPerformanceMessage(final String operation,
                                           final long duration) {
        return String.format("性能指标: %s，耗时: %dms", operation, duration);
    }

    /**
     * 构建慢查询日志消息
     *
     * @param operation 操作名称
     * @param duration 持续时间（毫秒）
     * @param threshold 阈值（毫秒）
     * @return 日志消息
     */
    public String buildSlowQueryMessage(final String operation,
                                         final long duration,
                                         final long threshold) {
        return String.format("慢查询检测: %s，耗时: %dms，阈值: %dms，超出: %dms",
                operation, duration, threshold, duration - threshold);
    }

    /**
     * 获取性能日志级别
     *
     * @param duration 持续时间（毫秒）
     * @param threshold 阈值（毫秒）
     * @return 日志级别
     */
    public String getPerformanceLogLevel(final long duration,
                                          final long threshold) {
        // 超过阈值使用WARN级别
        if (duration > threshold) {
            return "WARN";
        }
        return "INFO";
    }

    /**
     * 判断是否为慢查询
     *
     * @param duration 持续时间（毫秒）
     * @param threshold 阈值（毫秒）
     * @return 是否慢查询
     */
    public boolean isSlowQuery(final long duration,
                                final long threshold) {
        return duration > threshold;
    }
}