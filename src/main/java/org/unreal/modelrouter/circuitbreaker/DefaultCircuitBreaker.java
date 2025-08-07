package org.unreal.modelrouter.circuitbreaker;

public class DefaultCircuitBreaker implements CircuitBreaker {
    private final String instanceId;
    private final int failureThreshold;
    private final long timeout;
    private final int successThreshold;

    private State state = State.CLOSED;
    private int failureCount = 0;
    private int successCount = 0;
    private long lastFailureTime = 0;

    public DefaultCircuitBreaker(String instanceId, int failureThreshold,
                                 long timeout, int successThreshold) {
        this.instanceId = instanceId;
        this.failureThreshold = failureThreshold;
        this.timeout = timeout;
        this.successThreshold = successThreshold;
    }

    @Override
    public synchronized boolean canExecute() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                // 检查是否可以转为半开状态
                if (System.currentTimeMillis() - lastFailureTime >= timeout) {
                    state = State.HALF_OPEN;
                    successCount = 0;
                    return true;
                }
                return false;
            case HALF_OPEN:
                return true;
            default:
                return true;
        }
    }

    @Override
    public synchronized void onSuccess() {
        switch (state) {
            case CLOSED:
                failureCount = 0;
                break;
            case OPEN:
                break;
            case HALF_OPEN:
                successCount++;
                if (successCount >= successThreshold) {
                    // 恢复正常状态
                    state = State.CLOSED;
                    failureCount = 0;
                }
                break;
        }
    }

    @Override
    public synchronized void onFailure() {
        failureCount++;
        lastFailureTime = System.currentTimeMillis();

        switch (state) {
            case CLOSED:
                if (failureCount >= failureThreshold) {
                    state = State.OPEN;
                }
                break;
            case OPEN:
                break;
            case HALF_OPEN:
                // 失败则重新进入熔断状态
                state = State.OPEN;
                break;
        }
    }

    @Override
    public State getState() {
        return state;
    }
}
