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

import java.util.HashMap;
import java.util.Map;

/**
 * 业务事件日志构建器 - v2.16.3
 *
 * 从DefaultStructuredLogger提取的业务事件日志数据构建逻辑：
 * - 构建业务事件日志字段
 * - 构建负载均衡决策日志字段
 * - 构建限流检查日志字段
 * - 构建熔断状态变更日志字段
 *
 * 设计模式：Builder Pattern（构建器模式）
 * 注意：只负责构建日志数据，不负责输出
 *
 * @author JAiRouter Team
 * @since v2.16.3
 */
@Component
public class BusinessEventLogBuilder {

    /**
     * 构建业务事件日志字段
     *
     * @param event 事件名称
     * @param data 事件数据
     * @return 日志字段Map
     */
    public Map<String, Object> buildBusinessEventFields(final String event,
                                                         final Map<String, Object> data) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("event", event);

        if (data != null && !data.isEmpty()) {
            fields.put("data", data);
        }

        return fields;
    }

    /**
     * 构建负载均衡决策日志字段
     *
     * @param strategy 策略名称
     * @param selectedInstance 选中的实例
     * @param availableInstances 可用实例数量
     * @return 日志字段Map
     */
    public Map<String, Object> buildLoadBalancerDecisionFields(final String strategy,
                                                               final String selectedInstance,
                                                               final int availableInstances) {
        Map<String, Object> data = new HashMap<>();
        data.put("strategy", strategy);
        data.put("selectedInstance", selectedInstance);
        data.put("availableInstances", availableInstances);

        return buildBusinessEventFields("load_balancer_decision", data);
    }

    /**
     * 构建限流检查日志字段
     *
     * @param algorithm 算法名称
     * @param allowed 是否允许
     * @param remainingTokens 剩余令牌数
     * @return 日志字段Map
     */
    public Map<String, Object> buildRateLimitCheckFields(final String algorithm,
                                                          final boolean allowed,
                                                          final long remainingTokens) {
        Map<String, Object> data = new HashMap<>();
        data.put("algorithm", algorithm);
        data.put("allowed", allowed);
        data.put("remainingTokens", remainingTokens);

        return buildBusinessEventFields("rate_limit_check", data);
    }

    /**
     * 构建熔断状态变更日志字段
     *
     * @param previousState 前一个状态
     * @param currentState 当前状态
     * @param reason 原因
     * @return 日志字段Map
     */
    public Map<String, Object> buildCircuitBreakerStateChangeFields(final String previousState,
                                                                     final String currentState,
                                                                     final String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("previousState", previousState);
        data.put("currentState", currentState);
        data.put("reason", reason);

        return buildBusinessEventFields("circuit_breaker_state_change", data);
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