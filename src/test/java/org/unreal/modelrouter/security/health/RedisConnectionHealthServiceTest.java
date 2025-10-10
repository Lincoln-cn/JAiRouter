package org.unreal.modelrouter.security.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Redis连接健康检查服务测试
 */
@ExtendWith(MockitoExtension.class)
class RedisConnectionHealthServiceTest {
    
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private RedisConnectionFactory connectionFactory;
    
    @Mock
    private RedisConnection connection;
    
    @InjectMocks
    private RedisConnectionHealthService healthService;
    
    @BeforeEach
    void setUp() {
        when(redisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(connection);
    }
    
    @Test
    void testCheckRedisConnectionSuccess() {
        // 模拟成功的Redis连接
        when(connection.ping()).thenReturn("PONG");
        
        boolean result = healthService.checkRedisConnection();
        
        assertTrue(result);
        assertTrue(healthService.getCurrentHealthStatus());
        assertEquals(0, healthService.getConsecutiveFailures());
        
        verify(connection).ping();
    }
    
    @Test
    void testCheckRedisConnectionFailure() {
        // 模拟Redis连接失败
        when(connection.ping()).thenThrow(new RuntimeException("Connection failed"));
        
        boolean result = healthService.checkRedisConnection();
        
        assertFalse(result);
        assertEquals(1, healthService.getConsecutiveFailures());
        
        verify(connection).ping();
    }
    
    @Test
    void testConsecutiveFailuresLeadToUnhealthy() {
        // 模拟连续失败
        when(connection.ping()).thenThrow(new RuntimeException("Connection failed"));
        
        // 第一次失败
        boolean result1 = healthService.checkRedisConnection();
        assertFalse(result1);
        assertTrue(healthService.getCurrentHealthStatus()); // 还未达到阈值
        
        // 第二次失败
        boolean result2 = healthService.checkRedisConnection();
        assertFalse(result2);
        assertTrue(healthService.getCurrentHealthStatus()); // 还未达到阈值
        
        // 第三次失败
        boolean result3 = healthService.checkRedisConnection();
        assertFalse(result3);
        assertFalse(healthService.getCurrentHealthStatus()); // 达到阈值，变为不健康
        
        assertEquals(3, healthService.getConsecutiveFailures());
        assertTrue(healthService.shouldAlert());
    }
    
    @Test
    void testRecoveryAfterFailure() {
        // 先模拟失败
        when(connection.ping()).thenThrow(new RuntimeException("Connection failed"));
        healthService.checkRedisConnection();
        healthService.checkRedisConnection();
        healthService.checkRedisConnection();
        
        assertFalse(healthService.getCurrentHealthStatus());
        
        // 然后模拟恢复
        when(connection.ping()).thenReturn("PONG");
        boolean result = healthService.checkRedisConnection();
        
        assertTrue(result);
        assertTrue(healthService.getCurrentHealthStatus());
        assertEquals(0, healthService.getConsecutiveFailures());
        assertFalse(healthService.shouldAlert());
    }
    
    @Test
    void testCheckRedisConnectionWithoutRedisTemplate() {
        // 创建没有Redis模板的服务
        RedisConnectionHealthService serviceWithoutRedis = new RedisConnectionHealthService();
        
        boolean result = serviceWithoutRedis.checkRedisConnection();
        
        assertTrue(result); // 没有Redis配置时应该返回true
        assertTrue(serviceWithoutRedis.getCurrentHealthStatus());
    }
    
    @Test
    void testResponseTimeTracking() {
        // 模拟成功的连接
        when(connection.ping()).thenReturn("PONG");
        
        healthService.checkRedisConnection();
        
        long responseTime = healthService.getLastResponseTime();
        assertTrue(responseTime >= 0);
    }
    
    @Test
    void testGetDetailedHealthStatus() {
        // 模拟成功的连接
        when(connection.ping()).thenReturn("PONG");
        healthService.checkRedisConnection();
        
        Map<String, Object> status = healthService.getDetailedHealthStatus();
        
        assertNotNull(status);
        assertTrue(status.containsKey("healthy"));
        assertTrue(status.containsKey("configured"));
        assertTrue(status.containsKey("lastCheckTime"));
        assertTrue(status.containsKey("lastSuccessTime"));
        assertTrue(status.containsKey("lastResponseTimeMs"));
        assertTrue(status.containsKey("consecutiveFailures"));
        assertTrue(status.containsKey("totalChecks"));
        assertTrue(status.containsKey("successfulChecks"));
        assertTrue(status.containsKey("successRatePercent"));
        
        assertTrue((Boolean) status.get("healthy"));
        assertTrue((Boolean) status.get("configured"));
    }
    
    @Test
    void testTriggerHealthCheck() {
        when(connection.ping()).thenReturn("PONG");
        
        boolean result = healthService.triggerHealthCheck();
        
        assertTrue(result);
        verify(connection).ping();
    }
    
    @Test
    void testResetHealthStats() {
        // 先进行一些操作
        when(connection.ping()).thenReturn("PONG");
        healthService.checkRedisConnection();
        
        // 重置统计
        healthService.resetHealthStats();
        
        Map<String, Object> status = healthService.getDetailedHealthStatus();
        assertEquals(0L, status.get("totalChecks"));
        assertEquals(0L, status.get("successfulChecks"));
        assertEquals(0L, status.get("consecutiveFailures"));
        assertTrue((Boolean) status.get("healthy"));
    }
    
    @Test
    void testSuccessRateCalculation() {
        // 模拟混合结果
        when(connection.ping())
            .thenReturn("PONG")
            .thenThrow(new RuntimeException("Failed"))
            .thenReturn("PONG");
        
        healthService.checkRedisConnection(); // 成功
        healthService.checkRedisConnection(); // 失败
        healthService.checkRedisConnection(); // 成功
        
        Map<String, Object> status = healthService.getDetailedHealthStatus();
        
        assertEquals(3L, status.get("totalChecks"));
        assertEquals(2L, status.get("successfulChecks"));
        assertEquals(66.67, (Double) status.get("successRatePercent"), 0.01);
    }
    
    @Test
    void testUnexpectedPingResponse() {
        // 模拟非预期的ping响应
        when(connection.ping()).thenReturn("UNEXPECTED");
        
        boolean result = healthService.checkRedisConnection();
        
        assertFalse(result);
        assertEquals(1, healthService.getConsecutiveFailures());
    }
}