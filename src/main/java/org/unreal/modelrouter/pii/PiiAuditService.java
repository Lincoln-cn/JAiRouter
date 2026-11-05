package org.unreal.modelrouter.pii;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.persistence.jpa.entity.PiiAccessAuditEntity;
import org.unreal.modelrouter.persistence.jpa.repository.PiiAccessAuditRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 可逆脱敏的访问审计（#987）。
 *
 * <p>记录「哪个请求还原了几处 PII、由谁发起、走哪个路径」，不含任何原值。
 * 审计写入失败不影响主链路（只记 warn）。</p>
 */
@Service
public class PiiAuditService {

    private static final Logger log = LoggerFactory.getLogger(PiiAuditService.class);

    private final PiiAccessAuditRepository repository;

    public PiiAuditService(PiiAccessAuditRepository repository) {
        this.repository = repository;
    }

    /**
     * 记录一次还原行为。
     *
     * @param scopeId       本次请求的脱敏作用域
     * @param caller        调用方标识（API Key 或用户名），可为 null
     * @param path          请求路径
     * @param restoredCount 实际还原的占位符数量
     */
    public void record(String scopeId, String caller, String path, int restoredCount) {
        if (restoredCount <= 0) {
            return;
        }
        try {
            repository.save(PiiAccessAuditEntity.builder()
                    .scopeId(scopeId)
                    .caller(caller)
                    .path(path)
                    .restoredCount(restoredCount)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (RuntimeException e) {
            log.warn("记录可逆脱敏审计失败（不影响响应）: {}", e.toString());
        }
    }

    /**
     * 最近的审计记录，用于控制台排查。
     */
    public List<PiiAccessAuditEntity> recent() {
        return repository.findTop100ByOrderByCreatedAtDesc();
    }

    /**
     * 删除保留期之外的审计记录。
     */
    @Transactional
    public int cleanup(int retentionDays) {
        if (retentionDays <= 0) {
            log.warn("可逆脱敏审计保留天数为 {}，本次跳过清理", retentionDays);
            return 0;
        }
        return repository.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(retentionDays));
    }
}
