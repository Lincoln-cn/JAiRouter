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
import org.unreal.modelrouter.monitor.tracing.logger.dto.BackendCallFields;

/**
 * 后端服务调用日志构建器 - v2.16.6重构
 *
 * 负责构建后端调用日志的强类型BackendCallFields DTO：
 * - 适配器名称
 * - 实例地址
 * - 调用时长
 * - 调用结果
 * - HTTP状态码
 *
 * 设计模式：Builder Pattern（构建器模式）
 * 注意：只负责构建日志字段数据，不负责输出
 *
 * @author JAiRouter Team
 * @since v2.16.6
 */
@Component
public class BackendCallLogBuilder {

    /**
     * 构建后端调用日志字段（简化版）
     *
     * @param adapter 适配器名称
     * @param instance 实例地址
     * @param duration 调用时长（毫秒）
     * @param success 是否成功
     * @return 强类型BackendCallFields DTO
     */
    public BackendCallFields buildBackendCall(final String adapter,
                                                final String instance,
                                                final long duration,
                                                final boolean success) {
        return BackendCallFields.simple(adapter, instance, duration, success);
    }

    /**
     * 构建后端调用日志字段（详细版）
     *
     * @param adapter 适配器名称
     * @param instance 实例地址
     * @param url 调用URL
     * @param method HTTP方法
     * @param duration 调用时长（毫秒）
     * @param statusCode HTTP状态码
     * @param success 是否成功
     * @return 强类型BackendCallFields DTO
     */
    public BackendCallFields buildBackendCallDetails(final String adapter,
                                                       final String instance,
                                                       final String url,
                                                       final String method,
                                                       final long duration,
                                                       final int statusCode,
                                                       final boolean success) {
        return BackendCallFields.detailed(adapter, instance, url, method, duration, statusCode, success);
    }

    /**
     * 构建后端调用日志消息
     *
     * @param adapter 适配器名称
     * @param instance 实例地址
     * @param duration 调用时长（毫秒）
     * @param success 是否成功
     * @return 日志消息
     */
    public String buildBackendCallMessage(final String adapter,
                                           final String instance,
                                           final long duration,
                                           final boolean success) {
        return String.format("后端服务调用%s，适配器: %s，实例: %s，耗时: %dms",
                success ? "成功" : "失败", adapter, instance, duration);
    }
}