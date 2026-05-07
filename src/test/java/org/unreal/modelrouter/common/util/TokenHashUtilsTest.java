/*
 * Copyright (c) 2024 JAiRouter. All rights reserved.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package org.unreal.modelrouter.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenHashUtils 单元测试 - v2.9.x 测试覆盖率提升 Phase 1
 *
 * 测试目标：覆盖令牌哈希工具类核心功能
 */
@DisplayName("TokenHashUtils v2.9.x 测试")
class TokenHashUtilsTest {

    // ==================== hashToken测试 ====================

    @Nested
    @DisplayName("hashToken方法测试")
    class HashTokenTests {

        @Test
        @DisplayName("测试 1: hashToken - 正常计算哈希")
        void testHashTokenNormal() {
            String token = "test-token-12345";
            String hash = TokenHashUtils.hashToken(token);
            
            assertNotNull(hash, "哈希值不应为null");
            assertEquals(64, hash.length(), "SHA-256哈希应为64字符");
            assertTrue(hash.matches("[a-f0-9]+"), "哈希应为十六进制字符串");
        }

        @Test
        @DisplayName("测试 2: hashToken - 相同输入产生相同哈希")
        void testHashTokenConsistent() {
            String token = "same-token-value";
            String hash1 = TokenHashUtils.hashToken(token);
            String hash2 = TokenHashUtils.hashToken(token);
            
            assertEquals(hash1, hash2, "相同输入应产生相同哈希");
        }

        @Test
        @DisplayName("测试 3: hashToken - 不同输入产生不同哈希")
        void testHashTokenDifferent() {
            String hash1 = TokenHashUtils.hashToken("token-1");
            String hash2 = TokenHashUtils.hashToken("token-2");
            
            assertNotEquals(hash1, hash2, "不同输入应产生不同哈希");
        }

        @Test
        @DisplayName("测试 4: hashToken - null输入抛出异常")
        void testHashTokenNull() {
            assertThrows(IllegalArgumentException.class, () -> {
                TokenHashUtils.hashToken(null);
            }, "null输入应抛出IllegalArgumentException");
        }

        @Test
        @DisplayName("测试 5: hashToken - 空字符串抛出异常")
        void testHashTokenEmpty() {
            assertThrows(IllegalArgumentException.class, () -> {
                TokenHashUtils.hashToken("");
            }, "空字符串应抛出IllegalArgumentException");
        }

        @Test
        @DisplayName("测试 6: hashToken - 纯空白字符抛出异常")
        void testHashTokenWhitespace() {
            assertThrows(IllegalArgumentException.class, () -> {
                TokenHashUtils.hashToken("   ");
            }, "纯空白字符应抛出IllegalArgumentException");
        }

        @Test
        @DisplayName("测试 7: hashToken - 验证已知哈希值")
        void testHashTokenKnownValue() {
            // SHA-256("hello") 的已知值
            String hash = TokenHashUtils.hashToken("hello");
            assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hash,
                "SHA-256哈希值应匹配已知结果");
        }
    }

    // ==================== verifyTokenHash测试 ====================

    @Nested
    @DisplayName("verifyTokenHash方法测试")
    class VerifyTokenHashTests {

        @Test
        @DisplayName("测试 8: verifyTokenHash - 哈希匹配返回true")
        void testVerifyTokenHashMatch() {
            String token = "my-secret-token";
            String hash = TokenHashUtils.hashToken(token);
            
            assertTrue(TokenHashUtils.verifyTokenHash(token, hash), 
                "正确的哈希应返回true");
        }

        @Test
        @DisplayName("测试 9: verifyTokenHash - 哈希不匹配返回false")
        void testVerifyTokenHashNoMatch() {
            String token = "my-secret-token";
            String wrongHash = "wrong-hash-value";
            
            assertFalse(TokenHashUtils.verifyTokenHash(token, wrongHash), 
                "错误的哈希应返回false");
        }

        @Test
        @DisplayName("测试 10: verifyTokenHash - null token返回false")
        void testVerifyTokenHashNullToken() {
            assertFalse(TokenHashUtils.verifyTokenHash(null, "some-hash"), 
                "null token应返回false");
        }

        @Test
        @DisplayName("测试 11: verifyTokenHash - null hash返回false")
        void testVerifyTokenHashNullHash() {
            assertFalse(TokenHashUtils.verifyTokenHash("token", null), 
                "null hash应返回false");
        }

        @Test
        @DisplayName("测试 12: verifyTokenHash - 双null返回false")
        void testVerifyTokenHashBothNull() {
            assertFalse(TokenHashUtils.verifyTokenHash(null, null), 
                "双null应返回false");
        }
    }
}