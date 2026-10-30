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

import java.security.SecureRandom;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * API Key 的文本格式（#826）.
 *
 * <p>历史格式是 {@code sk-<32 位随机串>}，密钥本身不含任何索引信息；而存储只保存
 * 「盐 + 哈希」（{@code ApiKeyHashUtil}），无法按哈希建索引，于是每次校验都只能遍历整个
 * 缓存逐个做哈希比较 —— 密钥越多，认证路径越慢。</p>
 *
 * <p>新格式是 {@code sk-<keyId>.<secret>}：校验时先按 {@link #keyIdOf} 取出 keyId，
 * 用现有的 {@code keyIdIndex} 直接定位到唯一一条记录再做哈希比较，复杂度从 O(n) 降到 O(1)。
 * 包含 keyId 与哈希校验两重把关，猜测 keyId 并不能绕过密钥校验。</p>
 *
 * <p>旧格式继续可用：解析不出 keyId 时由调用方回退到遍历比较（见
 * {@code ApiKeyValidator#findMatchingKey}），即「双格式并存」。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.7
 */
public final class ApiKeyFormat {

    /** 所有密钥共用的前缀，{@code ApiKeyValidator#validateFormat} 依赖它. */
    public static final String PREFIX = "sk-";

    /** keyId 与 secret 之间的分隔符：只允许自定义 keyId 里不出现的字符. */
    public static final char KEY_ID_SEPARATOR = '.';

    /** 允许出现在 keyId 里的字符（与 UUID、{@code key-xxxxxxxx} 两种既有形态兼容）. */
    private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private static final String SECRET_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private ApiKeyFormat() {
        // 工具类，禁止实例化
    }

    /**
     * 判断给定的 keyId 能否编进密钥文本.
     *
     * <p>含分隔符或非 {@code [A-Za-z0-9_-]} 的 keyId 无法无歧义地解析，调用方应回退到旧格式。</p>
     *
     * @param keyId 期望的 keyId
     * @return 可编码时返回 {@code true}
     */
    public static boolean canEmbed(final String keyId) {
        return keyId != null && KEY_ID_PATTERN.matcher(keyId).matches();
    }

    /**
     * 生成新格式的密钥文本.
     *
     * @param keyId        记录 ID，必须满足 {@link #canEmbed}
     * @param secretLength 随机部分长度（含校验用途，不低于 16）
     * @return {@code sk-<keyId>.<secret>}
     */
    public static String compose(final String keyId, final int secretLength) {
        if (!canEmbed(keyId)) {
            throw new IllegalArgumentException("keyId 不能编进密钥文本: " + keyId);
        }
        final int length = Math.max(secretLength, 16);
        final StringBuilder builder = new StringBuilder(PREFIX.length() + keyId.length() + 1 + length);
        builder.append(PREFIX).append(keyId).append(KEY_ID_SEPARATOR);
        for (int i = 0; i < length; i++) {
            builder.append(SECRET_CHARS.charAt(SECURE_RANDOM.nextInt(SECRET_CHARS.length())));
        }
        return builder.toString();
    }

    /**
     * 从密钥文本里取出被编码的 keyId.
     *
     * @param keyValue 请求里带来的密钥文本，可为 {@code null}
     * @return 旧格式（或格式不合法）时返回 {@link Optional#empty()}
     */
    public static Optional<String> keyIdOf(final String keyValue) {
        if (keyValue == null || !keyValue.startsWith(PREFIX)) {
            return Optional.empty();
        }
        final int separator = keyValue.indexOf(KEY_ID_SEPARATOR, PREFIX.length());
        if (separator < 0 || separator == keyValue.length() - 1) {
            return Optional.empty();
        }
        final String keyId = keyValue.substring(PREFIX.length(), separator);
        return canEmbed(keyId) ? Optional.of(keyId) : Optional.empty();
    }

    /**
     * 判断密钥文本是否为不含 keyId 的旧格式.
     *
     * @param keyValue 密钥文本，可为 {@code null}
     * @return 解析不出 keyId 时返回 {@code true}
     */
    public static boolean isLegacy(final String keyValue) {
        return keyIdOf(keyValue).isEmpty();
    }
}
