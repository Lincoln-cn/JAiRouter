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

/**
 * 日志字段基类接口 - v2.16.4
 *
 * 所有强类型日志字段DTO的统一接口，用于：
 * - StructuredLogEntry.fields字段的多态支持
 * - JSON序列化时的类型标记
 *
 * 实现类：
 * - SecurityEventFields: 安全事件字段
 * - PerformanceFields: 性能日志字段
 * - BusinessEventFields: 业务事件字段
 * - RequestLogFields: 请求日志字段
 * - ResponseLogFields: 响应日志字段
 * - BackendCallFields: 后端调用字段
 * - ErrorLogFields: 错误日志字段
 * - SystemEventFields: 系统事件字段
 *
 * @author JAiRouter Team
 * @since v2.16.4
 */
public interface LogFields {

    /**
     * 获取日志字段类型标识
     *
     * @return 字段类型字符串
     */
    String getFieldType();
}