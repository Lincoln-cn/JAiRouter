package org.unreal.modelrouter.persistence.jpa.repository.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.unreal.modelrouter.persistence.jpa.entity.ApiCallHistoryEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
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
        return withFilters(startTime, endTime, modelName, serviceType, apiKeyId, isSuccess,
                httpStatusCode, null, null);
    }

    /**
     * 带过滤条件与本人在场范围的分页查询。
     *
     * <p>{@code ownerUserId} / {@code ownerApiKeyIds} 用来实现「本人读」：记录归属当前账号
     * <b>或</b> 记录所用密钥属于当前账号创建的密钥，两者取并集。两个都为 null / 空表示不限制。</p>
     *
     * @param startTime       起始时间（含）
     * @param endTime         结束时间（含）
     * @param modelName       模型名
     * @param serviceType     服务类型
     * @param apiKeyId        API Key 标识（管理员显式筛选用）
     * @param isSuccess       是否成功
     * @param httpStatusCode  HTTP 状态码
     * @param ownerUserId     本人读：归属账号名
     * @param ownerApiKeyIds  本人读：本人创建的密钥集合
     * @return 动态条件
     */
    public static Specification<ApiCallHistoryEntity> withFilters(
            final LocalDateTime startTime,
            final LocalDateTime endTime,
            final String modelName,
            final String serviceType,
            final String apiKeyId,
            final Boolean isSuccess,
            final Integer httpStatusCode,
            final String ownerUserId,
            final Collection<String> ownerApiKeyIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            addOwnership(predicates, cb, root, ownerUserId, ownerApiKeyIds);
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
     * 归属范围：userId 命中 <b>或</b> apiKeyId 落在本人密钥集合内（并集）。
     *
     * <p>单独暴露出来，是为了让 {@link #errors(LocalDateTime, LocalDateTime)} 与
     * {@link #slowCalls(Long, LocalDateTime, LocalDateTime)} 这类既有规格能直接 {@code .and(...)}。</p>
     *
     * @param ownerUserId    本人账号名，可为 null
     * @param ownerApiKeyIds 本人创建的密钥，可为 null / 空
     * @return 动态条件
     */
    public static Specification<ApiCallHistoryEntity> ownership(final String ownerUserId,
                                                                final Collection<String> ownerApiKeyIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            addOwnership(predicates, cb, root, ownerUserId, ownerApiKeyIds);
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

    /**
     * 归属范围：userId 命中 <b>或</b> apiKeyId 落在本人密钥集合内。
     */
    private static void addOwnership(final List<Predicate> predicates,
                                     final CriteriaBuilder cb,
                                     final Root<ApiCallHistoryEntity> root,
                                     final String ownerUserId,
                                     final Collection<String> ownerApiKeyIds) {
        final List<Predicate> ownership = new ArrayList<>();
        if (ownerUserId != null && !ownerUserId.isBlank()) {
            ownership.add(cb.equal(root.get("userId"), ownerUserId));
        }
        if (ownerApiKeyIds != null && !ownerApiKeyIds.isEmpty()) {
            ownership.add(root.get("apiKeyId").in(ownerApiKeyIds));
        }
        if (!ownership.isEmpty()) {
            predicates.add(cb.or(ownership.toArray(Predicate[]::new)));
        }
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
