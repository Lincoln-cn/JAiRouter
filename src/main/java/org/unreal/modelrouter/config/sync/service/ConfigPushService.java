package org.unreal.modelrouter.config.sync.service;

import reactor.core.publisher.Mono;
import java.util.Map;

/**
 * Config push service interface.
 *
 * @since v2.6.12
 */
public interface ConfigPushService {

    Mono<PushResult> pushToService(String targetService, Map<String, Object> config);

    Mono<BroadcastResult> broadcastConfig(Map<String, Object> config);

    record PushResult(boolean success, String targetService, String message, int httpStatus) {
        public static PushResult success(String targetService) {
            return new PushResult(true, targetService, "Push successful", 200);
        }
        public static PushResult failure(String targetService, String message) {
            return new PushResult(false, targetService, message, -1);
        }
    }

    record BroadcastResult(boolean success, int successCount, int failureCount, String message) {
        public static BroadcastResult success(int count) {
            return new BroadcastResult(true, count, 0, "Broadcast successful");
        }
        public static BroadcastResult partial(int success, int failure) {
            return new BroadcastResult(false, success, failure, "Broadcast partially failed");
        }
    }
}
