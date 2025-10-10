package org.unreal.modelrouter.security.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JWT持久化健康检查指示器测试
 */
@ExtendWith(MockitoExtension.class)
class JwtPersistenceHealthIndicatorTest {
    
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private RedisConnectionFactory connectionFactory;
    
    @Mock
    private RedisConnection connection;
    
    @InjectMocks
    private JwtPersistenceHealthIndicator healthIndicator;
    
    @BeforeEach
    void setUp() {
        // 设置Redis模拟
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(connection);
    }
    
    @Test
    void testHealthCheckWithRedisUp() {
        // 模拟Redis正常响应
        when(connection.ping()).thenReturn("PONG");
        
        Health health = healthIndicator.health();
        
        assertEquals(Status.UP, health.getStatus());
        assertNotNull(health.getDetails());
        assertTrue(health.getDetails().containsKey("redis"));
        assertTrue(health.getDetails().containsKey("memory"));
        assertTrue(health.getDetails().containsKey("storageSync"));
        
        verify(connection).ping();
    }
    
    @Test
    void testHealthCheckWithRedisDown() {
        // 模拟Redis连接失败
        when(connection.ping()).thenThrow(new RuntimeException("Connection failed"));
        
        Health health = healthIndicator.health();
        
        assertEquals(Status.DOWN, health.getStatus());
        assertNotNull(health.getDetails());
        
        // 检查Redis状态
        Object redisDetails = health.getDetails().get("redis");
        assertNotNull(redisDetails);
        
        verify(connection).ping();
    }
    
    @Test
    void testHealthCheckWithoutRedis() {
        // 创建没有Redis的健康检查器
        JwtPersistenceHealthIndicator indicatorWithoutRedis = new JwtPersistenceHealthIndicator();
        
        Health health = indicatorWithoutRedis.health();
        
        assertEquals(Status.UP, health.getStatus());
        assertNotNull(health.getDetails());
        
        // Redis应该显示为DISABLED
        Object redisDetails = health.getDetails().get("redis");
        assertNotNull(redisDetails);
    }
    
    @Test
    void testHealthCheckWithRedisTimeout() {
        // 模拟Redis响应超时（通过延迟模拟）
        when(connection.ping()).thenAnswer(invocation -> {
            Thread.sleep(100); // 短暂延迟
            return "PONG";
        });
        
        Health health = healthIndicator.health();
        
        // 应该仍然是UP，因为延迟不够长
        assertEquals(Status.UP, health.getStatus());
        
        verify(connection).ping();
    }
    
    @Test
    void testHealthCheckWithUnexpectedRedisResponse() {
        // 模拟Redis返回非预期响应
        when(connection.ping()).thenReturn("UNEXPECTED");
        
        Health health = healthIndicator.health();
        
        assertEquals(Status.DOWN, health.getStatus());
        
        verify(connection).ping();
    }
    
    @Test
    void testMemoryHealthCheck() {
        // 模拟Redis正常，主要测试内存检查
        when(connection.ping()).thenReturn("PONG");
        
        Health health = healthIndicator.health();
        
        assertNotNull(health.getDetails().get("memory"));
        
        // 内存检查应该包含使用情况信息
        Object memoryDetails = health.getDetails().get("memory");
        assertNotNull(memoryDetails);
    }
    
    @Test
    void testStorageSyncHealthCheck() {
        // 模拟Redis正常，测试存储同步检查
        when(connection.ping()).thenReturn("PONG");
        
        Health health = healthIndicator.health();
        
        assertNotNull(health.getDetails().get("storageSync"));
        
        // 存储同步检查应该包含状态信息
        Object syncDetails = health.getDetails().get("storageSync");
        assertNotNull(syncDetails);
    }
    
    @Test
    void testHealthCheckException() {
        // 模拟健康检查过程中发生异常
        when(redisTemplate.getConnectionFactory()).thenThrow(new RuntimeException("Unexpected error"));
        
        Health health = healthIndicator.health();
        
        assertEquals(Status.DOWN, health.getStatus());
        assertTrue(health.getDetails().containsKey("error"));
        assertNotNull(health.getException());
    }
    
    @Test
    void testHealthCheckDetailsContainTimestamp() {
        // 模拟正常情况
        when(connection.ping()).thenReturn("PONG");
        
        Health health = healthIndicator.health();
        
        assertTrue(health.getDetails().containsKey("checkTime"));
        assertTrue(health.getDetails().containsKey("overallStatus"));
    }
}