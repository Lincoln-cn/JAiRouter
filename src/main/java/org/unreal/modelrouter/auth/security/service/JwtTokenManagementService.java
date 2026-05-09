package org.unreal.modelrouter.auth.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.common.dto.JwtTokenInfo;
import org.unreal.modelrouter.common.dto.TokenStatus;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * JWT令牌管理服务
 * 提供令牌撤销、状态更新等管理操作
 *
 * @since v2.17.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenManagementService {

    // Optional services
    @Autowired(required = false)
    private JwtPersistenceService jwtPersistenceService;

    @Autowired(required = false)
    private JwtBlacklistService jwtBlacklistService;

    /**
     * 计算令牌哈希值
     *
     * @param token JWT令牌
     * @return SHA-256哈希值（Base64编码）
     */
    public String calculateTokenHash(final String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            log.warn("计算令牌哈希失败: {}", ex.getMessage());
            return String.valueOf(token.hashCode()); // 降级到简单哈希
        }
    }

    /**
     * 更新令牌在持久化存储中的状态
     *
     * @param token JWT令牌
     * @param status 新状态
     * @param reason 状态变更原因
     * @param updatedBy 操作者
     * @return Mono<Void>
     */
    public Mono<Void> updateTokenStatus(final String token, final TokenStatus status,
                                         final String reason, final String updatedBy) {
        if (jwtPersistenceService == null) {
            log.warn("JwtPersistenceService不可用，无法更新令牌状态");
            return Mono.empty();
        }

        try {
            String tokenHash = calculateTokenHash(token);
            log.info("更新令牌状态: tokenHash={}, status={}, reason={}",
                    tokenHash.substring(0, Math.min(10, tokenHash.length())) + "...", status, reason);

            return jwtPersistenceService.findByTokenHash(tokenHash)
                    .flatMap(tokenInfo -> {
                        tokenInfo.setStatus(status);
                        tokenInfo.setRevokeReason(reason);
                        tokenInfo.setRevokedBy(updatedBy);
                        tokenInfo.setRevokedAt(LocalDateTime.now());
                        tokenInfo.setUpdatedAt(LocalDateTime.now());
                        return jwtPersistenceService.saveToken(tokenInfo);
                    })
                    .doOnSuccess(v -> log.info("令牌状态已更新: status={}", status))
                    .onErrorResume(ex -> {
                        log.error("更新令牌持久化状态失败: {}", ex.getMessage(), ex);
                        return Mono.empty();
                    });
        } catch (Exception ex) {
            log.error("更新令牌持久化状态异常: {}", ex.getMessage(), ex);
            return Mono.empty();
        }
    }

    /**
     * 批量更新令牌状态
     *
     * @param tokens JWT令牌列表
     * @param status 新状态
     * @param reason 状态变更原因
     * @param updatedBy 操作者
     * @return Mono<Void>
     */
    public Mono<Void> batchUpdateTokenStatus(final List<String> tokens, final TokenStatus status,
                                              final String reason, final String updatedBy) {
        if (jwtPersistenceService == null || tokens == null || tokens.isEmpty()) {
            return Mono.empty();
        }

        try {
            List<String> tokenHashes = tokens.stream()
                    .map(this::calculateTokenHash)
                    .toList();

            return jwtPersistenceService.batchUpdateTokenStatus(tokenHashes, status, reason, updatedBy)
                    .onErrorResume(ex -> {
                        log.warn("批量更新令牌持久化状态失败: {}", ex.getMessage());
                        return Mono.empty();
                    });
        } catch (Exception ex) {
            log.warn("批量更新令牌持久化状态异常: {}", ex.getMessage());
            return Mono.empty();
        }
    }

    /**
     * 通过tokenHash撤销令牌
     *
     * @param tokenHash 令牌哈希值
     * @param reason 撤销原因
     * @param revokedBy 操作者
     * @return Mono<Void>
     */
    public Mono<Void> revokeTokenByHash(final String tokenHash, final String reason, final String revokedBy) {
        if (jwtPersistenceService == null) {
            return Mono.error(new RuntimeException("令牌持久化服务未启用"));
        }

        return jwtPersistenceService.findByTokenHash(tokenHash)
                .switchIfEmpty(Mono.error(new RuntimeException("令牌不存在")))
                .flatMap(tokenInfo -> {
                    // 更新令牌状态
                    tokenInfo.setStatus(TokenStatus.REVOKED);
                    tokenInfo.setRevokeReason(reason != null ? reason : "手动撤销");
                    tokenInfo.setRevokedBy(revokedBy);
                    tokenInfo.setRevokedAt(LocalDateTime.now());
                    tokenInfo.setUpdatedAt(LocalDateTime.now());

                    // 保存更新后的令牌信息
                    Mono<Void> saveToken = jwtPersistenceService.saveToken(tokenInfo);

                    // 添加到黑名单
                    Mono<Void> addToBlacklist = Mono.empty();
                    if (jwtBlacklistService != null) {
                        addToBlacklist = jwtBlacklistService.addToBlacklist(tokenHash, reason, revokedBy)
                                .onErrorResume(ex -> {
                                    log.warn("添加到黑名单失败: {}", ex.getMessage());
                                    return Mono.empty();
                                });
                    }

                    return Mono.when(saveToken, addToBlacklist);
                });
    }

    /**
     * 批量通过tokenHash撤销令牌
     *
     * @param tokenHashes 令牌哈希值列表
     * @param reason 撤销原因
     * @param revokedBy 操作者
     * @return Mono<Void>
     */
    public Mono<Void> batchRevokeTokensByHash(final List<String> tokenHashes,
                                               final String reason, final String revokedBy) {
        if (jwtPersistenceService == null) {
            return Mono.error(new RuntimeException("令牌持久化服务未启用"));
        }

        if (tokenHashes == null || tokenHashes.isEmpty()) {
            return Mono.empty();
        }

        // 并行处理所有tokenHash
        List<Mono<Void>> revokeTasks = tokenHashes.stream()
                .map(tokenHash -> revokeTokenByHash(tokenHash, reason, revokedBy)
                        .onErrorResume(ex -> {
                            log.warn("撤销令牌失败: tokenHash={}, error={}", tokenHash, ex.getMessage());
                            return Mono.empty();
                        }))
                .toList();

        return Mono.when(revokeTasks);
    }

    /**
     * 检查持久化服务是否可用
     *
     * @return 是否可用
     */
    public boolean isPersistenceServiceAvailable() {
        return jwtPersistenceService != null;
    }

    /**
     * 检查黑名单服务是否可用
     *
     * @return 是否可用
     */
    public boolean isBlacklistServiceAvailable() {
        return jwtBlacklistService != null;
    }
}