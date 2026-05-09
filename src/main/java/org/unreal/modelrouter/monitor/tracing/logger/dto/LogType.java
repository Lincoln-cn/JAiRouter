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
 * 日志类型枚举 - v2.16.4
 *
 * 定义结构化日志的所有类型常量
 *
 * @author JAiRouter Team
 * @since v2.16.4
 */
public enum LogType {

    /**
     * HTTP请求日志
     */
    REQUEST("request"),

    /**
     * HTTP响应日志
     */
    RESPONSE("response"),

    /**
     * 后端服务调用日志
     */
    BACKEND_CALL("backend_call"),

    /**
     * 错误日志
     */
    ERROR("error"),

    /**
     * 业务事件日志
     */
    BUSINESS_EVENT("business_event"),

    /**
     * 性能日志
     */
    PERFORMANCE("performance"),

    /**
     * 安全事件日志
     */
    SECURITY("security"),

    /**
     * 系统事件日志
     */
    SYSTEM("system");

    private final String value;

    LogType(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * 从字符串值获取LogType
     *
     * @param value 字符串值
     * @return LogType枚举，如果不存在返回null
     */
    public static LogType fromValue(final String value) {
        if (value == null) {
            return null;
        }
        for (LogType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }
}