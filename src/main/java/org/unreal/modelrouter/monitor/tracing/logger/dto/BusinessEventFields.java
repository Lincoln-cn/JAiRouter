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
 * 业务事件日志字段DTO - v2.16.4
 *
 * 替代Map<String, Object>的强类型方案：
 * - event: 事件名称
 * - data: 事件数据
 *
 * 子类型包括：
 * - load_balancer_decision: 负载均衡决策
 * - rate_limit_check: 限流检查
 * - circuit_breaker_state_change: 熔断状态变更
 *
 * @author JAiRouter Team
 * @since v2.16.4
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BusinessEventFields implements LogFields {

    /**
     * 事件名称
     * 例如：load_balancer_decision, rate_limit_check, circuit_breaker_state_change
     */
    private String event;

    /**
     * 事件数据（Map存储动态数据）
     */
    private Map<String, Object> data;

    @Override
    public String getFieldType() {
        return "business_event";
    }

    /**
     * 创建业务事件字段
     */
    public static BusinessEventFields create(final String event,
                                               final Map<String, Object> data) {
        return BusinessEventFields.builder()
                .event(event)
                .data(data)
                .build();
    }

    /**
     * 创建负载均衡决策事件字段
     */
    public static BusinessEventFields forLoadBalancerDecision(final String strategy,
                                                               final String selectedInstance,
                                                               final Integer availableInstances) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("strategy", strategy);
        data.put("selectedInstance", selectedInstance);
        data.put("availableInstances", availableInstances);

        return create("load_balancer_decision", data);
    }

    /**
     * 创建限流检查事件字段
     */
    public static BusinessEventFields forRateLimitCheck(final String algorithm,
                                                          final Boolean allowed,
                                                          final Long remainingTokens) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("algorithm", algorithm);
        data.put("allowed", allowed);
        data.put("remainingTokens", remainingTokens);

        return create("rate_limit_check", data);
    }

    /**
     * 创建熔断状态变更事件字段
     */
    public static BusinessEventFields forCircuitBreakerStateChange(final String previousState,
                                                                     final String currentState,
                                                                     final String reason) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("previousState", previousState);
        data.put("currentState", currentState);
        data.put("reason", reason);

        return create("circuit_breaker_state_change", data);
    }
}