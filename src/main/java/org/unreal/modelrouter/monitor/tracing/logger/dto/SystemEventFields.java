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
 * 系统事件日志字段DTO - v2.16.4
 *
 * 替代Map<String, Object>的强类型方案：
 * - event: 事件名称
 * - details: 详细信息
 *
 * @author JAiRouter Team
 * @since v2.16.4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemEventFields implements LogFields {

    /**
     * 事件名称
     * 例如：startup, shutdown, config_reload, health_check
     */
    private String event;

    /**
     * 详细信息
     */
    private Map<String, Object> details;

    @Override
    public String getFieldType() {
        return "system";
    }

    /**
     * 创建系统事件字段
     */
    public static SystemEventFields create(final String event,
                                             final Map<String, Object> details) {
        return SystemEventFields.builder()
                .event(event)
                .details(details)
                .build();
    }
}