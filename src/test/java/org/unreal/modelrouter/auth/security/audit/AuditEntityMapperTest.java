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

package org.unreal.modelrouter.auth.security.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.dto.AuditEvent;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity.RiskLevel;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuditEntityMapper 测试类
 */
@DisplayName("AuditEntityMapper测试")
class AuditEntityMapperTest {

    private AuditEntityMapper mapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        mapper = new AuditEntityMapper(objectMapper);
    }

    @Nested
    @DisplayName("entityToDto测试")
    class EntityToDtoTests {

        @Test
        @DisplayName("基本转换")
        void testBasicConversion() {
            SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                    .eventId("event-123")
                    .eventType(AuditEventType.JWT_TOKEN_ISSUED)
                    .userId("user-001")
                    .resourceId("token-abc")
                    .clientIp("192.168.1.100")
                    .userAgent("Mozilla/5.0")
                    .timestamp(LocalDateTime.of(2025, 1, 15, 10, 30, 0))
                    .action("login")
                    .details("User logged in")
                    .success(true)
                    .metadata("{\"key\":\"value\"}")
                    .build();

            AuditEvent dto = mapper.entityToDto(entity);

            assertEquals("event-123", dto.getId());
            assertEquals(AuditEventType.JWT_TOKEN_ISSUED, dto.getType());
            assertEquals("user-001", dto.getUserId());
            assertEquals("token-abc", dto.getResourceId());
            assertEquals("192.168.1.100", dto.getIpAddress());
            assertEquals("Mozilla/5.0", dto.getUserAgent());
            assertEquals("login", dto.getAction());
            assertEquals("User logged in", dto.getDetails());
            assertTrue(dto.isSuccess());
            assertEquals(LocalDateTime.of(2025, 1, 15, 10, 30, 0), dto.getTimestamp());
            assertNotNull(dto.getMetadata());
            assertEquals("value", dto.getMetadata().get("key"));
        }

        @Test
        @DisplayName("空metadata处理")
        void testNullMetadata() {
            SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                    .eventId("event-456")
                    .eventType(AuditEventType.API_KEY_USED)
                    .success(true)
                    .timestamp(LocalDateTime.now())
                    .metadata(null)
                    .build();

            AuditEvent dto = mapper.entityToDto(entity);

            assertNotNull(dto.getMetadata());
            assertTrue(dto.getMetadata().isEmpty());
        }
    }

    @Nested
    @DisplayName("dtoToEntity测试")
    class DtoToEntityTests {

        @Test
        @DisplayName("基本转换")
        void testBasicConversion() {
            AuditEvent dto = new AuditEvent();
            dto.setId("event-789");
            dto.setType(AuditEventType.JWT_TOKEN_REFRESHED);
            dto.setUserId("user-002");
            dto.setResourceId("token-xyz");
            dto.setIpAddress("10.0.0.1");
            dto.setUserAgent("curl/7.68.0");
            dto.setAction("refresh");
            dto.setDetails("Token refreshed");
            dto.setSuccess(true);
            dto.setTimestamp(LocalDateTime.of(2025, 2, 20, 14, 0, 0));
            dto.setMetadata(Map.of("duration", 3600));

            SecurityAuditEventEntity entity = mapper.dtoToEntity(dto);

            assertEquals("event-789", entity.getEventId());
            assertEquals(AuditEventType.JWT_TOKEN_REFRESHED, entity.getEventType());
            assertEquals("user-002", entity.getUserId());
            assertEquals("token-xyz", entity.getResourceId());
            assertEquals("10.0.0.1", entity.getClientIp());
            assertEquals("curl/7.68.0", entity.getUserAgent());
            assertEquals("refresh", entity.getAction());
            assertEquals("Token refreshed", entity.getDetails());
            assertTrue(entity.getSuccess());
            assertNotNull(entity.getMetadata());
            assertTrue(entity.getMetadata().contains("duration"));
        }

        @Test
        @DisplayName("自动生成ID和时间戳")
        void testAutoGenerateIdAndTimestamp() {
            AuditEvent dto = new AuditEvent();
            dto.setType(AuditEventType.API_KEY_CREATED);
            dto.setSuccess(true);
            // 不设置id和timestamp

            SecurityAuditEventEntity entity = mapper.dtoToEntity(dto);

            assertNotNull(entity.getEventId());
            assertNotNull(entity.getTimestamp());
        }

        @Test
        @DisplayName("空metadata处理")
        void testNullMetadata() {
            AuditEvent dto = new AuditEvent();
            dto.setId("event-null-meta");
            dto.setType(AuditEventType.API_KEY_REVOKED);
            dto.setSuccess(true);
            dto.setTimestamp(LocalDateTime.now());
            dto.setMetadata(null);

            SecurityAuditEventEntity entity = mapper.dtoToEntity(dto);

            assertNull(entity.getMetadata());
        }
    }

    @Nested
    @DisplayName("entityToSecurityEvent测试")
    class EntityToSecurityEventTests {

        @Test
        @DisplayName("基本转换")
        void testBasicConversion() {
            SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                    .eventId("sec-event-001")
                    .eventType(AuditEventType.AUTHENTICATION_FAILED)
                    .userId("user-003")
                    .clientIp("172.16.0.50")
                    .userAgent("PostmanRuntime/7.29.0")
                    .timestamp(LocalDateTime.of(2025, 3, 10, 8, 15, 30))
                    .resource("/api/chat")
                    .action("authenticate")
                    .success(false)
                    .failureReason("Invalid credentials")
                    .requestId("req-123")
                    .sessionId("sess-456")
                    .metadata("{\"attempts\":3}")
                    .build();

            SecurityAuditEvent event = mapper.entityToSecurityEvent(entity);

            assertEquals("sec-event-001", event.getEventId());
            assertEquals("AUTHENTICATION_FAILED", event.getEventType());
            assertEquals("user-003", event.getUserId());
            assertEquals("172.16.0.50", event.getClientIp());
            assertEquals("PostmanRuntime/7.29.0", event.getUserAgent());
            assertEquals(LocalDateTime.of(2025, 3, 10, 8, 15, 30), event.getTimestamp());
            assertEquals("/api/chat", event.getResource());
            assertEquals("authenticate", event.getAction());
            assertFalse(event.isSuccess());
            assertEquals("Invalid credentials", event.getFailureReason());
            assertEquals("req-123", event.getRequestId());
            assertEquals("sess-456", event.getSessionId());
            assertNotNull(event.getAdditionalData());
            assertEquals(3, event.getAdditionalData().get("attempts"));
        }
    }

    @Nested
    @DisplayName("securityEventToEntity测试")
    class SecurityEventToEntityTests {

        @Test
        @DisplayName("基本转换")
        void testBasicConversion() {
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventId("sec-event-002")
                    .eventType("JWT_TOKEN_REVOKED")
                    .userId("user-004")
                    .clientIp("192.168.100.1")
                    .userAgent("Java/17")
                    .timestamp(LocalDateTime.of(2025, 4, 5, 16, 45, 0))
                    .resource("jwt-token")
                    .action("revoke")
                    .success(true)
                    .failureReason(null)
                    .requestId("req-789")
                    .sessionId("sess-012")
                    .additionalData(Map.of("reason", "user_logout"))
                    .build();

            SecurityAuditEventEntity entity = mapper.securityEventToEntity(event);

            assertEquals("sec-event-002", entity.getEventId());
            assertEquals(AuditEventType.JWT_TOKEN_REVOKED, entity.getEventType());
            assertEquals("user-004", entity.getUserId());
            assertEquals("192.168.100.1", entity.getClientIp());
            assertEquals("Java/17", entity.getUserAgent());
            assertEquals(LocalDateTime.of(2025, 4, 5, 16, 45, 0), entity.getTimestamp());
            assertEquals("jwt-token", entity.getResource());
            assertEquals("revoke", entity.getAction());
            assertTrue(entity.getSuccess());
            assertNull(entity.getFailureReason());
            assertEquals("req-789", entity.getRequestId());
            assertEquals("sess-012", entity.getSessionId());
            assertNotNull(entity.getMetadata());
            assertTrue(entity.getMetadata().contains("reason"));
        }

        @Test
        @DisplayName("自动生成ID和时间戳")
        void testAutoGenerateIdAndTimestamp() {
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventType("API_KEY_USED")
                    .success(true)
                    .build();

            SecurityAuditEventEntity entity = mapper.securityEventToEntity(event);

            assertNotNull(entity.getEventId());
            assertNotNull(entity.getTimestamp());
        }
    }

    @Nested
    @DisplayName("parseEventType测试")
    class ParseEventTypeTests {

        @Test
        @DisplayName("有效事件类型")
        void testValidEventType() {
            assertEquals(AuditEventType.JWT_TOKEN_ISSUED, mapper.parseEventType("JWT_TOKEN_ISSUED"));
            assertEquals(AuditEventType.API_KEY_CREATED, mapper.parseEventType("API_KEY_CREATED"));
            assertEquals(AuditEventType.AUTHENTICATION_FAILED, mapper.parseEventType("AUTHENTICATION_FAILED"));
        }

        @Test
        @DisplayName("无效事件类型返回默认值")
        void testInvalidEventType() {
            assertEquals(AuditEventType.SYSTEM_MAINTENANCE, mapper.parseEventType("INVALID_TYPE"));
            assertEquals(AuditEventType.SYSTEM_MAINTENANCE, mapper.parseEventType(""));
        }

        @Test
        @DisplayName("null事件类型抛出NPE")
        void testNullEventType() {
            // valueOf(null) 会抛出 NullPointerException，不是 IllegalArgumentException
            assertThrows(NullPointerException.class, () -> mapper.parseEventType(null));
        }
    }

    @Nested
    @DisplayName("determineRiskLevel测试")
    class DetermineRiskLevelTests {

        @Test
        @DisplayName("null类型返回LOW")
        void testNullType() {
            assertEquals(RiskLevel.LOW, mapper.determineRiskLevel(null, true));
            assertEquals(RiskLevel.LOW, mapper.determineRiskLevel(null, false));
            assertEquals(RiskLevel.LOW, mapper.determineRiskLevel(null, null));
        }

        @Test
        @DisplayName("失败事件的风险等级")
        void testFailedEvents() {
            // SECURITY_ALERT失败 = CRITICAL
            assertEquals(RiskLevel.CRITICAL, mapper.determineRiskLevel(AuditEventType.SECURITY_ALERT, false));

            // SUSPICIOUS_ACTIVITY失败 = HIGH
            assertEquals(RiskLevel.HIGH, mapper.determineRiskLevel(AuditEventType.SUSPICIOUS_ACTIVITY, false));

            // AUTHORIZATION_FAILED失败 = MEDIUM
            assertEquals(RiskLevel.MEDIUM, mapper.determineRiskLevel(AuditEventType.AUTHORIZATION_FAILED, false));

            // 其他失败 = LOW
            assertEquals(RiskLevel.LOW, mapper.determineRiskLevel(AuditEventType.AUTHENTICATION_FAILED, false));
        }

        @Test
        @DisplayName("成功事件的风险等级")
        void testSuccessfulEvents() {
            // JWT_TOKEN_REVOKED成功 = MEDIUM
            assertEquals(RiskLevel.MEDIUM, mapper.determineRiskLevel(AuditEventType.JWT_TOKEN_REVOKED, true));

            // API_KEY_REVOKED成功 = MEDIUM
            assertEquals(RiskLevel.MEDIUM, mapper.determineRiskLevel(AuditEventType.API_KEY_REVOKED, true));

            // 其他成功 = LOW
            assertEquals(RiskLevel.LOW, mapper.determineRiskLevel(AuditEventType.JWT_TOKEN_ISSUED, true));
            assertEquals(RiskLevel.LOW, mapper.determineRiskLevel(AuditEventType.API_KEY_USED, true));
        }

        @Test
        @DisplayName("null成功状态")
        void testNullSuccess() {
            // success为null时，按成功处理
            assertEquals(RiskLevel.MEDIUM, mapper.determineRiskLevel(AuditEventType.JWT_TOKEN_REVOKED, null));
            assertEquals(RiskLevel.LOW, mapper.determineRiskLevel(AuditEventType.JWT_TOKEN_ISSUED, null));
        }
    }

    @Nested
    @DisplayName("toJson测试")
    class ToJsonTests {

        @Test
        @DisplayName("正常序列化")
        void testNormalSerialization() {
            Map<String, Object> map = Map.of("key1", "value1", "key2", 123);

            String json = mapper.toJson(map);

            assertNotNull(json);
            assertTrue(json.contains("key1"));
            assertTrue(json.contains("value1"));
            assertTrue(json.contains("key2"));
            assertTrue(json.contains("123"));
        }

        @Test
        @DisplayName("null输入")
        void testNullInput() {
            assertNull(mapper.toJson(null));
        }

        @Test
        @DisplayName("空Map输入")
        void testEmptyMap() {
            assertNull(mapper.toJson(Map.of()));
        }

        @Test
        @DisplayName("复杂对象序列化")
        void testComplexObject() {
            Map<String, Object> map = Map.of(
                    "string", "test",
                    "number", 42,
                    "nested", Map.of("inner", "value")
            );

            String json = mapper.toJson(map);

            assertNotNull(json);
            assertTrue(json.contains("nested"));
            assertTrue(json.contains("inner"));
        }
    }

    @Nested
    @DisplayName("parseJson测试")
    class ParseJsonTests {

        @Test
        @DisplayName("正常解析")
        void testNormalParsing() {
            String json = "{\"key1\":\"value1\",\"key2\":123}";

            Map<String, Object> map = mapper.parseJson(json);

            assertNotNull(map);
            assertEquals("value1", map.get("key1"));
            assertEquals(123, map.get("key2"));
        }

        @Test
        @DisplayName("null输入")
        void testNullInput() {
            Map<String, Object> result = mapper.parseJson(null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("空字符串输入")
        void testEmptyString() {
            Map<String, Object> result = mapper.parseJson("");
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("无效JSON返回空Map")
        void testInvalidJson() {
            Map<String, Object> result = mapper.parseJson("not valid json");
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }
}
