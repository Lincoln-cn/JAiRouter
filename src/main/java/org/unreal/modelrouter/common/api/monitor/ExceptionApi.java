package org.unreal.modelrouter.common.api.monitor;

import java.util.List;
import java.util.Map;

/**
 * 异常管理接口 - 其他模块通过此接口记录和查询异常。
 *
 * <p>此接口用于模块间通信：
 * <ul>
 *   <li>router 模块通过此接口记录路由异常</li>
 *   <li>auth 模块通过此接口记录认证异常</li>
 *   <li>persistence 模块通过此接口记录存储异常</li>
 * </ul>
 *
 * <p>微服务拆分后，此接口将成为 monitor-service 对外暴露的 API。
 *
 * @since v2.8.0
 */
public interface ExceptionApi {

    /**
     * 记录异常事件。
     *
     * @param exception 异常快照
     */
    void recordException(ExceptionSnapshot exception);

    /**
     * 查询异常事件。
     *
     * @param query 查询条件
     * @return 异常列表
     */
    List<ExceptionSnapshot> queryExceptions(ExceptionQuery query);

    /**
     * 获取异常统计信息。
     *
     * @return 统计数据
     */
    Map<String, Object> getExceptionStatistics();

    /**
     * 获取异常趋势。
     *
     * @param durationMinutes 时间范围（分钟）
     * @return 异常趋势数据
     */
    List<Map<String, Object>> getExceptionTrend(int durationMinutes);

    // === 内部 DTO ===

    /**
     * 异常快照。
     */
    class ExceptionSnapshot {
        private String eventId;
        private String componentType;
        private String componentId;
        private String exceptionType;
        private String message;
        private String stackTrace;
        private String severity;
        private long timestamp;
        private Map<String, Object> context;

        public ExceptionSnapshot() {
        }

        public String getEventId() {
            return eventId;
        }

        public void setEventId(String eventId) {
            this.eventId = eventId;
        }

        public String getComponentType() {
            return componentType;
        }

        public void setComponentType(String componentType) {
            this.componentType = componentType;
        }

        public String getComponentId() {
            return componentId;
        }

        public void setComponentId(String componentId) {
            this.componentId = componentId;
        }

        public String getExceptionType() {
            return exceptionType;
        }

        public void setExceptionType(String exceptionType) {
            this.exceptionType = exceptionType;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getStackTrace() {
            return stackTrace;
        }

        public void setStackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        public Map<String, Object> getContext() {
            return context;
        }

        public void setContext(Map<String, Object> context) {
            this.context = context;
        }
    }

    /**
     * 异常查询条件。
     */
    class ExceptionQuery {
        private String componentType;
        private String exceptionType;
        private String severity;
        private long startTime;
        private long endTime;
        private int limit;
        private int offset;

        public ExceptionQuery() {
        }

        public String getComponentType() {
            return componentType;
        }

        public void setComponentType(String componentType) {
            this.componentType = componentType;
        }

        public String getExceptionType() {
            return exceptionType;
        }

        public void setExceptionType(String exceptionType) {
            this.exceptionType = exceptionType;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
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