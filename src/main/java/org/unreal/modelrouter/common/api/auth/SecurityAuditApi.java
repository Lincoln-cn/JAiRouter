package org.unreal.modelrouter.common.api.auth;

import java.util.List;
import java.util.Map;

/**
 * 安全审计接口 - 其他模块通过此接口记录和查询安全事件。
 *
 * <p>此接口用于模块间通信：
 * <ul>
 *   <li>monitor 模块通过此接口记录安全事件</li>
 *   <li>router 模块通过此接口记录访问审计</li>
 * </ul>
 *
 * <p>微服务拆分后，此接口将成为 auth-service 对外暴露的 API。
 *
 * @since v2.8.0
 */
public interface SecurityAuditApi {

    /**
     * 记录安全事件。
     *
     * @param event 安全事件
     */
    void recordSecurityEvent(SecurityEvent event);

    /**
     * 查询安全事件。
     *
     * @param query 查询条件
     * @return 事件列表
     */
    List<SecurityEvent> querySecurityEvents(SecurityEventQuery query);

    /**
     * 获取安全统计信息。
     *
     * @return 统计数据
     */
    Map<String, Object> getSecurityStatistics();

    // === 内部 DTO ===

    /**
     * 安全事件。
     */
    class SecurityEvent {
        private String eventId;
        private String eventType;
        private String accountId;
        private String sourceIp;
        private String targetResource;
        private String action;
        private String result;
        private String riskLevel;
        private long timestamp;
        private Map<String, Object> metadata;

        public SecurityEvent() {
        }

        public String getEventId() {
            return eventId;
        }

        public void setEventId(String eventId) {
            this.eventId = eventId;
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public String getSourceIp() {
            return sourceIp;
        }

        public void setSourceIp(String sourceIp) {
            this.sourceIp = sourceIp;
        }

        public String getTargetResource() {
            return targetResource;
        }

        public void setTargetResource(String targetResource) {
            this.targetResource = targetResource;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata;
        }
    }

    /**
     * 安全事件查询条件。
     */
    class SecurityEventQuery {
        private String eventType;
        private String accountId;
        private String riskLevel;
        private long startTime;
        private long endTime;
        private int limit;
        private int offset;

        public SecurityEventQuery() {
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public String getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(String riskLevel) {
            this.riskLevel = riskLevel;
        }

        public long getStartTime() {
            return startTime;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        public long getEndTime() {
            return endTime;
        }

        public void setEndTime(long endTime) {
            this.endTime = endTime;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public int getOffset() {
            return offset;
        }

        public void setOffset(int offset) {
            this.offset = offset;
        }
    }
}