package org.unreal.modelrouter.auth.security.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * API Key 批量导入结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyBatchImportResult {

    /**
     * 导入总数
     */
    private int totalAttempted;

    /**
     * 成功导入数量
     */
    private int successCount;

    /**
     * 失败数量
     */
    private int failureCount;

    /**
     * 成功导入的密钥（包含原始 keyValue，仅此一次显示）
     */
    private List<ApiKeyCreationVO> importedKeys;

    /**
     * 失败的导入项及错误原因
     */
    private List<ImportError> errors;

    /**
     * 导入错误详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportError {
        /**
         * 失败的 keyId（如果有）
         */
        private String keyId;

        /**
         * 失败原因
         */
        private String reason;
    }
}