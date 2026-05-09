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
import org.unreal.modelrouter.monitor.tracing.logger.dto.BusinessEventFields;

import java.util.Map;

/**
 * 业务事件日志构建器 - v2.16.4重构
 *
 * 返回强类型BusinessEventFields DTO，替代Map<String, Object>弱约束方案：
 * - 构建业务事件日志字段
 * - 构建负载均衡决策日志字段
 * - 构建限流检查日志字段
 * - 构建熔断状态变更日志字段
 *
 * 设计模式：Builder Pattern（构建器模式）
 * 注意：只负责构建日志字段数据，不负责输出
 *
 * @author JAiRouter Team
 * @since v2.16.3 (重构于 v2.16.4)
 */
@Component
public class BusinessEventLogBuilder {

    /**
     * 构建业务事件日志字段
     *
     * @param event 事件名称
     * @param data 事件数据
     * @return 强类型BusinessEventFields DTO
     */
    public BusinessEventFields buildBusinessEvent(final String event,
                                                    final Map<String, Object> data) {
        return BusinessEventFields.create(event, data);
    }

    /**
     * 构建负载均衡决策日志字段
     *
     * @param strategy 策略名称
     * @param selectedInstance 选中的实例
     * @param availableInstances 可用实例数量
     * @return 强类型BusinessEventFields DTO
     */
    public BusinessEventFields buildLoadBalancerDecision(final String strategy,
                                                           final String selectedInstance,
                                                           final int availableInstances) {
        return BusinessEventFields.forLoadBalancerDecision(strategy, selectedInstance, availableInstances);
    }

    /**
     * 构建限流检查日志字段
     *
     * @param algorithm 算法名称
     * @param allowed 是否允许
     * @param remainingTokens 剩余令牌数
     * @return 强类型BusinessEventFields DTO
     */
    public BusinessEventFields buildRateLimitCheck(final String algorithm,
                                                     final boolean allowed,
                                                     final long remainingTokens) {
        return BusinessEventFields.forRateLimitCheck(algorithm, allowed, remainingTokens);
    }

    /**
     * 构建熔断状态变更日志字段
     *
     * @param previousState 前一个状态
     * @param currentState 当前状态
     * @param reason 原因
     * @return 强类型BusinessEventFields DTO
     */
    public BusinessEventFields buildCircuitBreakerStateChange(final String previousState,
                                                                final String currentState,
                                                                final String reason) {
        return BusinessEventFields.forCircuitBreakerStateChange(previousState, currentState, reason);
    }

    /**
     * 构建业务事件日志消息
     *
     * @param event 事件名称
     * @return 日志消息
     */
    public String buildBusinessEventMessage(final String event) {
        return String.format("业务事件: %s", event);
    }

    /**
     * 构建负载均衡决策日志消息
     *
     * @param strategy 策略名称
     * @param selectedInstance 选中的实例
     * @return 日志消息
     */
    public String buildLoadBalancerDecisionMessage(final String strategy,
                                                     final String selectedInstance) {
        return String.format("负载均衡决策: 策略=%s，选中实例=%s", strategy, selectedInstance);
    }

    /**
     * 构建限流检查日志消息
     *
     * @param allowed 是否允许
     * @param remainingTokens 剩余令牌数
     * @return 日志消息
     */
    public String buildRateLimitCheckMessage(final boolean allowed,
                                               final long remainingTokens) {
        return String.format("限流检查: 允许=%s，剩余令牌=%d", allowed, remainingTokens);
    }

    /**
     * 构建熔断状态变更日志消息
     *
     * @param previousState 前一个状态
     * @param currentState 当前状态
     * @param reason 原因
     * @return 日志消息
     */
    public String buildCircuitBreakerStateChangeMessage(final String previousState,
                                                          final String currentState,
                                                          final String reason) {
        return String.format("熔断状态变更: %s -> %s，原因: %s", previousState, currentState, reason);
    }
}