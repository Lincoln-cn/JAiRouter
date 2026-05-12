package org.unreal.modelrouter.config.sync.service;

import reactor.core.publisher.Mono;
import java.util.Map;

/**
 * Config migration service interface.
 *
 * @since v2.6.12
 */
public interface ConfigMigrationService {

    Mono<MigrationResult> exportConfig(String sourceEnv, String targetEnv, String[] serviceTypes);

    Mono<MigrationResult> importConfig(Map<String, Object> backupData, String targetEnv);

    record MigrationResult(boolean success, String sourceEnv, String targetEnv, int migratedCount, String message) {
        public static MigrationResult success(String sourceEnv, String targetEnv, int count) {
            return new MigrationResult(true, sourceEnv, targetEnv, count, "Migration successful");
        }
        public static MigrationResult failure(String sourceEnv, String targetEnv, String message) {
            return new MigrationResult(false, sourceEnv, targetEnv, 0, message);
        }
    }
}
