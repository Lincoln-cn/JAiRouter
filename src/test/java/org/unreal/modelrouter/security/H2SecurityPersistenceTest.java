package org.unreal.modelrouter.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.unreal.modelrouter.security.audit.H2SecurityAuditServiceImpl;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.security.service.H2JwtBlacklistService;
import org.unreal.modelrouter.store.repository.JwtBlacklistRepository;
import org.unreal.modelrouter.store.repository.SecurityAuditRepository;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H2 安全持久化功能测试
 */
@SpringBootTest
@ActiveProfiles("test")
public class H2SecurityPersistenceTest {
    
    @Autowired(required = false)
    private H2SecurityAuditServiceImpl auditService;
    
    @Autowired(required = false)
    private SecurityAuditRepository auditRepository;
    
    @Autowired(required = false)
    private H2JwtBlacklistService blacklistService;
    
    @Autowired(required = false)
    private JwtBlacklistRepository blacklistRepository;
    
    @Autowired(required = false)
    private ObjectMapper objectMapper;
    
    @Test
    public void testSecurityAuditPersistence() {
        if (auditService == null || auditRepository == null) {
            System.out.println("安全审计服务未启用，跳过测试");
            return;
        }
        
        // 创建测试审计事件
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("testKey", "testValue");
        
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventType("TEST_EVENT")
                .userId("test-user")
                .clientIp("127.0.0.1")
                .userAgent("Test Agent")
                .action("TEST_ACTION")
                .success(true)
                .additionalData(additionalData)
                .build();
        
        // 记录事件
        StepVerifier.create(auditService.recordEvent(event))
                .verifyComplete();
        
        // 查询事件
        LocalDateTime startTime = LocalDateTime.now().minusMinutes(5);
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(5);
        
        StepVerifier.create(
                auditService.queryEvents(startTime, endTime, "TEST_EVENT", "test-user", 10)
        )
                .assertNext(retrievedEvent -> {
                    assertEquals("TEST_EVENT", retrievedEvent.getEventType());
                    assertEquals("test-user", retrievedEvent.getUserId());
                    assertEquals("127.0.0.1", retrievedEvent.getClientIp());
                    assertTrue(retrievedEvent.isSuccess());
                })
                .verifyComplete();
        
        System.out.println("✅ 安全审计持久化测试通过");
    }
    
    @Test
    public void testJwtBlacklistPersistence() {
        if (blacklistService == null || blacklistRepository == null) {
            System.out.println("JWT黑名单服务未启用，跳过测试");
            return;
        }
        
        String testTokenHash = "test-token-hash-" + System.currentTimeMillis();
        String userId = "test-user";
        long ttlSeconds = 3600; // 1小时
        
        // 添加到黑名单
        StepVerifier.create(
                blacklistService.addToBlacklist(testTokenHash, userId, ttlSeconds, "测试撤销", "admin")
        )
                .assertNext(result -> assertTrue(result))
                .verifyComplete();
        
        // 检查是否在黑名单中
        StepVerifier.create(blacklistService.isBlacklisted(testTokenHash))
                .assertNext(isBlacklisted -> assertTrue(isBlacklisted))
                .verifyComplete();
        
        // 从黑名单移除
        StepVerifier.create(blacklistService.removeFromBlacklist(testTokenHash))
                .assertNext(result -> assertTrue(result))
                .verifyComplete();
        
        // 再次检查（应该不在黑名单中）
        StepVerifier.create(blacklistService.isBlacklisted(testTokenHash))
                .assertNext(isBlacklisted -> assertFalse(isBlacklisted))
                .verifyComplete();
        
        System.out.println("✅ JWT黑名单持久化测试通过");
    }
    
    @Test
    public void testBlacklistStats() {
        if (blacklistService == null) {
            System.out.println("JWT黑名单服务未启用，跳过测试");
            return;
        }
        
        StepVerifier.create(blacklistService.getStats())
                .assertNext(stats -> {
                    assertNotNull(stats);
                    assertTrue(stats.getLocalCacheMaxSize() > 0);
                    System.out.println("黑名单统计: 本地缓存=" + stats.getLocalCacheSize() + 
                                     ", H2大小=" + stats.getH2Size() + 
                                     ", H2可用=" + stats.isH2Available());
                })
                .verifyComplete();
        
        System.out.println("✅ 黑名单统计测试通过");
    }
    
    @Test
    public void testAuditStatistics() {
        if (auditService == null) {
            System.out.println("安全审计服务未启用，跳过测试");
            return;
        }
        
        LocalDateTime startTime = LocalDateTime.now().minusDays(1);
        LocalDateTime endTime = LocalDateTime.now();
        
        StepVerifier.create(auditService.getSecurityStatistics(startTime, endTime))
                .assertNext(stats -> {
                    assertNotNull(stats);
                    assertTrue(stats.containsKey("totalEvents"));
                    System.out.println("审计统计: " + stats);
                })
                .verifyComplete();
        
        System.out.println("✅ 审计统计测试通过");
    }
}
