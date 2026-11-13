package org.unreal.modelrouter.persistence.jpa.repository.spec;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.unreal.modelrouter.persistence.jpa.entity.ExceptionEventEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 异常事件的动态查询条件。
 *
 * <p>与原 JPQL 一致的语义差异被保留：列表查询包含 {@code serviceType}/{@code modelName}，
 * 而计数条件不包含这两项。</p>
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
public final class ExceptionEventSpecifications {

    private static final String OCCURRED_AT = "occurredAt";

    private ExceptionEventSpecifications() {
    }

    /**
     * 列表查询条件（含服务类型与模型名）。
     *
     * @param startTime     起始时间（含）
     * @param endTime       结束时间（含）
     * @param exceptionType 异常类型（模糊匹配）
     * @param operation     操作名（模糊匹配）
     * @param errorCode     错误码
     * @param errorCategory 错误分类
     * @param traceId       追踪标识
     * @param clientIp      客户端 IP
     * @param isAggregated  是否聚合
     * @param serviceType   服务类型
     * @param modelName     模型名（模糊匹配）
     * @return 动态条件
     */
    public static Specification<ExceptionEventEntity> of(
            final LocalDateTime startTime,
            final LocalDateTime endTime,
            final String exceptionType,
            final String operation,
            final String errorCode,
            final String errorCategory,
            final String traceId,
            final String clientIp,
            final Boolean isAggregated,
            final String serviceType,
            final String modelName) {
        return (root, query, cb) -> {
            List<Predicate> predicates = base(
                    startTime, endTime, exceptionType, operation, errorCode, errorCategory,
                    traceId, clientIp, isAggregated, cb, root);
            if (serviceType != null) {
                predicates.add(cb.equal(root.get("serviceType"), serviceType));
            }
            if (modelName != null) {
                predicates.add(cb.like(root.get("modelName"), "%" + modelName + "%"));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * 计数条件（不含服务类型与模型名，与原实现一致）。
     *
     * @param startTime     起始时间（含）
     * @param endTime       结束时间（含）
     * @param exceptionType 异常类型（模糊匹配）
     * @param operation     操作名（模糊匹配）
     * @param errorCode     错误码
     * @param errorCategory 错误分类
     * @param traceId       追踪标识
     * @param clientIp      客户端 IP
     * @param isAggregated  是否聚合
     * @return 动态条件
     */
    public static Specification<ExceptionEventEntity> countOf(
            final LocalDateTime startTime,
            final LocalDateTime endTime,
            final String exceptionType,
            final String operation,
            final String errorCode,
            final String errorCategory,
            final String traceId,
            final String clientIp,
            final Boolean isAggregated) {
        return (root, query, cb) -> cb.and(base(
                startTime, endTime, exceptionType, operation, errorCode, errorCategory,
                traceId, clientIp, isAggregated, cb, root).toArray(Predicate[]::new));
    }

    private static List<Predicate> base(
            final LocalDateTime startTime,
            final LocalDateTime endTime,
            final String exceptionType,
            final String operation,
            final String errorCode,
            final String errorCategory,
            final String traceId,
            final String clientIp,
            final Boolean isAggregated,
            final jakarta.persistence.criteria.CriteriaBuilder cb,
            final jakarta.persistence.criteria.Root<ExceptionEventEntity> root) {
        List<Predicate> predicates = new ArrayList<>();
        if (startTime != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(OCCURRED_AT), startTime));
        }
        if (endTime != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(OCCURRED_AT), endTime));
        }
        if (exceptionType != null) {
            predicates.add(cb.like(root.get("exceptionType"), "%" + exceptionType + "%"));
        }
        if (operation != null) {
            predicates.add(cb.like(root.get("operation"), "%" + operation + "%"));
        }
        if (errorCode != null) {
            predicates.add(cb.equal(root.get("errorCode"), errorCode));
        }
        if (errorCategory != null) {
            predicates.add(cb.equal(root.get("errorCategory"), errorCategory));
        }
        if (traceId != null) {
            predicates.add(cb.equal(root.get("traceId"), traceId));
        }
        if (clientIp != null) {
            predicates.add(cb.equal(root.get("clientIp"), clientIp));
        }
        if (isAggregated != null) {
            predicates.add(cb.equal(root.get("isAggregated"), isAggregated));
        }
        return predicates;
    }
}
