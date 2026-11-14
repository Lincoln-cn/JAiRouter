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

package org.unreal.modelrouter.monitor.tracing.client;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.unreal.modelrouter.common.filter.RequestIdWebFilter;
import org.unreal.modelrouter.monitor.tracing.TracingConstants;
import reactor.core.publisher.Mono;

/**
 * 出站请求 ID 透传过滤器
 *
 * <p>把当前请求的 ID 带到上游模型服务的请求头里，让「网关日志 → 上游日志」可以按同一个值串起来。
 * 值的来源是 {@link RequestIdWebFilter} 写入 Reactor Context 的 {@code traceId}：WebFlux 会在线程间
 * 切换，读 Reactor Context 而不是 ThreadLocal 才能保证取到正确的请求。</p>
 *
 * <p>取不到值时不做任何修改，直接放行 —— 定时任务、健康检查探针等非请求链路本来就没有请求 ID。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.8
 */
@Component
public class RequestIdPropagationFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(final ClientRequest request, final ExchangeFunction next) {
        return Mono.deferContextual(contextView -> {
            final String traceId = contextView.getOrDefault(RequestIdWebFilter.MDC_TRACE_ID_KEY, null);
            if (traceId == null || traceId.isEmpty()) {
                return next.exchange(request);
            }
            final ClientRequest decorated = ClientRequest.from(request)
                    .headers(headers -> {
                        // set 而不是 add：同一请求可能被重试多次，不允许出现重复头
                        headers.set(TracingConstants.Headers.X_REQUEST_ID, traceId);
                        headers.set(TracingConstants.Headers.X_TRACE_ID, traceId);
                    })
                    .build();
            return next.exchange(decorated);
        });
    }
}
