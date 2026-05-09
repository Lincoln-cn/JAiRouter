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

import java.time.Instant;

/**
 * 结构化日志条目DTO - v2.16.4
 *
 * 顶层日志数据结构，替代Map<String, Object>弱约束方案：
 * - timestamp: 日志时间戳
 * - type: 日志类型（使用LogType枚举）
 * - serviceName: 服务名称
 * - serviceVersion: 服务版本
 * - environment: 环境名称
 * - traceId: 追踪ID
 * - spanId: Span ID
 * - fields: 日志字段（强类型Fields DTO）
 * - message: 日志消息
 * - level: 日志级别
 *
 * @author JAiRouter Team
 * @since v2.16.4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StructuredLogEntry {

    /**
     * 日志时间戳（ISO-8601格式）
     */
    private Instant timestamp;

    /**
     * 日志类型
     */
    private LogType type;

    /**
     * 服务名称
     */
    private String serviceName;

    /**
     * 服务版本
     */
    private String serviceVersion;

    /**
     * 环境名称（命名空间）
     */
    private String environment;

    /**
     * 追踪ID
     */
    private String traceId;

    /**
     * Span ID
     */
    private String spanId;

    /**
     * 日志字段（强类型）
     */
    private LogFields fields;

    /**
     * 日志消息
     */
    private String message;

    /**
     * 日志级别（INFO/WARN/ERROR/DEBUG）
     */
    private String level;

    /**
     * 获取日志类型的字符串值（用于JSON序列化）
     */
    public String getTypeValue() {
        return type != null ? type.getValue() : null;
    }

    /**
     * 设置日志类型（从字符串）
     */
    public void setTypeFromValue(final String value) {
        this.type = LogType.fromValue(value);
    }
}