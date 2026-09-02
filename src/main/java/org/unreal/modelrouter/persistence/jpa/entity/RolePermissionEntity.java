package org.unreal.modelrouter.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 角色-权限映射实体（v2.9.8 RBAC 数据驱动权限体系）
 *
 * <p>存储角色模板到权限码的映射关系，一个角色可拥有多个权限码，
 * 同一权限码也可被多个角色共享，通过唯一约束 {@code role_name + permission_code} 去重。
 *
 * <p>新表由 Hibernate {@code ddl-auto: update} 自动创建，无需登记 CompatibilitySchemaMigrator。
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "role_permissions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_role_permission", columnNames = {"role_name", "permission_code"})
})
public class RolePermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 角色名（大写，如 ADMIN / OPERATOR / USER / VIEWER） */
    @Column(name = "role_name", nullable = false, length = 64)
    private String roleName;

    /** 权限码（module:resource:action，如 config:services:read） */
    @Column(name = "permission_code", nullable = false, length = 128)
    private String permissionCode;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
