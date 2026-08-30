package org.unreal.modelrouter.auth.security.service;

import org.unreal.modelrouter.auth.security.dto.ApiKeyCreationVO;

/**
 * 单个密钥导入结果
 */
public class ApiKeyImportResult {
    private final boolean success;
    private final String keyId;
    private final ApiKeyCreationVO creationVO;
    private final String errorMessage;

    private ApiKeyImportResult(final boolean success, final String keyId,
                               final ApiKeyCreationVO creationVO, final String errorMessage) {
        this.success = success;
        this.keyId = keyId;
        this.creationVO = creationVO;
        this.errorMessage = errorMessage;
    }

    public static ApiKeyImportResult success(final String keyId, final ApiKeyCreationVO creationVO) {
        return new ApiKeyImportResult(true, keyId, creationVO, null);
    }

    public static ApiKeyImportResult failure(final String keyId, final String errorMessage) {
        return new ApiKeyImportResult(false, keyId, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getKeyId() {
        return keyId;
    }

    public ApiKeyCreationVO getCreationVO() {
        return creationVO;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
