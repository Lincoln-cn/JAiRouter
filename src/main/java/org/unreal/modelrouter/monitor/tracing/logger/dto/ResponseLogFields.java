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
 * 响应日志字段DTO - v2.16.4
 *
 * 替代Map<String, Object>的强类型方案：
 * - statusCode: HTTP状态码
 * - statusText: 状态文本
 * - responseSize: 响应大小
 * - duration: 处理时长（毫秒）
 * - headers: 响应头
 *
 * @author JAiRouter Team
 * @since v2.16.4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseLogFields implements LogFields {

    /**
     * HTTP状态码
     */
    private Integer statusCode;

    /**
     * 状态文本描述
     */
    private String statusText;

    /**
     * 响应大小（字节）
     */
    private Long responseSize;

    /**
     * 处理时长（毫秒）
     */
    private Long duration;

    /**
     * 响应头（已脱敏）
     */
    private Map<String, String> headers;

    @Override
    public String getFieldType() {
        return "response";
    }
}