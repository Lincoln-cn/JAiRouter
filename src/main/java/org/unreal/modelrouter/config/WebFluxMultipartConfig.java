package org.unreal.modelrouter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * WebFlux Multipart 配置
 * 确保multipart请求能够正确处理
 * 兼容各个Spring版本的简化配置
 */
@Configuration
public class WebFluxMultipartConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        // 配置内存缓冲区大小 - 这是最重要的配置
        configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024); // 10MB

        // 启用请求详情日志记录（仅用于调试，生产环境建议关闭）
        configurer.defaultCodecs().enableLoggingRequestDetails(true);
    }
}