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
 * 安全事件日志字段DTO - v2.16.4
 *
 * 替代Map<String, Object>的强类型方案：
 * - event: 事件类型（如 authentication_success/failure）
 * - user: 用户标识
 * - ip: IP地址
 * - authMethod: 认证方式（可选）
 * - success: 是否成功（可选）
 * - field: 脱敏字段名（可选）
 * - action: 动作类型（可选）
 * - ruleId: 规则ID（可选）
 * - configType: 配置类型（可选）
 * - details: 详细信息（可选）
 *
 * @author JAiRouter Team
 * @since v2.16.4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityEventFields implements LogFields {

    /**
     * 事件类型
     * 例如：authentication_success, authentication_failure, data_sanitization
     */
    private String event;

    /**
     * 用户标识
     */
    private String user;

    /**
     * IP地址
     */
    private String ip;

    /**
     * 认证方式（认证事件专用）
     * 例如：JWT, API_KEY
     */
    private String authMethod;

    /**
     * 是否成功（认证事件专用）
     */
    private Boolean success;

    /**
     * 脱敏字段名（脱敏事件专用）
     */
    private String field;

    /**
     * 动作类型（脱敏事件专用）
     */
    private String action;

    /**
     * 规则ID（脱敏事件专用）
     */
    private String ruleId;

    /**
     * 配置类型（配置变更事件专用）
     */
    private String configType;

    /**
     * 详细信息（配置变更事件专用）
     */
    private Map<String, Object> details;

    @Override
    public String getFieldType() {
        return "security";
    }

    /**
     * 创建认证事件字段
     */
    public static SecurityEventFields forAuthentication(final boolean success,
                                                         final String authMethod,
                                                         final String user,
                                                         final String ip) {
        return SecurityEventFields.builder()
                .event(success ? "authentication_success" : "authentication_failure")
                .user(user)
                .ip(ip)
                .authMethod(authMethod)
                .success(success)
                .build();
    }

    /**
     * 创建数据脱敏事件字段
     */
    public static SecurityEventFields forSanitization(final String field,
                                                        final String action,
                                                        final String ruleId) {
        return SecurityEventFields.builder()
                .event("data_sanitization")
                .field(field)
                .action(action)
                .ruleId(ruleId)
                .build();
    }

    /**
     * 创建配置变更事件字段
     */
    public static SecurityEventFields forConfigurationChange(final String configType,
                                                              final String action,
                                                              final Map<String, Object> details) {
        return SecurityEventFields.builder()
                .event("configuration_change")
                .configType(configType)
                .action(action)
                .details(details)
                .build();
    }
}