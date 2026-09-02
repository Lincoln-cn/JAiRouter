package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.RolePermissionEntity;

import java.util.Collection;
import java.util.List;

/**
 * 角色-权限映射仓库（v2.9.8 RBAC 数据驱动权限体系）
 *
 * @author JAiRouter Team
 * @since 2.9.8
 */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermissionEntity, Long> {

    /**
     * 按角色名查询权限映射
     *
     * @param roleName 角色名
     * @return 权限映射列表
     */
    List<RolePermissionEntity> findByRoleName(String roleName);

    /**
     * 按多个角色名批量查询权限映射
     *
     * @param roleNames 角色名集合
     * @return 权限映射列表
     */
    List<RolePermissionEntity> findByRoleNameIn(Collection<String> roleNames);

    /**
     * 删除某角色的全部权限映射（权限管理 API 更新角色时使用）
     *
     * @param roleName 角色名
     */
    void deleteByRoleName(String roleName);
}
