package org.unreal.modelrouter.ratelimit;

import org.springframework.stereotype.Component;
import org.unreal.modelrouter.ratelimit.impl.*;
/**
 * 限流器工厂实现
 */
@Component
public class RateLimiterFactory {

    public RateLimiter createRateLimiter(RateLimitConfig config) {
        if (config == null) {
            return null;
        }

        switch (config.getAlgorithm().toLowerCase()) {
            case "token-bucket":
                return new TokenBucketRateLimiter(config);
            case "leaky-bucket":
                return new LeakyBucketRateLimiter(config);
            case "sliding-window":
                return new SlidingWindowRateLimiter(config);
            default:
                throw new IllegalArgumentException("Unsupported rate limit algorithm: " + config.getAlgorithm());
        }
    }
}