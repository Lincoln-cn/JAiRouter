package org.unreal.modelrouter.router.adapter.checker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.adapter.AdapterCapabilities;
import org.unreal.modelrouter.common.model.ModelServiceRegistry;
import reactor.core.publisher.Mono;

/**
 * 适配器能力检查器
 *
 * 负责检查适配器是否支持指定的服务能力，提供统一的能力验证逻辑。
 *
 * @author JAiRouter Team
 * @since v2.2.7
 */
@Component
public class CapabilityChecker {

    private static final Logger logger = LoggerFactory.getLogger(CapabilityChecker.class);

    /**
     * 检查适配器是否支持指定的服务能力
     *
     * @param capabilities 适配器能力
     * @param serviceType 服务类型
     * @return 如果不支持返回错误响应，否则返回 null
     */
    public Mono<ResponseEntity<String>> checkCapability(
            final AdapterCapabilities capabilities,
            final ModelServiceRegistry.ServiceType serviceType) {

        if (capabilities == null) {
            logger.warn("适配器能力配置为空，无法检查服务类型：{}", serviceType);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Adapter capabilities not configured."));
        }

        if (!capabilities.contains(serviceType)) {
            String message = "This adapter does not support " + serviceType.name() + " capability.";
            logger.debug("能力检查失败：{}", message);
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(message));
        }

        logger.trace("能力检查通过：serviceType={}", serviceType);
        return null;
    }

    /**
     * 检查是否支持聊天服务
     *
     * @param capabilities 适配器能力
     * @return 如果支持返回 true
     */
    public boolean supportsChat(final AdapterCapabilities capabilities) {
        return capabilities != null && capabilities.isSupportChat();
    }

    /**
     * 检查是否支持嵌入服务
     *
     * @param capabilities 适配器能力
     * @return 如果支持返回 true
     */
    public boolean supportsEmbedding(final AdapterCapabilities capabilities) {
        return capabilities != null && capabilities.isSupportEmbedding();
    }

    /**
     * 检查是否支持重排序服务
     *
     * @param capabilities 适配器能力
     * @return 如果支持返回 true
     */
    public boolean supportsRerank(final AdapterCapabilities capabilities) {
        return capabilities != null && capabilities.isSupportRerank();
    }

    /**
     * 检查是否支持 TTS 服务
     *
     * @param capabilities 适配器能力
     * @return 如果支持返回 true
     */
    public boolean supportsTts(final AdapterCapabilities capabilities) {
        return capabilities != null && capabilities.isSupportTts();
    }

    /**
     * 检查是否支持 STT 服务
     *
     * @param capabilities 适配器能力
     * @return 如果支持返回 true
     */
    public boolean supportsStt(final AdapterCapabilities capabilities) {
        return capabilities != null && capabilities.isSupportStt();
    }

    /**
     * 检查是否支持图像生成服务
     *
     * @param capabilities 适配器能力
     * @return 如果支持返回 true
     */
    public boolean supportsImageGenerate(final AdapterCapabilities capabilities) {
        return capabilities != null && capabilities.isSupportImageGenerate();
    }

    /**
     * 检查是否支持图像编辑服务
     *
     * @param capabilities 适配器能力
     * @return 如果支持返回 true
     */
    public boolean supportsImageEdit(final AdapterCapabilities capabilities) {
        return capabilities != null && capabilities.isSupportImageEdit();
    }

    /**
     * 检查是否支持流式传输
     *
     * @param capabilities 适配器能力
     * @return 如果支持返回 true
     */
    public boolean supportsStreaming(final AdapterCapabilities capabilities) {
        return capabilities != null && capabilities.isSupportStreaming();
    }

    /**
     * 获取适配器支持的所有服务类型
     *
     * @param capabilities 适配器能力
     * @return 支持的服务类型列表
     */
    public String[] getSupportedServices(final AdapterCapabilities capabilities) {
        if (capabilities == null) {
            return new String[0];
        }

        return java.util.stream.Stream.of(
                capabilities.isSupportChat() ? "chat" : null,
                capabilities.isSupportEmbedding() ? "embedding" : null,
                capabilities.isSupportRerank() ? "rerank" : null,
                capabilities.isSupportTts() ? "tts" : null,
                capabilities.isSupportStt() ? "stt" : null,
                capabilities.isSupportImageGenerate() ? "imgGen" : null,
                capabilities.isSupportImageEdit() ? "imgEdit" : null
        ).filter(s -> s != null).toArray(String[]::new);
    }
}
