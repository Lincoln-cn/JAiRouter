package org.unreal.modelrouter.common.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 更新API Key请求DTO
 *
 * <p>更新密钥的入参：描述、权限、启用标记、元数据与过期时间。
 * <p><b>Deprecated / zero consumers:</b> nothing outside this file references it &mdash; the main + test
 * reference count is 0 (scan on 2026-10-20).
 *
 * <p><b>What it used to do:</b> it was the update-key request body &mdash; description, permissions,
 * enabled, metadata and expiresAt. Key updates now bind {@code ApiKeyUpdateRequest} (the
 * {@code @Valid @RequestBody} of {@code ApiKeyManagementController.updateApiKey}), which adds IP
 * allow-listing and quota limits and never rewrites {@code keyValue}.
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
public final class UpdateApiKeyRequest {

    private String description;
    private List<String> permissions;
    private Boolean enabled;
    private Map<String, Object> metadata;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime expiresAt;
}
