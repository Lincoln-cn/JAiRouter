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

package org.unreal.modelrouter.common.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 扩展审计查询响应的序列化契约测试。
 *
 * <p>issue #459 的成因不是"字段不存在"，而是**Jackson 不认 {@code hasMore()} 这种命名**
 * （只认 {@code getXxx()} / {@code isXxx()}）⇒ 响应里根本没有这个键，前端读到的必然是
 * {@code undefined}。所以断言必须落在**序列化后的 JSON 键**上，只看 DTO 方法不算。</p>
 */
@DisplayName("ExtendedAuditQueryResponse 序列化契约")
class ExtendedAuditQueryResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private ExtendedAuditQueryResponse response(int page, int size, int eventCount) {
        List<AuditEvent> events = java.util.stream.IntStream.range(0, eventCount)
                .mapToObj(i -> new AuditEvent())
                .toList();
        return ExtendedAuditQueryResponse.builder()
                .events(events)
                .page(page)
                .size(size)
                .totalElements(100L)
                .totalPages(4)
                .build();
    }

    @Test
    @DisplayName("满页时应序列化出 hasMore=true")
    void hasMoreIsSerializedWhenPageIsFull() {
        JsonNode json = objectMapper.valueToTree(response(0, 10, 10));

        assertTrue(json.has("hasMore"), "JSON 必须含 hasMore（#459 就是这里漏了）");
        assertTrue(json.get("hasMore").asBoolean());
    }

    @Test
    @DisplayName("不满页时应序列化出 hasMore=false")
    void hasMoreIsFalseWhenPageIsNotFull() {
        JsonNode json = objectMapper.valueToTree(response(3, 10, 3));

        assertTrue(json.has("hasMore"));
        assertFalse(json.get("hasMore").asBoolean());
    }

    @Test
    @DisplayName("nextPage 也按 get 前缀正常序列化")
    void nextPageIsSerialized() {
        JsonNode json = objectMapper.valueToTree(response(0, 10, 10));

        assertTrue(json.has("nextPage"));
        assertEquals(1, json.get("nextPage").asInt());
    }
}
