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
import org.unreal.modelrouter.monitor.tracing.logger.dto.SecurityEventFields;

/**
 * 安全事件日志构建器 - v2.16.4重构
 *
 * 返回强类型SecurityEventFields DTO，替代Map<String, Object>弱约束方案：
 * - 构建安全事件日志字段
 * - 构建认证事件日志字段
 * - 构建数据脱敏日志字段
 * - 构建配置变更日志字段
 *
 * 设计模式：Builder Pattern（构建器模式）
 * 注意：只负责构建日志字段数据，不负责输出
 *
 * @author JAiRouter Team
 * @since v2.16.1 (重构于 v2.16.4)
 */
@Component
public class SecurityEventLogBuilder {

    /**
     * 构建安全事件日志字段
     *
     * @param event 事件类型
     * @param user 用户
     * @param ip IP地址
     * @return 强类型SecurityEventFields DTO
     */
    public SecurityEventFields buildSecurityEvent(final String event,
                                                    final String user,
                                                    final String ip) {
        return SecurityEventFields.builder()
                .event(event)
                .user(user)
                .ip(ip)
                .build();
    }

    /**
     * 构建认证事件日志字段
     *
     * @param success 是否成功
     * @param authMethod 认证方式
     * @param user 用户
     * @param ip IP地址
     * @return 强类型SecurityEventFields DTO
     */
    public SecurityEventFields buildAuthenticationEvent(final boolean success,
                                                          final String authMethod,
                                                          final String user,
                                                          final String ip) {
        return SecurityEventFields.forAuthentication(success, authMethod, user, ip);
    }

    /**
     * 构建数据脱敏日志字段
     *
     * @param field 字段名
     * @param action 动作
     * @param ruleId 规则ID
     * @return 强类型SecurityEventFields DTO
     */
    public SecurityEventFields buildSanitizationEvent(final String field,
                                                        final String action,
                                                        final String ruleId) {
        return SecurityEventFields.forSanitization(field, action, ruleId);
    }

    /**
     * 构建配置变更日志字段
     *
     * @param configType 配置类型
     * @param action 动作
     * @param details 详情
     * @return 强类型SecurityEventFields DTO
     */
    public SecurityEventFields buildConfigurationChangeEvent(final String configType,
                                                               final String action,
                                                               final java.util.Map<String, Object> details) {
        return SecurityEventFields.forConfigurationChange(configType, action, details);
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