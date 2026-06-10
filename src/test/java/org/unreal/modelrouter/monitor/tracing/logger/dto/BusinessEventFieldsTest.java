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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BusinessEventFields测试
 */
@DisplayName("BusinessEventFields测试")
class BusinessEventFieldsTest {

    @Test
    @DisplayName("测试Builder构建")
    void testBuilder() {
        Map<String, Object> data = Map.of("key", "value");
        BusinessEventFields fields = BusinessEventFields.builder()
                .event("test_event")
                .data(data)
                .build();

        assertEquals("test_event", fields.getEvent());
        assertEquals(data, fields.getData());
    }

    @Test
    @DisplayName("测试getFieldType方法")
    void testGetFieldType() {
        assertEquals("business_event", new BusinessEventFields().getFieldType());
    }

    @Test
    @DisplayName("测试create静态方法")
    void testCreate() {
        Map<String, Object> data = Map.of("test", 123);
        BusinessEventFields fields = BusinessEventFields.create("custom_event", data);

        assertEquals("custom_event", fields.getEvent());
        assertEquals(data, fields.getData());
    }

    @Nested
    @DisplayName("forLoadBalancerDecision测试")
    class ForLoadBalancerDecisionTests {

        @Test
        @DisplayName("负载均衡决策事件")
        void testLoadBalancerDecision() {
            BusinessEventFields fields = BusinessEventFields.forLoadBalancerDecision(
                    "round-robin", "instance-1", 5
            );

            assertEquals("load_balancer_decision", fields.getEvent());
            assertEquals("round-robin", fields.getData().get("strategy"));
            assertEquals("instance-1", fields.getData().get("selectedInstance"));
            assertEquals(5, fields.getData().get("availableInstances"));
        }
    }

    @Nested
    @DisplayName("forRateLimitCheck测试")
    class ForRateLimitCheckTests {

        @Test
        @DisplayName("限流检查事件 - 允许")
        void testRateLimitCheckAllowed() {
            BusinessEventFields fields = BusinessEventFields.forRateLimitCheck(
                    "token_bucket", true, 50L
            );

            assertEquals("rate_limit_check", fields.getEvent());
            assertEquals("token_bucket", fields.getData().get("algorithm"));
            assertTrue((Boolean) fields.getData().get("allowed"));
            assertEquals(50L, fields.getData().get("remainingTokens"));
        }

        @Test
        @DisplayName("限流检查事件 - 拒绝")
        void testRateLimitCheckDenied() {
            BusinessEventFields fields = BusinessEventFields.forRateLimitCheck(
                    "sliding_window", false, 0L
            );

            assertFalse((Boolean) fields.getData().get("allowed"));
            assertEquals(0L, fields.getData().get("remainingTokens"));
        }
    }

    @Nested
    @DisplayName("forCircuitBreakerStateChange测试")
    class ForCircuitBreakerStateChangeTests {

        @Test
        @DisplayName("熔断状态变更事件")
        void testCircuitBreakerStateChange() {
            BusinessEventFields fields = BusinessEventFields.forCircuitBreakerStateChange(
                    "CLOSED", "OPEN", "error_threshold_exceeded"
            );

            assertEquals("circuit_breaker_state_change", fields.getEvent());
            assertEquals("CLOSED", fields.getData().get("previousState"));
            assertEquals("OPEN", fields.getData().get("currentState"));
            assertEquals("error_threshold_exceeded", fields.getData().get("reason"));
        }
    }
}
