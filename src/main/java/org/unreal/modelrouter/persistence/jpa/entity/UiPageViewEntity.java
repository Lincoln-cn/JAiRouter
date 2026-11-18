package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 控制台页面访问记录（B4，2026-11-18）.
 *
 * <p><b>为什么单独建表，而不是塞进 {@code security_audit}</b>：</p>
 * <ol>
 *   <li>语义不同。审计表回答「谁做了什么敏感操作」，页面浏览回答「谁在用哪个页面」；
 *       把浏览记录混进审计流，会让按事件类型统计审计的口径失真，也让审计页被浏览流水淹没。</li>
 *   <li>{@code security_audit.event_type} 在库层是 ENUM，新增取值必须写迁移脚本
 *       （见 {@code V7__audit_event_type_values.sql} 的注释：漏写时写入静默失败）。
 *       浏览统计是高频、低价值、可丢弃的数据，不该让它去承担那个约束的风险。</li>
 * </ol>
 *
 * <p>记录粒度刻意收窄：<b>只记 主体 + 路径 + 角色 + 时间</b>，不记客户端 IP、不记查询参数。
 * 「谁看过哪一页」是本次需求的原话（用户决定，m10072），超出这个范围的识别信息不采。</p>
 *
 * @since 2026-11（B4）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ui_page_views", indexes = {
        @Index(name = "idx_ui_page_view_viewed_at", columnList = "viewed_at"),
        @Index(name = "idx_ui_page_view_path", columnList = "path, viewed_at")
})
public class UiPageViewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 访问主体（登录用户名）。 */
    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    /** 站内路由路径，不含查询串与 hash。 */
    @Column(name = "path", nullable = false, length = 200)
    private String path;

    /** 上报时的角色名（可为空：角色可能尚未同步）。 */
    @Column(name = "role", length = 32)
    private String role;

    /** 访问时间。 */
    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;
}
