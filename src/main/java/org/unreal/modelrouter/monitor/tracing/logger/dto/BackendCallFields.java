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

/**
 * 后端服务调用日志字段DTO - v2.16.4
 *
 * 替代Map<String, Object>的强类型方案：
 * - adapter: 适配器名称
 * - instance: 实例名称
 * - duration: 调用时长（毫秒）
 * - success: 是否成功
 * - statusCode: HTTP状态码
 * - url: 调用URL（可选）
 * - method: HTTP方法（可选）
 *
 * @author JAiRouter Team
 * @since v2.16.4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BackendCallFields implements LogFields {

    /**
     * 适配器名称
     * 例如：GPUStack, Ollama, vLLM
     */
    private String adapter;

    /**
     * 实例名称/地址
     */
    private String instance;

    /**
     * 调用时长（毫秒）
     */
    private Long duration;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * HTTP状态码
     */
    private Integer statusCode;

    /**
     * 调用URL（可选）
     */
    private String url;

    /**
     * HTTP方法（可选）
     */
    private String method;

    @Override
    public String getFieldType() {
        return "backend_call";
    }

    /**
     * 创建基础后端调用字段（简化版）
     */
    public static BackendCallFields simple(final String adapter,
                                             final String instance,
                                             final long duration,
                                             final boolean success) {
        return BackendCallFields.builder()
                .adapter(adapter)
                .instance(instance)
                .duration(duration)
                .success(success)
                .statusCode(success ? 200 : 500)
                .build();
    }

    /**
     * 创建详细后端调用字段
     */
    public static BackendCallFields detailed(final String adapter,
                                               final String instance,
                                               final String url,
                                               final String method,
                                               final long duration,
                                               final int statusCode,
                                               final boolean success) {
        return BackendCallFields.builder()
                .adapter(adapter)
                .instance(instance)
                .url(url)
                .method(method)
                .duration(duration)
                .statusCode(statusCode)
                .success(success)
                .build();
    }
}