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

package org.unreal.modelrouter.monitor.tracing.logger.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LogType枚举测试
 */
@DisplayName("LogType枚举测试")
class LogTypeTest {

    @Test
    @DisplayName("测试所有枚举值")
    void testAllValues() {
        LogType[] types = LogType.values();
        assertEquals(8, types.length);

        assertEquals(LogType.REQUEST, LogType.valueOf("REQUEST"));
        assertEquals(LogType.RESPONSE, LogType.valueOf("RESPONSE"));
        assertEquals(LogType.BACKEND_CALL, LogType.valueOf("BACKEND_CALL"));
        assertEquals(LogType.ERROR, LogType.valueOf("ERROR"));
        assertEquals(LogType.BUSINESS_EVENT, LogType.valueOf("BUSINESS_EVENT"));
        assertEquals(LogType.PERFORMANCE, LogType.valueOf("PERFORMANCE"));
        assertEquals(LogType.SECURITY, LogType.valueOf("SECURITY"));
        assertEquals(LogType.SYSTEM, LogType.valueOf("SYSTEM"));
    }

    @Test
    @DisplayName("测试getValue方法")
    void testGetValue() {
        assertEquals("request", LogType.REQUEST.getValue());
        assertEquals("response", LogType.RESPONSE.getValue());
        assertEquals("backend_call", LogType.BACKEND_CALL.getValue());
        assertEquals("error", LogType.ERROR.getValue());
        assertEquals("business_event", LogType.BUSINESS_EVENT.getValue());
        assertEquals("performance", LogType.PERFORMANCE.getValue());
        assertEquals("security", LogType.SECURITY.getValue());
        assertEquals("system", LogType.SYSTEM.getValue());
    }

    @Test
    @DisplayName("测试toString方法")
    void testToString() {
        assertEquals("request", LogType.REQUEST.toString());
        assertEquals("response", LogType.RESPONSE.toString());
        assertEquals("backend_call", LogType.BACKEND_CALL.toString());
    }

    @Test
    @DisplayName("测试fromValue方法 - 有效值")
    void testFromValueValid() {
        assertEquals(LogType.REQUEST, LogType.fromValue("request"));
        assertEquals(LogType.RESPONSE, LogType.fromValue("response"));
        assertEquals(LogType.BACKEND_CALL, LogType.fromValue("backend_call"));
        assertEquals(LogType.ERROR, LogType.fromValue("error"));
        assertEquals(LogType.BUSINESS_EVENT, LogType.fromValue("business_event"));
        assertEquals(LogType.PERFORMANCE, LogType.fromValue("performance"));
        assertEquals(LogType.SECURITY, LogType.fromValue("security"));
        assertEquals(LogType.SYSTEM, LogType.fromValue("system"));
    }

    @Test
    @DisplayName("测试fromValue方法 - 无效值")
    void testFromValueInvalid() {
        assertNull(LogType.fromValue("invalid_type"));
        assertNull(LogType.fromValue("REQUEST")); // 大写不匹配
        assertNull(LogType.fromValue(""));
    }

    @Test
    @DisplayName("测试fromValue方法 - null值")
    void testFromValueNull() {
        assertNull(LogType.fromValue(null));
    }
}
