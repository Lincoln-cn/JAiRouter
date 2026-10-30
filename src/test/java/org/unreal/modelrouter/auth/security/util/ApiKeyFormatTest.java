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

package org.unreal.modelrouter.auth.security.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ApiKeyFormat} 的单元测试（#826）.
 *
 * @author JAiRouter Team
 */
@DisplayName("ApiKeyFormat 密钥文本格式")
class ApiKeyFormatTest {

    @Test
    @DisplayName("826-001 新格式能编进 keyId 并原样解析回来")
    void composeEmbedsKeyId() {
        String value = ApiKeyFormat.compose("key-1234abcd", 32);
        assertTrue(value.startsWith("sk-"), "密钥必须以 sk- 开头，否则 validateFormat 直接拒绝");
        assertTrue(value.contains("key-1234abcd"));
        assertEquals(Optional.of("key-1234abcd"), ApiKeyFormat.keyIdOf(value));
        assertFalse(ApiKeyFormat.isLegacy(value));
        assertTrue(value.length() > "sk-key-1234abcd.".length() + 16, "随机部分不能短于 16");
    }

    @Test
    @DisplayName("826-002 旧格式（无分隔符）解析不出 keyId，按旧行为处理")
    void legacyKeyHasNoKeyId() {
        String legacy = ApiKey.generateApiKey("sk-", 32);
        assertEquals(Optional.empty(), ApiKeyFormat.keyIdOf(legacy));
        assertTrue(ApiKeyFormat.isLegacy(legacy));
    }

    @Test
    @DisplayName("826-003 含非法字符的 keyId 不能编进密钥文本")
    void keyIdWithSeparatorIsRejected() {
        assertFalse(ApiKeyFormat.canEmbed("key.1"));
        assertFalse(ApiKeyFormat.canEmbed("key:1"));
        assertFalse(ApiKeyFormat.canEmbed(""));
        assertFalse(ApiKeyFormat.canEmbed(null));
        assertThrows(IllegalArgumentException.class, () -> ApiKeyFormat.compose("key.1", 32));
    }

    @Test
    @DisplayName("826-004 畸形文本一律按旧格式处理，不抛异常")
    void malformedValuesFallBackToLegacy() {
        assertEquals(Optional.empty(), ApiKeyFormat.keyIdOf(null));
        assertEquals(Optional.empty(), ApiKeyFormat.keyIdOf("not-a-key"));
        assertEquals(Optional.empty(), ApiKeyFormat.keyIdOf("sk-abcdef"));
        assertEquals(Optional.empty(), ApiKeyFormat.keyIdOf("sk-abcdef."));
        assertEquals(Optional.empty(), ApiKeyFormat.keyIdOf("sk-.abcdef"));
    }
}
