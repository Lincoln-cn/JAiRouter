package org.unreal.modelrouter.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * API Key创建响应DTO
 *
 * <p>包含 keyValue 字段，仅在创建时返回。
 * <p><b>Deprecated / zero consumers:</b> nothing outside this file references it &mdash; the main + test
 * reference count is 0 (scan on 2026-10-20).
 *
 * <p><b>What it used to do:</b> it was the create-key response of the API-key API &mdash; keyId plus the
 * one-time {@code keyValue} (returned on creation only), description, permissions, enabled, expiresAt and
 * createdAt. Key creation now answers with {@code ApiKeyCreationVO} from
 * {@code ApiKeyManagementController.createApiKey}, which also carries createdBy, quota, rotation and the
 * one-time-display warning.
 *
 * <p><b>Before deleting:</b> it is a response DTO no controller returns &mdash; confirm that no client
 * parses this JSON shape and that it has no OpenAPI schema.
 *
 * @deprecated No caller remains in main or test sources (scan 2026-10-20); kept only until the dead-code
 *             sweep deletes it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Deprecated(forRemoval = true)
public final class ApiKeyCreationResponse {

    private String keyId;

    /**
     * API Key值 - 仅在创建时返回，需要前端弹窗展示
     */
    private String keyValue;

    private String description;

    private List<String> permissions;

    private boolean enabled;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime expiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime createdAt;

    private Map<String, Object> metadata;
}
