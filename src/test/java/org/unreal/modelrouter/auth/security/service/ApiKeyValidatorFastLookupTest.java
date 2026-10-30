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

package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.util.ApiKeyFormat;
import org.unreal.modelrouter.auth.security.util.ApiKeyHashUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApiKeyValidator#findMatchingKey} 的查找路径测试（#826）.
 *
 * <p>守护的契约是「新格式按键内 keyId 定位，旧格式才遍历」：缓存里放一个
 * {@code verifyKey} 会抛错的诱饵条目，只要新格式命中了索引，就证明没有走遍历。</p>
 *
 * @author JAiRouter Team
 */
@DisplayName("ApiKeyValidator 按 keyId 快速查找")
class ApiKeyValidatorFastLookupTest {

    private final ApiKeyValidator validator = new ApiKeyValidator();

    private static ApiKey registerKey(Map<String, ApiKey> cache, Map<String, String> index,
                                      String keyId, String keyValue) {
        ApiKey key = ApiKey.builder()
                .keyId(keyId)
                .keyHash(ApiKeyHashUtil.hashApiKey(keyValue))
                .permissions(List.of("chat"))
                .enabled(true)
                .build();
        cache.put(key.getKeyHash(), key);
        index.put(keyId, key.getKeyHash());
        return key;
    }

    private static ApiKey decoyThatMustNotBeScanned(Map<String, ApiKey> cache) {
        ApiKey decoy = mock(ApiKey.class);
        when(decoy.verifyKey(anyString())).thenThrow(new AssertionError("新格式命中索引时不应遍历缓存"));
        cache.put("decoy-hash", decoy);
        return decoy;
    }

    @Test
    @DisplayName("826-101 新格式按键内 keyId 命中，且不遍历其它条目")
    void newFormatUsesIndex() {
        Map<String, ApiKey> cache = new HashMap<>();
        Map<String, String> index = new HashMap<>();
        ApiKey decoy = decoyThatMustNotBeScanned(cache);
        String value = ApiKeyFormat.compose("key-abc123", 32);
        ApiKey key = registerKey(cache, index, "key-abc123", value);

        ApiKey matched = validator.findMatchingKey(value, cache, index);

        assertSame(key, matched);
        verify(decoy, never()).verifyKey(anyString());
    }

    @Test
    @DisplayName("826-102 新格式但索引里没有该 keyId：直接判无效，不回退遍历")
    void unknownKeyIdDoesNotFallBackToScan() {
        Map<String, ApiKey> cache = new HashMap<>();
        Map<String, String> index = new HashMap<>();
        ApiKey decoy = decoyThatMustNotBeScanned(cache);
        String value = ApiKeyFormat.compose("key-missing", 32);

        assertNull(validator.findMatchingKey(value, cache, index));
        verify(decoy, never()).verifyKey(anyString());
    }

    @Test
    @DisplayName("826-103 新格式但密钥部分不对：哈希校验失败，且不遍历其它条目")
    void wrongSecretIsRejected() {
        Map<String, ApiKey> cache = new HashMap<>();
        Map<String, String> index = new HashMap<>();
        ApiKey decoy = decoyThatMustNotBeScanned(cache);
        String value = ApiKeyFormat.compose("key-abc123", 32);
        registerKey(cache, index, "key-abc123", value);
        String tampered = value.substring(0, value.length() - 1) + (value.endsWith("a") ? "b" : "a");

        assertNull(validator.findMatchingKey(tampered, cache, index));
        verify(decoy, never()).verifyKey(anyString());
    }

    @Test
    @DisplayName("826-104 旧格式仍然可用：回退为遍历比较")
    void legacyFormatStillWorks() {
        Map<String, ApiKey> cache = new HashMap<>();
        Map<String, String> index = new HashMap<>();
        String legacy = ApiKey.generateApiKey("sk-", 32);
        registerKey(cache, index, "key-legacy", legacy);

        ApiKey matched = validator.findMatchingKey(legacy, cache, index);

        assertEquals("key-legacy", matched.getKeyId());
    }

    @Test
    @DisplayName("826-105 不传索引时行为不变，一律遍历")
    void withoutIndexKeepsOldBehaviour() {
        Map<String, ApiKey> cache = new HashMap<>();
        Map<String, String> index = new HashMap<>();
        String value = ApiKeyFormat.compose("key-abc123", 32);
        ApiKey key = registerKey(cache, index, "key-abc123", value);

        assertSame(key, validator.findMatchingKey(value, cache));
    }
}
