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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.logger.dto.ErrorLogFields;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

/**
 * 错误日志构建器 - v2.16.6重构
 *
 * 负责构建错误日志的强类型ErrorLogFields DTO：
 * - 异常类型
 * - 异常消息
 * - 堆栈信息（截断处理）
 * - 额外信息
 *
 * 设计模式：Builder Pattern（构建器模式）
 * 注意：只负责构建日志字段数据，不负责输出
 *
 * @author JAiRouter Team
 * @since v2.16.6
 */
@Component
@RequiredArgsConstructor
public class ErrorLogBuilder {

    private final TracingConfiguration tracingConfiguration;

    /**
     * 堆栈信息最大长度
     */
    private static final int MAX_STACK_TRACE_LENGTH = 2000;

    /**
     * 构建错误日志字段
     *
     * @param error 异常
     * @return 强类型ErrorLogFields DTO
     */
    public ErrorLogFields buildError(final Throwable error) {
        String stackTrace = getStackTrace(error);
        return ErrorLogFields.create(error, stackTrace);
    }

    /**
     * 构建错误日志字段（带额外信息）
     *
     * @param error 异常
     * @param additionalInfo 额外信息
     * @return 强类型ErrorLogFields DTO
     */
    public ErrorLogFields buildErrorWithInfo(final Throwable error,
                                               final Map<String, Object> additionalInfo) {
        String stackTrace = getStackTrace(error);
        if (additionalInfo != null && !additionalInfo.isEmpty()) {
            return ErrorLogFields.withAdditionalInfo(error, stackTrace, additionalInfo);
        }
        return ErrorLogFields.create(error, stackTrace);
    }

    /**
     * 构建错误日志消息
     *
     * @param error 异常
     * @return 日志消息
     */
    public String buildErrorMessage(final Throwable error) {
        return String.format("发生错误: %s", error.getMessage());
    }

    /**
     * 获取异常堆栈信息（截断处理）
     *
     * @param error 异常
     * @return 堆栈信息字符串，或null
     */
    public String getStackTrace(final Throwable error) {
        if (error == null) {
            return null;
        }

        // 检查配置是否启用堆栈跟踪
        if (!tracingConfiguration.getLogging().isIncludeStackTrace()) {
            return null;
        }

        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            error.printStackTrace(pw);
            String stackTrace = sw.toString();

            // 截断过长的堆栈信息
            if (stackTrace.length() > MAX_STACK_TRACE_LENGTH) {
                stackTrace = stackTrace.substring(0, MAX_STACK_TRACE_LENGTH) + "... (truncated)";
            }

            return stackTrace;
        } catch (Exception e) {
            return error.toString();
        }
    }
}