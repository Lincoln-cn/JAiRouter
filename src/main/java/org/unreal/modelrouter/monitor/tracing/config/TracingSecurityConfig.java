package org.unreal.modelrouter.monitor.tracing.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全配置
 *
 * <p>字段分两类（issue #224 逐项核过消费方）：</p>
 * <ul>
 *   <li><b>真正生效</b>：{@code sanitization.enabled} / {@code inherit-global-rules} /
 *       {@code additional-patterns} / {@code sensitive-attributes}（{@code TracingSanitizationService}）、
 *       {@code access-control.restrict-trace-access} / {@code allowed-roles}
 *       （{@code TracingSecurityManager}）；{@code sanitization.enabled} 与
 *       {@code access-control.enabled}（无字段，见 {@code TracingSecurityAutoConfiguration}）还是 Bean 注册条件。</li>
 *   <li><b>只被 {@code /actuator/info} 回显</b>：{@code encryption.enabled} / {@code algorithm} /
 *       {@code key-size} / {@code key-management.auto-rotation}。保留是因为那份回显是它们唯一的消费方，
 *       契约由 {@code TracingInfoContributorReportContractTest} 钉住。</li>
 * </ul>
 *
 * <p><b>已删除（issue #224 批 B）</b>：原先还有一整套 {@code audit.*}（审计落盘开关、日志文件、保留期，
 * 共 11 个字段）、{@code encryption.encrypt-sensitive-spans} / {@code encrypt-sensitive-logs}、
 * {@code key-management} 的轮换/密钥库/HSM 三项、整段 {@code data-retention.*}，以及
 * {@code access-control} 的 {@code field-access.*} / {@code max-access-history-per-user} /
 * {@code audit-access-attempts} —— 这些字段全仓零消费方，且语义上各自意味着一套未实现的子系统
 * （审计、密钥轮换、数据保留策略、字段级访问控制）。要提供这些能力，应先实现消费方，再按字段实名加回来。</p>
 */
@Data
public class TracingSecurityConfig {
    private SanitizationConfig sanitization = new SanitizationConfig();
    private AccessControlConfig accessControl = new AccessControlConfig();
    private EncryptionConfig encryption = new EncryptionConfig();

    /**
     * 脱敏配置。
     *
     * <p>消费方（issue #224 逐项核对）：{@code enabled} 由 {@code TracingSanitizationService.isEnabled()} 与
     * {@code TracingSecurityAutoConfiguration} 的条件读取；{@code inherit-global-rules} /
     * {@code additional-patterns} / {@code sensitive-attributes} 由 {@code TracingSanitizationService} 读取
     * （并回显到 /actuator/info）。</p>
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
    }

    @Data
    public static class EncryptionConfig {
        private boolean enabled = false;
        private String algorithm = "AES";
        private int keySize = 256;

        /**
         * 密钥管理。
         *
         * <p>只剩 {@code auto-rotation} —— 它被 {@code /actuator/info} 回显；轮换间隔、密钥库路径与 HSM
         * 三项零消费方，已随 issue #224 批 B 删除。</p>
         */
        private KeyManagement keyManagement = new KeyManagement();

        @Data
        public static class KeyManagement {
            private boolean autoRotation = true;
        }
    }
}
