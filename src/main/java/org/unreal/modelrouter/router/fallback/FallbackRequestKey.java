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

package org.unreal.modelrouter.router.fallback;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * 降级缓存请求键（#823）.
 *
 * <p>降级缓存必须按请求隔离：同一个服务、同一个模型，不同调用方或不同请求体不能互相命中，
 * 否则一次失败的请求可能拿到别人上一次成功的响应。这个类把「服务类型 + 模型 + 调用方 +
 * 请求体哈希」拼成一个稳定键。</p>
 *
 * <p>调用方拿不到稳定哈希时（请求体无法序列化）{@link #of} 返回 {@code null}，
 * 降级策略必须把 {@code null} 当作「不可缓存 / 不可命中」处理，宁可返回 503 也不能串响应。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.7
 */
public final class FallbackRequestKey {

    private static final Logger logger = LoggerFactory.getLogger(FallbackRequestKey.class);

    private static final String ANONYMOUS = "anonymous";

    private final String serviceType;

    private final String modelName;

    private final String callerId;

    private final String requestHash;

    private FallbackRequestKey(final String serviceType, final String modelName,
            final String callerId, final String requestHash) {
        this.serviceType = serviceType;
        this.modelName = modelName;
        this.callerId = callerId;
        this.requestHash = requestHash;
    }

    /**
     * 构造请求键.
     *
     * @param serviceType 服务类型名，可为 {@code null}
     * @param modelName   模型名，可为 {@code null}
     * @param callerId    调用方标识（API Key ID 等），可为 {@code null}
     * @param request     原始请求对象（{@code toString} 必须稳定，DTO 由 Lombok 提供）
     * @param objectMapper 用于把请求体规范化成 JSON 的映射器，为 {@code null} 时退回 {@code toString}
     * @return 请求键；请求对象本身为 {@code null} 时返回 {@code null}（不可缓存）
     */
    public static FallbackRequestKey of(final String serviceType, final String modelName,
            final String callerId, final Object request, final ObjectMapper objectMapper) {
        if (request == null) {
            return null;
        }
        final String payload;
        if (objectMapper == null) {
            payload = String.valueOf(request);
        } else {
            try {
                payload = objectMapper.writeValueAsString(request);
            } catch (final Exception e) {
                logger.debug("降级请求键无法序列化请求体，本条请求不参与降级缓存: {}", e.getMessage());
                return null;
            }
        }
        final String requestHash = sha256(payload);
        if (requestHash == null) {
            return null;
        }
        return new FallbackRequestKey(
                serviceType == null ? "unknown" : serviceType,
                modelName == null ? "unknown" : modelName,
                callerId == null || callerId.isBlank() ? ANONYMOUS : callerId,
                requestHash);
    }

    /**
     * 缓存键（可直接作为 Map 键）.
     *
     * @return 由四个维度拼成的字符串
     */
    public String asCacheKey() {
        return serviceType + ':' + modelName + ':' + callerId + ':' + requestHash;
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getModelName() {
        return modelName;
    }

    public String getCallerId() {
        return callerId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FallbackRequestKey)) {
            return false;
        }
        final FallbackRequestKey that = (FallbackRequestKey) other;
        return Objects.equals(serviceType, that.serviceType)
                && Objects.equals(modelName, that.modelName)
                && Objects.equals(callerId, that.callerId)
                && Objects.equals(requestHash, that.requestHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceType, modelName, callerId, requestHash);
    }

    @Override
    public String toString() {
        return asCacheKey();
    }

    private static String sha256(final String payload) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            final StringBuilder builder = new StringBuilder(hash.length * 2);
            for (final byte value : hash) {
                builder.append(Character.forDigit((value >> 4) & 0xF, 16));
                builder.append(Character.forDigit(value & 0xF, 16));
            }
            return builder.toString();
        } catch (final Exception e) {
            logger.warn("计算降级请求键哈希失败，本条请求不参与降级缓存: {}", e.getMessage());
            return null;
        }
    }
}
