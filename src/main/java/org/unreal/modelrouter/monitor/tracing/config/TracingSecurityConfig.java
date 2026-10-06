package org.unreal.modelrouter.monitor.tracing.config;

import lombok.Data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 安全配置
 */
@Data
public class TracingSecurityConfig {
    private SanitizationConfig sanitization = new SanitizationConfig();
    private AccessControlConfig accessControl = new AccessControlConfig();
    private EncryptionConfig encryption = new EncryptionConfig();
    private AuditConfig audit = new AuditConfig();

    /**
     * 脱敏配置。
     *
     * <p>消费方（issue #224 逐项核对）：{@code enabled} 由 {@code TracingSanitizationService.isEnabled()} 与
     * {@code TracingSecurityAutoConfiguration} 的条件读取；{@code inherit-global-rules} /
     * {@code additional-patterns} / {@code sensitive-attributes} 由 {@code TracingSanitizationService} 读取
     * （并回显到 /actuator/info）。</p>
     *
     * <p>原先还有 {@code encrypt-sensitive-data} 与一整个 {@code tracing-rules} 子段
     * （{@code sanitize-span-attributes} / {@code sanitize-event-attributes} / {@code sanitize-log-data} /
     * {@code exempted-attributes} / {@code default-mask-character}），**零消费方**，已随 issue #224 删除。</p>
     */
    @Data
    public static class SanitizationConfig {
        private boolean enabled = true;
        private boolean inheritGlobalRules = true;
        private List<String> additionalPatterns = new ArrayList<>();
        private List<String> sensitiveAttributes = new ArrayList<>();
    }

    @Data
    public static class AccessControlConfig {
        private boolean restrictTraceAccess = true;
        private List<String> allowedRoles = new ArrayList<>();
        private boolean enableRoleBasedFiltering = true;
        private boolean auditAccessAttempts = true;
        private int maxAccessHistoryPerUser = 100;

        /**
         * 字段级别访问控制
         */
        private FieldAccessControl fieldAccess = new FieldAccessControl();

        @Data
        public static class FieldAccessControl {
            private boolean enabled = true;
            private Map<String, List<String>> fieldRoleMapping = new HashMap<>();
            private List<String> adminOnlyFields = new ArrayList<>();
        }
    }

    @Data
    public static class EncryptionConfig {
        private boolean enabled = false;
        private String algorithm = "AES";
        private int keySize = 256;
        private boolean encryptSensitiveSpans = true;
        private boolean encryptSensitiveLogs = true;

        /**
         * 密钥管理配置
         */
        private KeyManagement keyManagement = new KeyManagement();

        /**
         * 数据保留策略
         */
        private DataRetention dataRetention = new DataRetention();

        @Data
        public static class KeyManagement {
            private boolean autoRotation = true;
            private Duration rotationInterval = Duration.ofDays(1);
            private String keyStorePath = "./keys";
            private boolean useHardwareSecurityModule = false;
        }

        @Data
        public static class DataRetention {
            private Duration defaultRetention = Duration.ofDays(30);
            private Duration sensitiveDataRetention = Duration.ofDays(7);
            private Duration errorDataRetention = Duration.ofDays(90);
            private Duration performanceDataRetention = Duration.ofDays(60);
            private boolean autoCleanup = true;
            private Duration cleanupInterval = Duration.ofHours(1);
        }
    }

    @Data
    public static class AuditConfig {
        private boolean enabled = true;
        private boolean auditDataAccess = true;
        private boolean auditSanitization = true;
        private boolean auditEncryption = true;
        private boolean auditConfigChanges = true;
        private String auditLogLevel = "INFO";

        /**
         * 审计日志存储配置
         */
        private AuditStorage storage = new AuditStorage();

        @Data
        public static class AuditStorage {
            private boolean separateAuditLog = true;
            private String auditLogFile = "audit.log";
            private boolean encryptAuditLog = false;
            private Duration auditLogRetention = Duration.ofDays(365);
        }
    }
}
