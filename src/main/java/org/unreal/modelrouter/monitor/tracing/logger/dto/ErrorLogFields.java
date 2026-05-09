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
 * 错误日志字段DTO - v2.16.4
 *
 * 替代Map<String, Object>的强类型方案：
 * - errorType: 异常类型（类名）
 * - errorMessage: 异常消息
 * - stackTrace: 堆栈信息（可选，截断）
 * - additionalInfo: 额外信息（可选）
 *
 * @author JAiRouter Team
 * @since v2.16.4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorLogFields implements LogFields {

    /**
     * 异常类型（类名）
     * 例如：NullPointerException, TimeoutException
     */
    private String errorType;

    /**
     * 异常消息
     */
    private String errorMessage;

    /**
     * 堆栈信息（截断处理，最多2000字符）
     */
    private String stackTrace;

    /**
     * 额外信息
     */
    private Map<String, Object> additionalInfo;

    @Override
    public String getFieldType() {
        return "error";
    }

    /**
     * 创建错误日志字段
     */
    public static ErrorLogFields create(final Throwable error,
                                          final String stackTrace) {
        return ErrorLogFields.builder()
                .errorType(error.getClass().getSimpleName())
                .errorMessage(error.getMessage())
                .stackTrace(stackTrace)
                .build();
    }

    /**
     * 创建带额外信息的错误日志字段
     */
    public static ErrorLogFields withAdditionalInfo(final Throwable error,
                                                      final String stackTrace,
                                                      final Map<String, Object> additionalInfo) {
        return ErrorLogFields.builder()
                .errorType(error.getClass().getSimpleName())
                .errorMessage(error.getMessage())
                .stackTrace(stackTrace)
                .additionalInfo(additionalInfo)
                .build();
    }
}