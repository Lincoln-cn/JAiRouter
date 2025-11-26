package org.unreal.modelrouter.store.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.security.config.properties.ApiKey;
import org.unreal.modelrouter.security.config.properties.JwtUserProperties;
import org.unreal.modelrouter.security.service.ApiKeyService;
import org.unreal.modelrouter.security.service.JwtAccountService;
import org.unreal.modelrouter.store.entity.ApiKeyEntity;
import org.unreal.modelrouter.store.entity.JwtAccountEntity;
import org.unreal.modelrouter.store.repository.ApiKeyRepository;
import org.unreal.modelrouter.store.repository.JwtAccountRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 安全数据迁移服务
 * 用于将 API Key 和 JWT 账户数据迁移到 H2 数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "store.security-migration.enabled", havingValue = "true")
public class SecurityDataMigrationService {

    private final ApiKeyService apiKeyService;
    private final JwtAccountService jwtAccountService;
    private final ApiKeyRepository apiKeyRepository;
    private final JwtAccountRepository jwtAccountRepository;
    private final ObjectMapper objectMapper;

    /**
     * 应用启动后自动执行迁移
     */
    @EventListener(ApplicationReadyEvent.class)
    public void migrateOnStartup() {
        log.info("Starting automatic security data migration...");
        try {
            migrateApiKeys();
            migrateJwtAccounts();
            log.info("Security data migration completed successfully");
        } catch (Exception e) {
            log.error("Security data migration failed", e);
        }
    }

    /**
     * 迁移 API Keys
     */
    public void migrateApiKeys() {
        try {
            log.info("Starting API Keys migration...");

            // 检查是否已有数据
            Long existingCount = apiKeyRepository.countAll().block();
            if (existingCount != null && existingCount > 0) {
                log.info("API Keys already exist in H2 database, skipping migration");
                return;
            }

            // 获取所有 API Keys
            List<ApiKey> apiKeys = apiKeyService.getAllApiKeys().block();
            if (apiKeys == null || apiKeys.isEmpty()) {
                log.info("No API Keys to migrate");
                return;
            }

            int migratedCount = 0;
            int errorCount = 0;

            for (ApiKey apiKey : apiKeys) {
                try {
                    ApiKeyEntity entity = ApiKeyEntity.builder()
                            .keyId(apiKey.getKeyId())
                            .keyValue(apiKey.getKeyValue())
                            .description(apiKey.getDescription())
                            .permissions(objectMapper.writeValueAsString(apiKey.getPermissions()))
                            .expiresAt(apiKey.getExpiresAt())
                            .createdAt(apiKey.getCreatedAt() != null ? apiKey.getCreatedAt() : LocalDateTime.now())
                            .enabled(apiKey.isEnabled())
                            .metadata(apiKey.getMetadata() != null ? objectMapper.writeValueAsString(apiKey.getMetadata()) : null)
                            .usageStatistics(apiKey.getUsage() != null ? objectMapper.writeValueAsString(apiKey.getUsage()) : null)
                            .build();

                    apiKeyRepository.save(entity).block();
                    migratedCount++;
                    log.debug("Migrated API Key: {}", apiKey.getKeyId());
                } catch (Exception e) {
                    errorCount++;
                    log.error("Failed to migrate API Key: {}", apiKey.getKeyId(), e);
                }
            }

            log.info("API Keys migration completed. Migrated: {}, Errors: {}", migratedCount, errorCount);
        } catch (Exception e) {
            log.error("API Keys migration failed", e);
            throw new RuntimeException("Failed to migrate API Keys", e);
        }
    }

    /**
     * 迁移 JWT 账户
     */
    public void migrateJwtAccounts() {
        try {
            log.info("Starting JWT Accounts migration...");

            // 检查是否已有数据
            Long existingCount = jwtAccountRepository.countAll().block();
            if (existingCount != null && existingCount > 0) {
                log.info("JWT Accounts already exist in H2 database, skipping migration");
                return;
            }

            // 获取所有 JWT 账户
            List<JwtUserProperties.UserAccount> accounts = jwtAccountService.getAllAccounts().block();
            if (accounts == null || accounts.isEmpty()) {
                log.info("No JWT Accounts to migrate");
                return;
            }

            int migratedCount = 0;
            int errorCount = 0;

            for (JwtUserProperties.UserAccount account : accounts) {
                try {
                    JwtAccountEntity entity = JwtAccountEntity.builder()
                            .username(account.getUsername())
                            .password(account.getPassword())
                            .roles(objectMapper.writeValueAsString(account.getRoles()))
                            .enabled(account.isEnabled())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    jwtAccountRepository.save(entity).block();
                    migratedCount++;
                    log.debug("Migrated JWT Account: {}", account.getUsername());
                } catch (Exception e) {
                    errorCount++;
                    log.error("Failed to migrate JWT Account: {}", account.getUsername(), e);
                }
            }

            log.info("JWT Accounts migration completed. Migrated: {}, Errors: {}", migratedCount, errorCount);
        } catch (Exception e) {
            log.error("JWT Accounts migration failed", e);
            throw new RuntimeException("Failed to migrate JWT Accounts", e);
        }
    }

    /**
     * 手动触发完整迁移
     */
    public void migrateAll() {
        log.info("Starting manual security data migration...");
        migrateApiKeys();
        migrateJwtAccounts();
        log.info("Manual security data migration completed");
    }
}
