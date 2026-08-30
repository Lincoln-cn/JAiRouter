package org.unreal.modelrouter.auth.security.service;

/**
 * 过期密钥统计
 */
public class ApiKeyExpirationStats {
    private final int totalKeys;
    private final int expiredKeys;
    private final int expiringToday;
    private final int disabledKeys;

    public ApiKeyExpirationStats(final int totalKeys, final int expiredKeys,
                                 final int expiringToday, final int disabledKeys) {
        this.totalKeys = totalKeys;
        this.expiredKeys = expiredKeys;
        this.expiringToday = expiringToday;
        this.disabledKeys = disabledKeys;
    }

    public int getTotalKeys() {
        return totalKeys;
    }

    public int getExpiredKeys() {
        return expiredKeys;
    }

    public int getExpiringToday() {
        return expiringToday;
    }

    public int getDisabledKeys() {
        return disabledKeys;
    }
}
