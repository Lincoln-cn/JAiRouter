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
 * 请求日志字段DTO - v2.16.4
 *
 * 替代Map<String, Object>的强类型方案：
 * - method: HTTP方法
 * - path: URL路径
 * - url: 完整URL
 * - clientIp: 客户端IP
 * - userAgent: 用户代理
 * - requestSize: 请求大小
 * - headers: 请求头
 *
 * @author JAiRouter Team
 * @since v2.16.4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequestLogFields implements LogFields {

    /**
     * HTTP方法（GET/POST/PUT/DELETE等）
     */
    private String method;

    /**
     * URL路径（不含查询参数）
     */
    private String path;

    /**
     * 完整URL（含查询参数，已脱敏）
     */
    private String url;

    /**
     * 客户端IP地址（已脱敏）
     */
    private String clientIp;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 请求大小（字节）
     */
    private Long requestSize;

    /**
     * 请求头（已脱敏敏感信息）
     */
    private Map<String, String> headers;

    @Override
    public String getFieldType() {
        return "request";
    }
}