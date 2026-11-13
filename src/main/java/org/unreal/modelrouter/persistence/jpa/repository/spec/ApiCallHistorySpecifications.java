package org.unreal.modelrouter.persistence.jpa.repository.spec;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.unreal.modelrouter.persistence.jpa.entity.ApiCallHistoryEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 调用历史的动态查询条件。
 *
 * @author JAiRouter Team
 * @since 3.8.6
 */
public final class ApiCallHistorySpecifications {

    private static final String CREATED_AT = "createdAt";

    private ApiCallHistorySpecifications() {
    }

    /**
     * 带过滤条件的分页查询。
     *
     * @param startTime      起始时间（含）
     * @param endTime        结束时间（含）
     * @param modelName      模型名
     * @param serviceType    服务类型
     * @param apiKeyId       API Key 标识
     * @param isSuccess      是否成功
     * @param httpStatusCode HTTP 状态码
     * @return 动态条件
     */
    public static Specification<ApiCallHistoryEntity> withFilters(
            final LocalDateTime startTime,
            final LocalDateTime endTime,
            final String modelName,
            final String serviceType,
            final String apiKeyId,
            final Boolean isSuccess,
            final Integer httpStatusCode) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            addTimeRange(predicates, cb, root, startTime, endTime);
            if (modelName != null) {
                predicates.add(cb.equal(root.get("modelName"), modelName));
            }
            if (serviceType != null) {
                predicates.add(cb.equal(root.get("serviceType"), serviceType));
            }
            if (apiKeyId != null) {
                predicates.add(cb.equal(root.get("apiKeyId"), apiKeyId));
            }
            if (isSuccess != null) {
                predicates.add(cb.equal(root.get("isSuccess"), isSuccess));
            }
            if (httpStatusCode != null) {
                predicates.add(cb.equal(root.get("httpStatusCode"), httpStatusCode));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * 失败调用。
     *
     * @param startTime 起始时间（含）
     * @param endTime   结束时间（含）
     * @return 动态条件
     */
    public static Specification<ApiCallHistoryEntity> errors(
            final LocalDateTime startTime,
            final LocalDateTime endTime) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isSuccess")));
            addTimeRange(predicates, cb, root, startTime, endTime);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * 慢调用（响应时间超过阈值）。
     *
     * @param thresholdMillis 阈值（毫秒）
     * @param startTime       起始时间（含）
     * @param endTime         结束时间（含）
     * @return 动态条件
     */
    public static Specification<ApiCallHistoryEntity> slowCalls(
            final long thresholdMillis,
            final LocalDateTime startTime,
            final LocalDateTime endTime) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.greaterThan(root.get("responseTimeMs"), thresholdMillis));
            addTimeRange(predicates, cb, root, startTime, endTime);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addTimeRange(
            final List<Predicate> predicates,
            final jakarta.persistence.criteria.CriteriaBuilder cb,
            final jakarta.persistence.criteria.Root<ApiCallHistoryEntity> root,
            final LocalDateTime startTime,
            final LocalDateTime endTime) {
        if (startTime != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(CREATED_AT), startTime));
        }
        if (endTime != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(CREATED_AT), endTime));
        }
    }
}
