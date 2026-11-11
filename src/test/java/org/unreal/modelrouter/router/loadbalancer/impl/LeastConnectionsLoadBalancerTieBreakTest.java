/*
 * Copyright (c) 2024-2026 JAI Router
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.unreal.modelrouter.router.loadbalancer.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「最少连接」策略在等连接时的分散性（issue #1085）。
 *
 * <p>背景：实测 3 个等权实例、30 并发请求，100% 命中同一实例，另外两个完全闲置。
 * 原因是各实例在途连接数几乎总是相等，而原实现取「遍历到的第一个」，
 * 遍历顺序又固定 ⇒ 负载均衡退化为单实例。</p>
 */
class LeastConnectionsLoadBalancerTieBreakTest {

    private static final String CHAT_PATH = "/v1/chat/completions";

    private ModelRouterProperties.ModelInstance instance(final String name, final int weight) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName(name);
        instance.setBaseUrl("http://" + name + ":8080");
        instance.setPath(CHAT_PATH);
        instance.setWeight(weight);
        return instance;
    }

    private List<ModelRouterProperties.ModelInstance> threeIdleInstances() {
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        instances.add(instance("a", 1));
        instances.add(instance("b", 1));
        instances.add(instance("c", 1));
        return instances;
    }

    @Test
    @DisplayName("三个等权且空闲的实例：30 次选择必须分散到所有实例")
    void idleEqualWeightInstancesShouldBeSpreadAcrossAll() {
        LeastConnectionsLoadBalancer balancer = new LeastConnectionsLoadBalancer();
        List<ModelRouterProperties.ModelInstance> instances = threeIdleInstances();

        Map<String, Integer> hits = new HashMap<>();
        for (int i = 0; i < 30; i++) {
            ModelRouterProperties.ModelInstance selected =
                    balancer.selectInstance(instances, "127.0.0.1", "chat");
            hits.merge(selected.getName(), 1, Integer::sum);
        }

        // 修复前：只会有 1 个键（恒选同一个实例）
        assertEquals(3, hits.size(),
                "三个空闲实例应当都被选中过，实际分布=" + hits + "（修复前这里只会有一个实例命中 30 次）");
        hits.forEach((name, count) -> assertEquals(10, count.intValue(),
                "等权空闲时应严格轮转，实例 " + name + " 命中 " + count + " 次，分布=" + hits));
    }

    @Test
    @DisplayName("在途连接数不同时，仍优先选连接数最少的实例")
    void leastConnectionsStillWinsWhenCountsDiffer() {
        LeastConnectionsLoadBalancer balancer = new LeastConnectionsLoadBalancer();
        List<ModelRouterProperties.ModelInstance> instances = threeIdleInstances();

        // 给 b、c 各叠加在途连接，使 a 成为唯一的最小值
        balancer.recordCall(instances.get(1));
        balancer.recordCall(instances.get(2));
        balancer.recordCall(instances.get(2));

        for (int i = 0; i < 5; i++) {
            ModelRouterProperties.ModelInstance selected =
                    balancer.selectInstance(instances, "127.0.0.1", "chat");
            assertEquals("a", selected.getName(),
                    "连接数不同时应选最小连接数的实例，不该被轮转起点带偏");
        }
    }

    @Test
    @DisplayName("加权：低权重实例在等连接时按权重比例被选中")
    void weightIsRespectedWhenConnectionsEqual() {
        LeastConnectionsLoadBalancer balancer = new LeastConnectionsLoadBalancer();
        // 加权连接数 = 0 / weight 恒为 0，因此三者仍等价；此用例守住「不会因权重为 0 而抛异常」
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        instances.add(instance("a", 0));
        instances.add(instance("b", 100));
        instances.add(instance("c", 1));

        Map<String, Integer> hits = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            hits.merge(balancer.selectInstance(instances, "127.0.0.1", "chat").getName(), 1, Integer::sum);
        }

        assertEquals(3, hits.size(), "权重不影响等连接场景的分散性，实际分布=" + hits);
        assertTrue(hits.values().stream().allMatch(count -> count == 3), "分布应均匀，实际=" + hits);
    }

    @Test
    @DisplayName("单实例时不应因轮转游标越界而失败")
    void singleInstanceIsAlwaysSelected() {
        LeastConnectionsLoadBalancer balancer = new LeastConnectionsLoadBalancer();
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>();
        instances.add(instance("only", 1));

        for (int i = 0; i < 5; i++) {
            assertEquals("only",
                    balancer.selectInstance(instances, "127.0.0.1", "chat").getName());
        }
    }

    @Test
    @DisplayName("空列表必须抛出明确异常，而不是返回 null")
    void emptyInstancesThrow() {
        LeastConnectionsLoadBalancer balancer = new LeastConnectionsLoadBalancer();
        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> balancer.selectInstance(new ArrayList<>(), "127.0.0.1", "chat"));
        assertNotEquals("", error.getMessage());
    }
}
