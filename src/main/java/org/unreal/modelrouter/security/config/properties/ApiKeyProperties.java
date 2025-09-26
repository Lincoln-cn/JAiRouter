package org.unreal.modelrouter.security.config.properties;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;
import org.unreal.modelrouter.security.model.ApiKeyInfo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ApiKeyProperties {
    /**
     *           keyId: "dev-admin-key"
     *           keyValue: "dev-admin-12345-abcde-67890-fghij"
     *           description: "开发环境管理员测试密钥"
     *           permissions: ["ADMIN", "READ", "WRITE", "DELETE"]
     *           expiresAt: "2025-12-31T23:59:59"
     *           enabled: true
     *           metadata:
     *             environment: "development"
     *             created-by: "developer"
     */
    private String keyId;

    private String keyValue;

    private String description;

    private List<String> permissions;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime expiresAt;

    private boolean enabled;

    private Map<String, Object> metadata;

    public ApiKeyInfo covertTo() {
        return ApiKeyInfo.builder()
                .keyId(this.keyId)
                .keyValue(this.keyValue)
                .description(this.description)
                .expiresAt(this.expiresAt)
                .permissions(this.permissions)
                .metadata(this.metadata)
                .build();
    }
}
