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
import org.unreal.modelrouter.monitor.tracing.logger.dto.SystemEventFields;

import java.util.Map;

/**
 * 系统事件日志构建器 - v2.16.6重构
 *
 * 负责构建系统事件日志的强类型SystemEventFields DTO：
 * - 事件名称
 * - 详细信息
 *
 * 设计模式：Builder Pattern（构建器模式）
 * 注意：只负责构建日志字段数据，不负责输出
 *
 * @author JAiRouter Team
 * @since v2.16.6
 */
@Component
public class SystemEventLogBuilder {

    /**
     * 构建系统事件日志字段
     *
     * @param event 事件名称
     * @param details 详细信息
     * @return 强类型SystemEventFields DTO
     */
    public SystemEventFields buildSystemEvent(final String event,
                                               final Map<String, Object> details) {
        return SystemEventFields.create(event, details);
    }

    /**
     * 构建系统事件日志消息
     *
     * @param event 事件名称
     * @return 日志消息
     */
    public String buildSystemEventMessage(final String event) {
        return String.format("系统事件: %s", event);
    }

    /**
     * 获取系统事件日志级别
     *
     * @param level 指定级别（可为null）
     * @return 日志级别，默认INFO
     */
    public String getSystemEventLogLevel(final String level) {
        return level != null ? level : "INFO";
    }
}