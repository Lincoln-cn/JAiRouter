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

/**
 * 降级策略接口.
 *
 * <p>#823：契约里必须带请求键。此前只有 {@code fallback(Exception cause)}，
 * 策略实现无法知道「这次降级属于哪个请求」，只能返回缓存里的任意一条，
 * 等于把别人的响应发给当前调用方。</p>
 *
 * @param <T> 降级结果类型
 */
public interface FallbackStrategy<T> {

    /** 命中降级缓存时附加的响应头（#823）。 */
    String FALLBACK_HEADER = "X-Fallback";

    /** 命中降级缓存时的响应头取值（#823）。 */
    String FALLBACK_HEADER_CACHE_VALUE = "cache";

    /**
     * 执行降级逻辑.
     *
     * @param key   当前请求的键；{@code null} 表示该请求不参与降级缓存（例如请求体不可序列化），
     *              此时实现必须退化为「不命中缓存」，不得返回任何其它请求的条目
     * @param cause 引起降级的异常或原因
     * @return 降级返回的结果
     */
    T fallback(FallbackRequestKey key, Exception cause);
}
