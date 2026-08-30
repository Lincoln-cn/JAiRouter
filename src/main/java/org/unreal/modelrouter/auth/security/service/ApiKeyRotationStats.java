package org.unreal.modelrouter.auth.security.service;

/**
 * 密钥轮换统计
 */
public class ApiKeyRotationStats {
    private final int totalKeys;
    private final int keysWithRotation;
    private final int keysNeedingRotation;
    private final int rotatedToday;

    public ApiKeyRotationStats(final int totalKeys, final int keysWithRotation,
                               final int keysNeedingRotation, final int rotatedToday) {
        this.totalKeys = totalKeys;
        this.keysWithRotation = keysWithRotation;
        this.keysNeedingRotation = keysNeedingRotation;
        this.rotatedToday = rotatedToday;
    }

    public int getTotalKeys() {
        return totalKeys;
    }

    public int getKeysWithRotation() {
        return keysWithRotation;
    }

    public int getKeysNeedingRotation() {
        return keysNeedingRotation;
    }

    public int getRotatedToday() {
        return rotatedToday;
    }
}
