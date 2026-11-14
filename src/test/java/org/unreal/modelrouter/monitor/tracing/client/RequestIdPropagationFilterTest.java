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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.unreal.modelrouter.common.filter.RequestIdWebFilter;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 出站请求 ID 透传过滤器测试
 *
 * <p>守护的事实：Reactor Context 里有 traceId 时，上游请求头必须带 {@code X-Request-Id}
 * 与 {@code X-Trace-Id}，且重复调用不产生重复头；没有 traceId（定时任务等）时不得凭空造值。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.8
 */
@DisplayName("出站请求 ID 透传（X-Request-Id → 上游）")
class RequestIdPropagationFilterTest {

    private final RequestIdPropagationFilter filter = new RequestIdPropagationFilter();
    private final AtomicReference<ClientRequest> captured = new AtomicReference<>();

    private ExchangeFunction capturingNext() {
        return request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        };
    }

    private static ClientRequest upstreamRequest() {
        return ClientRequest.create(HttpMethod.POST, URI.create("http://upstream/v1/chat/completions")).build();
    }

    @Test
    @DisplayName("Context 里有 traceId 时写入两个请求头")
    void addsHeadersWhenContextHasTraceId() {
        filter.filter(upstreamRequest(), capturingNext())
                .contextWrite(Context.of(RequestIdWebFilter.MDC_TRACE_ID_KEY, "req-abc-123"))
                .block();

        final ClientRequest sent = captured.get();
        assertEquals("req-abc-123", sent.headers().getFirst("X-Request-Id"));
        assertEquals("req-abc-123", sent.headers().getFirst("X-Trace-Id"));
    }

    @Test
    @DisplayName("没有 traceId 时原样放行，不凭空造值")
    void passesThroughWithoutTraceId() {
        filter.filter(upstreamRequest(), capturingNext()).block();

        assertNull(captured.get().headers().getFirst("X-Request-Id"));
        assertNull(captured.get().headers().getFirst("X-Trace-Id"));
    }

    @Test
    @DisplayName("已有同名头时覆盖而不是追加，避免重试时出现重复头")
    void overwritesInsteadOfAppending() {
        final ClientRequest withOldValue = ClientRequest.from(upstreamRequest())
                .header("X-Request-Id", "stale-value")
                .build();

        filter.filter(withOldValue, capturingNext())
                .contextWrite(Context.of(RequestIdWebFilter.MDC_TRACE_ID_KEY, "req-new"))
                .block();

        final List<String> values = captured.get().headers().get("X-Request-Id");
        assertEquals(1, values.size());
        assertEquals("req-new", values.get(0));
    }
}
