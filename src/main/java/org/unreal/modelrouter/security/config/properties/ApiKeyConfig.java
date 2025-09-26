package org.unreal.modelrouter.security.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * API Key配置类
 */
@Data
public class ApiKeyConfig {
    /**
     * 是否启用API Key认证
     */
    private boolean enabled = true;

    /**
     * API Key请求头名称
     */
    @NotBlank
    @Size(min = 1, max = 100)
    private String headerName = "X-API-Key";

    /**
     * 预配置的API Key列表
     */
    @Valid
    private List<ApiKeyProperties> keys = new ArrayList<>();

    /**
     * 默认过期天数
     */
    @Min(1)
    @Max(3650)
    private long defaultExpirationDays = 365;

    /**
     * 缓存过期时间（秒）
     */
    @Min(60)
    @Max(86400)
    private long cacheExpirationSeconds = 3600;


}
