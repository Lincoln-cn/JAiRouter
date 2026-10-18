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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试辅助端点的 profile 门控与归属（issue #483）。
 *
 * <p>回归点：{@code POST /api/state-persistence/test/save-circuit-breaker/{id}} 原先挂在
 * {@link StatePersistenceManagementController} 上，**没有任何 profile 门控** ⇒ 生产环境也能调用，
 * 往持久化层写合成的熔断状态。</p>
 *
 * <p><b>这是元数据断言，不是上下文测试</b>：用 {@code AnnotationConfigApplicationContext}
 * 注册该控制器时，Spring 会去实例化真实的 {@code CompositeStatePersistenceServiceImpl}
 * 并连带拉起它的整条依赖链（实测报 {@code field 'h2Service' 无 bean}），代价远大于本断言的价值。
 * 因此这里断言"类上确实有 {@code @Profile("!prod")}"与"路径已搬到独立类、原控制器不再声明",
 * 这两条一旦被回退会立刻变红。</p>
 */
@DisplayName("状态持久化测试端点：@Profile(!prod) 且不再挂在管理控制器上")
class StatePersistenceTestControllerProfileTest {

    @Test
    @DisplayName("独立类带 @Profile(\"!prod\")")
    void declaresNotProdProfile() {
        Profile profile = StatePersistenceTestController.class.getAnnotation(Profile.class);
        assertNotNull(profile, "缺少 @Profile ⇒ 生产环境也会注册该端点");
        assertArrayEquals(new String[]{"!prod"}, profile.value(),
                "必须是 !prod（只排除生产），实际=" + Arrays.toString(profile.value()));
    }

    @Test
    @DisplayName("路径落在 /api/state-persistence/test 下")
    void declaresTestPath() {
        RequestMapping mapping = StatePersistenceTestController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertTrue(Arrays.asList(mapping.value()).contains("/api/state-persistence/test"),
                "实际=" + Arrays.toString(mapping.value()));
    }

    @Test
    @DisplayName("原管理控制器不再声明该测试端点（防止两处重复暴露）")
    void oldControllerNoLongerExposesIt() {
        for (Method m : StatePersistenceManagementController.class.getDeclaredMethods()) {
            PostMapping pm = m.getAnnotation(PostMapping.class);
            if (pm == null) {
                continue;
            }
            for (String p : pm.value()) {
                assertFalse(p.contains("/test/save-circuit-breaker"),
                        "测试端点不应再出现在管理控制器上：" + m.getName() + " -> " + p);
            }
        }
    }
}
