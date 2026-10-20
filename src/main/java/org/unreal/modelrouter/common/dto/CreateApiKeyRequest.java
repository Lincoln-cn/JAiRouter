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
 * 创建API Key请求DTO
 *
 * <p>创建密钥的入参：keyId、描述、权限、启用标记、元数据与过期时间。
 * <p><b>Deprecated / zero consumers:</b> nothing outside this file references it &mdash; the main + test
 * reference count is 0 (scan on 2026-10-20).
 *
 * <p><b>What it used to do:</b> it was the create-key request body &mdash; keyId, description,
 * permissions, enabled, metadata and expiresAt. Key creation now binds {@code ApiKeyCreateRequest} (the
 * {@code @Valid @RequestBody} of {@code ApiKeyManagementController.createApiKey}), which adds IP
 * allow-listing, quota limits and rotation period.
 *
 * <p><b>Before deleting:</b> it is a request DTO no controller takes &mdash; check external clients and
 * the published API docs.
 *
 * @deprecated No caller remains in main or test sources (scan 2026-10-20); kept only until the dead-code
 *             sweep deletes it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Deprecated(forRemoval = true)
public final class CreateApiKeyRequest {

    private String keyId;
    private String description;
    private List<String> permissions;
    private Boolean enabled;
    private Map<String, Object> metadata;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime expiresAt;
}
