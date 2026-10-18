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

package org.unreal.modelrouter.monitor.monitoring.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code ExceptionEventEntity.lineNumber} 的取值规则测试（issue #501）。
 *
 * <p>回归点：该 JPA 列此前**从未被赋值**（实体构建处漏填），库里恒为 null。
 * 现在从异常堆栈取"第一个应用帧"的行号，并明确"取不到就 null，不伪造"。</p>
 */
@DisplayName("ExceptionPersistenceService 行号提取")
class ExceptionPersistenceServiceLineNumberTest {

    @Test
    @DisplayName("跳过 JDK/Spring 内部帧，取第一个 org.unreal.modelrouter 帧")
    void picksFirstApplicationFrame() {
        Throwable t = new Throwable("boom");
        t.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("org.springframework.web.Foo", "invoke", "Foo.java", 111),
                new StackTraceElement("java.lang.Thread", "run", "Thread.java", 222),
                new StackTraceElement("org.unreal.modelrouter.router.Gateway", "handle", "Gateway.java", 42),
                new StackTraceElement("org.unreal.modelrouter.router.Other", "x", "Other.java", 99),
        });

        assertEquals(42, ExceptionPersistenceService.resolveLineNumber(t));
    }

    @Test
    @DisplayName("没有应用帧时返回 null —— 不把框架行号配到应用类名上")
    void returnsNullWhenNoApplicationFrame() {
        Throwable t = new Throwable("boom");
        t.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("java.util.concurrent.ThreadPoolExecutor", "run", "ThreadPoolExecutor.java", 7),
                new StackTraceElement("org.springframework.web.Foo", "invoke", "Foo.java", 111),
        });

        assertNull(ExceptionPersistenceService.resolveLineNumber(t),
                "className/methodName 是应用层的，行号不能取自框架帧");
    }

    @Test
    @DisplayName("行号未知（<0）或没有堆栈时返回 null，而不是 0/-1")
    void returnsNullWhenLineUnknown() {
        Throwable nativeFrame = new Throwable("boom");
        nativeFrame.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("org.unreal.modelrouter.Native", "call", "Native.java", -2),
        });
        assertNull(ExceptionPersistenceService.resolveLineNumber(nativeFrame), "未知行号不能落成 -2");

        Throwable empty = new Throwable("boom");
        empty.setStackTrace(new StackTraceElement[0]);
        assertNull(ExceptionPersistenceService.resolveLineNumber(empty));

        assertNull(ExceptionPersistenceService.resolveLineNumber(null));
    }

    @Test
    @DisplayName("真实抛出的异常能取到非空行号")
    void realThrowableYieldsLineNumber() {
        IllegalStateException e = new IllegalStateException("boom");

        Integer line = ExceptionPersistenceService.resolveLineNumber(e);

        assertNotNull(line, "真实异常的首个应用帧必须有行号");
        assertTrue(line > 0);
    }
}
