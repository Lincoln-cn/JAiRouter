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
import org.unreal.modelrouter.monitor.tracing.TracingContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 安全事件日志构建器 - v2.16.1
 *
 * 从DefaultStructuredLogger提取的安全事件日志数据构建逻辑：
 * - 构建安全事件日志字段
 * - 构建认证事件日志字段
 * - 构建配置变更日志字段
 *
 * 设计模式：Builder Pattern（构建器模式）
 * 注意：只负责构建日志数据，不负责输出
 *
 * @author JAiRouter Team
 * @since v2.16.1
 */
@Component
public class SecurityEventLogBuilder {

    /**
     * 构建安全事件日志字段
     *
     * @param event 事件类型
     * @param user 用户
     * @param ip IP地址
     * @return 日志字段Map
     */
    public Map<String, Object> buildSecurityEventFields(final String event,
                                                         final String user,
                                                         final String ip) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("event", event);
        fields.put("user", user);
        fields.put("ip", ip);
        return fields;
    }

    /**
     * 构建认证事件日志字段
     *
     * @param success 是否成功
     * @param authMethod 认证方式
     * @param user 用户
     * @param ip IP地址
     * @return 日志字段Map
     */
    public Map<String, Object> buildAuthenticationEventFields(final boolean success,
                                                              final String authMethod,
                                                              final String user,
                                                              final String ip) {
        String event = success ? "authentication_success" : "authentication_failure";
        Map<String, Object> fields = buildSecurityEventFields(event, user, ip);
        fields.put("authMethod", authMethod);
        fields.put("success", success);
        return fields;
    }

    /**
     * 构建数据脱敏日志字段
     *
     * @param field 字段名
     * @param action 动作
     * @param ruleId 规则ID
     * @return 日志字段Map
     */
    public Map<String, Object> buildSanitizationFields(final String field,
                                                        final String action,
                                                        final String ruleId) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("field", field);
        fields.put("action", action);
        fields.put("ruleId", ruleId);
        return fields;
    }

    /**
     * 构建配置变更日志字段
     *
     * @param configType 配置类型
     * @param action 动作
     * @param details 详情
     * @return 日志字段Map
     */
    public Map<String, Object> buildConfigurationChangeFields(final String configType,
                                                              final String action,
                                                              final Map<String, Object> details) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("configType", configType);
        fields.put("action", action);
        if (details != null) {
            fields.put("details", details);
        }
        return fields;
    }

    /**
     * 构建安全事件日志消息
     *
     * @param event 事件类型
     * @param user 用户
     * @param ip IP地址
     * @return 日志消息
     */
    public String buildSecurityEventMessage(final String event,
                                             final String user,
                                             final String ip) {
        return String.format("安全事件: %s，用户: %s，IP: %s", event, user, ip);
    }

    /**
     * 获取安全事件日志级别
     *
     * @param event 事件类型
     * @return 日志级别
     */
    public String getSecurityLogLevel(final String event) {
        // 认证失败等敏感事件使用WARN级别
        if (event.contains("failure") || event.contains("error")) {
            return "WARN";
        }
        return "INFO";
    }
}