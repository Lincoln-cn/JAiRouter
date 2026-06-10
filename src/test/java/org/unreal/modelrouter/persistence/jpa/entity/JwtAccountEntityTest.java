package org.unreal.modelrouter.persistence.jpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtAccountEntity 单元测试
 */
@DisplayName("JwtAccountEntity 测试")
class JwtAccountEntityTest {

    @Test
    @DisplayName("测试默认构造函数")
    void testDefaultConstructor() {
        JwtAccountEntity entity = new JwtAccountEntity();

        assertNull(entity.getId());
        assertNull(entity.getUsername());
        assertNull(entity.getPassword());
        assertNull(entity.getRoles());
        assertNull(entity.getEnabled());
    }

    @Test
    @DisplayName("测试全参数构造函数")
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        JwtAccountEntity entity = new JwtAccountEntity(
                1L,
                "testuser",
                "password123",
                "[\"ADMIN\",\"USER\"]",
                true,
                now,
                now
        );

        assertEquals(1L, entity.getId());
        assertEquals("testuser", entity.getUsername());
        assertEquals("password123", entity.getPassword());
        assertEquals("[\"ADMIN\",\"USER\"]", entity.getRoles());
        assertTrue(entity.getEnabled());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(now, entity.getUpdatedAt());
    }

    @Test
    @DisplayName("测试Builder模式")
    void testBuilder() {
        LocalDateTime now = LocalDateTime.now();
        JwtAccountEntity entity = JwtAccountEntity.builder()
                .id(2L)
                .username("builderuser")
                .password("builderpass")
                .roles("[\"USER\"]")
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(2L, entity.getId());
        assertEquals("builderuser", entity.getUsername());
        assertEquals("builderpass", entity.getPassword());
        assertEquals("[\"USER\"]", entity.getRoles());
        assertTrue(entity.getEnabled());
    }

    @Test
    @DisplayName("测试Setter和Getter")
    void testSetterGetter() {
        JwtAccountEntity entity = new JwtAccountEntity();
        LocalDateTime now = LocalDateTime.now();

        entity.setId(3L);
        entity.setUsername("setteruser");
        entity.setPassword("setterpass");
        entity.setRoles("[\"DEVELOPER\"]");
        entity.setEnabled(false);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        assertEquals(3L, entity.getId());
        assertEquals("setteruser", entity.getUsername());
        assertEquals("setterpass", entity.getPassword());
        assertEquals("[\"DEVELOPER\"]", entity.getRoles());
        assertFalse(entity.getEnabled());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(now, entity.getUpdatedAt());
    }

    @Test
    @DisplayName("测试equals和hashCode")
    void testEqualsAndHashCode() {
        JwtAccountEntity entity1 = JwtAccountEntity.builder()
                .id(1L)
                .username("user1")
                .password("pass1")
                .build();

        JwtAccountEntity entity2 = JwtAccountEntity.builder()
                .id(1L)
                .username("user1")
                .password("pass1")
                .build();

        JwtAccountEntity entity3 = JwtAccountEntity.builder()
                .id(2L)
                .username("user2")
                .password("pass2")
                .build();

        assertEquals(entity1, entity2);
        assertEquals(entity1.hashCode(), entity2.hashCode());
        assertNotEquals(entity1, entity3);
    }

    @Test
    @DisplayName("测试toString")
    void testToString() {
        JwtAccountEntity entity = JwtAccountEntity.builder()
                .id(1L)
                .username("testuser")
                .password("testpass")
                .enabled(true)
                .build();

        String str = entity.toString();

        assertTrue(str.contains("testuser"));
        assertTrue(str.contains("testpass"));
    }
}
