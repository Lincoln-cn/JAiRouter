/*
 * Copyright 2024 JAiRouter Authors
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

package org.unreal.modelrouter.router.handler;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.unreal.modelrouter.router.adapter.ServiceCapability;
import reactor.core.publisher.Mono;

/**
 * 服务请求执行器函数式接口.
 *
 * <p>用于封装特定服务类型的请求执行逻辑，
 * 支持不同的适配器方法调用（chat、embedding、rerank等）。
 *
 * @author JAiRouter Team
 * @since 2.10.0
 */
@FunctionalInterface
public interface ServiceRequestExecutor {

    /**
     * 执行服务请求.
     *
     * @param adapter 服务适配器
     * @param authorization 认证头信息
     * @param httpRequest HTTP请求对象
     * @return 响应实体的Mono
     * @throws Exception 执行过程中可能抛出的异常
     */
    Mono<ResponseEntity<?>> execute(
        ServiceCapability adapter,
        String authorization,
        ServerHttpRequest httpRequest
    ) throws Exception;
}
