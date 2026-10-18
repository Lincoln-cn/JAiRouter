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

package org.unreal.modelrouter.persistence.store.persistence.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.persistence.store.persistence.CompositeStatePersistenceServiceImpl;
import org.unreal.modelrouter.persistence.store.persistence.StatePersistenceService.StateType;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 状态持久化的**测试辅助端点**（issue #483）。
 *
 * <p>原先这个方法挂在 {@link StatePersistenceManagementController} 上
 * （`/api/state-persistence/test/...`），没有任何 profile 门控 ⇒ 生产环境也能调用，
 * 往持久化层写入**合成的**熔断状态，污染「状态持久化」面板与后续恢复出来的状态。</p>
 *
 * <p>现在独立成类并加 {@code @Profile("!prod")}：生产 profile 下该 bean 不注册，
 * 端点不出现在生产契约里。它所需的权限码 {@code config:persistence:write} 仍照旧生效
 * （规则在 {@code PermissionRuleRegistry}，与本次改动无关）。</p>
 */
@RestController
@RequestMapping("/api/state-persistence/test")
@Profile("!prod")
public class StatePersistenceTestController {

    private static final Logger logger = LoggerFactory.getLogger(StatePersistenceTestController.class);

    private final CompositeStatePersistenceServiceImpl compositePersistenceService;

    public StatePersistenceTestController(
            final CompositeStatePersistenceServiceImpl compositePersistenceService) {
        this.compositePersistenceService = compositePersistenceService;
    }

    /**
     * 测试用：手动保存熔断器状态（仅非生产 profile）。
     *
     * @param instanceId 实例 ID
     * @return 保存结果
     */
    @PostMapping("/save-circuit-breaker/{instanceId}")
    public Mono<ResponseEntity<Map<String, Object>>> testSaveCircuitBreakerState(
            @PathVariable final String instanceId) {

        logger.info("Test save circuit breaker state for instance: {}", instanceId);

        Map<String, Object> stateData = new HashMap<>();
        stateData.put("instanceId", instanceId);
        stateData.put("state", "OPEN");
        stateData.put("failureCount", 5);
        stateData.put("successCount", 0);
        stateData.put("timestamp", System.currentTimeMillis());

        return compositePersistenceService.save(StateType.CIRCUIT_BREAKER, instanceId, stateData)
                .map(saved -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("instanceId", instanceId);
                    data.put("saved", saved);
                    data.put("stateData", stateData);

                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("data", data);
                    return ResponseEntity.ok(response);
                });
    }
}
