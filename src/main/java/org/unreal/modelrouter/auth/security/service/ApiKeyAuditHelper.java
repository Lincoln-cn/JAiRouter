package org.unreal.modelrouter.auth.security.service;

import lombok.extern.slf4j.Slf4j;
import org.unreal.modelrouter.auth.security.audit.ExtendedSecurityAuditService;
import reactor.core.publisher.Mono;

/**
 * API Key 审计辅助类
 * 负责记录批量操作的审计事件
 */
@Slf4j
class ApiKeyAuditHelper {

    void auditBatchImport(final ExtendedSecurityAuditService auditService,
                          final String importedBy, final String ipAddress,
                          final int successCount, final int failureCount) {
        if (auditService != null) {
            auditService.auditSecurityEvent("API_KEY_BATCH_IMPORT",
                    "批量导入完成: 成功 " + successCount + ", 失败 " + failureCount, null, ipAddress)
                    .onErrorResume(ex -> {
                        log.warn("记录批量导入审计失败: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        }
    }

    void auditApiKeyRotated(final ExtendedSecurityAuditService auditService,
                            final String keyId, final String rotatedBy) {
        if (auditService != null) {
            auditService.auditSecurityEvent("API_KEY_ROTATED",
                    "密钥已轮换", keyId, null)
                    .onErrorResume(ex -> {
                        log.warn("记录密钥轮换审计失败: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        }
    }

    void auditApiKeyExpired(final ExtendedSecurityAuditService auditService,
                            final String keyId) {
        if (auditService != null) {
            auditService.auditSecurityEvent("API_KEY_EXPIRED",
                    "密钥已过期，自动禁用", keyId, null)
                    .onErrorResume(ex -> {
                        log.warn("记录密钥过期审计失败: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        }
    }
}
