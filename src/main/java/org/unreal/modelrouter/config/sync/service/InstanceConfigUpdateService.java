package org.unreal.modelrouter.config.sync.service;

import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;

/**
 * Instance config update service interface.
 *
 * @since v2.6.12
 */
public interface InstanceConfigUpdateService {

    Mono<UpdateResult> updateInstanceConfig(String instanceId, Map<String, Object> config);

    Mono<BatchUpdateResult> batchUpdate(List<InstanceConfigUpdate> updates);

    record InstanceConfigUpdate(String instanceId, Map<String, Object> config) {}

    record UpdateResult(boolean success, String instanceId, String message) {
        public static UpdateResult success(String instanceId) {
            return new UpdateResult(true, instanceId, "Update successful");
        }
        public static UpdateResult failure(String instanceId, String message) {
            return new UpdateResult(false, instanceId, message);
        }
    }

    record BatchUpdateResult(boolean success, int successCount, int failureCount, List<UpdateResult> results) {
        public static BatchUpdateResult of(List<UpdateResult> results) {
            long successCnt = results.stream().filter(UpdateResult::success).count();
            long failureCnt = results.size() - successCnt;
            return new BatchUpdateResult(failureCnt == 0, (int) successCnt, (int) failureCnt, results);
        }
    }
}
