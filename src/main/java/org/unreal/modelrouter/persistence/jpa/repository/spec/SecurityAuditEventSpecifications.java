package org.unreal.modelrouter.persistence.jpa.repository.spec;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity.RiskLevel;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 安全审计事件的动态查询条件。
 *
 * <p>刻意不用 JPQL 的 {@code (:param IS NULL OR ...)} 写法：PostgreSQL 无法从
 * {@code (? is null or e.timestamp >= ?)} 推断参数类型，运行期报
 * {@code could not determine data type of parameter $1}；H2 能推断，所以单测从不暴露。
 * 动态条件只在参数非空时才加入 SQL，两种方言都只看到确定类型的绑定变量。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
public final class SecurityAuditEventSpecifications {

    private static final String TIMESTAMP = "timestamp";

    private SecurityAuditEventSpecifications() {
    }

    /**
     * 组合查询条件：所有参数均可为 null，为 null 表示不限制。
     *
     * @param startTime     起始时间（含）
     * @param endTime       结束时间（含）
     * @param eventTypes    事件类型集合（null 或空表示不限制）
     * @param userId        用户标识
     * @param resourceId    资源标识
     * @param clientIp      客户端 IP
     * @param success       是否成功
     * @param eventCategory 事件分类
     * @param riskLevel     风险等级
     * @return 动态条件
     */
    public static Specification<SecurityAuditEventEntity> of(
            final LocalDateTime startTime,
            final LocalDateTime endTime,
            final Collection<AuditEventType> eventTypes,
            final String userId,
            final String resourceId,
            final String clientIp,
            final Boolean success,
            final String eventCategory,
            final RiskLevel riskLevel) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get(TIMESTAMP), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get(TIMESTAMP), endTime));
            }
            if (eventTypes != null && !eventTypes.isEmpty()) {
                predicates.add(root.get("eventType").in(eventTypes));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (resourceId != null) {
                predicates.add(cb.equal(root.get("resourceId"), resourceId));
            }
            if (clientIp != null) {
                predicates.add(cb.equal(root.get("clientIp"), clientIp));
            }
            if (success != null) {
                predicates.add(cb.equal(root.get("success"), success));
            }
            if (eventCategory != null) {
                predicates.add(cb.equal(root.get("eventCategory"), eventCategory));
            }
            if (riskLevel != null) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
