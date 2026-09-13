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

package org.unreal.modelrouter.router.anthropic;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Anthropic {@code POST /v1/messages/count_tokens} 响应体（v3.1 PR-4c）.
 *
 * <p>协议形状：{@code {"input_tokens": N}}。数值由 {@link AnthropicTokenEstimator}
 * 估算（与流式 {@code message_start.usage.input_tokens} 同源）。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AnthropicCountTokensResponse(
        @JsonProperty("input_tokens") long inputTokens) {
}
