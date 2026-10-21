package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.SanitizationConfigEntity;

/**
 * 脱敏配置快照仓库（TA2 / v3.4.0）。
 *
 * <p>主键即 scope（request / response），因此只需要按主键读写：
 * {@link #findById} 判定某个 scope 是否有持久化快照，{@link #save} 覆盖写。</p>
 *
 * @author JAiRouter Team
 * @since 3.4.0
 */
@Repository
public interface SanitizationConfigRepository extends JpaRepository<SanitizationConfigEntity, String> {
}
