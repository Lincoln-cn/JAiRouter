package org.unreal.modelrouter.config.core.helper;

/**
 * 出站 baseUrl SSRF 门闩（R3-P0）。
 *
 * <p>统一委托 {@link org.unreal.modelrouter.common.util.UrlSecurityValidator}，
 * 在配置写入与代理出站两处复用，拦截云元数据 / 链路本地等危险目标。</p>
 */
public final class SsrfGuard {

    private SsrfGuard() {
    }

    /**
     * 校验出站 baseUrl；非法时抛 {@link SecurityException}。
     *
     * @return 规范化后的 baseUrl
     */
    public static String validateOutboundBaseUrl(final String baseUrl) {
        return org.unreal.modelrouter.common.util.UrlSecurityValidator.validateOutboundBaseUrl(baseUrl);
    }
}
