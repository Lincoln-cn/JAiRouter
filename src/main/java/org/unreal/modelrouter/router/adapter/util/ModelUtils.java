package org.unreal.modelrouter.router.adapter.util;

import org.unreal.modelrouter.common.constants.ServiceTypeConstants;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.common.dto.EmbeddingDTO;
import org.unreal.modelrouter.common.dto.ImageEditDTO;
import org.unreal.modelrouter.common.dto.ImageGenerateDTO;
import org.unreal.modelrouter.common.dto.RerankDTO;
import org.unreal.modelrouter.common.dto.SttDTO;
import org.unreal.modelrouter.common.dto.TtsDTO;

/**
 * 模型相关工具方法
 *
 * 从 BaseAdapter 提取的工具类，用于处理请求对象中的模型信息提取。
 *
 * @author JAiRouter Team
 * @since v2.26.5
 */
public final class ModelUtils {

    private ModelUtils() {
        // 工具类禁止实例化
    }

    /**
     * 从请求对象中提取模型名称
     *
     * @param request 请求对象
     * @return 模型名称，如果无法获取则返回 "unknown"
     */
    public static String getModelNameFromRequest(final Object request) {
        if (request == null) {
            return "unknown";
        }

        try {
            // 使用反射获取 model 字段
            java.lang.reflect.Method modelMethod = request.getClass().getMethod("model");
            Object modelName = modelMethod.invoke(request);
            return modelName != null ? modelName.toString() : "unknown";
        } catch (Exception e) {
            // 如果无法获取模型名称，返回 unknown
            return "unknown";
        }
    }

    /**
     * 从请求对象中提取服务类型
     *
     * @param request 请求对象
     * @return 服务类型字符串
     */
    public static String getServiceTypeFromRequest(final Object request) {
        if (request == null) {
            return "unknown";
        }

        // 根据请求类型判断服务类型
        if (request instanceof ChatDTO.Request) {
            return ServiceTypeConstants.CHAT;
        } else if (request instanceof EmbeddingDTO.Request) {
            return ServiceTypeConstants.EMBEDDING;
        } else if (request instanceof RerankDTO.Request) {
            return ServiceTypeConstants.RERANK;
        } else if (request instanceof TtsDTO.Request) {
            return ServiceTypeConstants.TTS;
        } else if (request instanceof SttDTO.Request) {
            return ServiceTypeConstants.STT;
        } else if (request instanceof ImageGenerateDTO.Request) {
            return ServiceTypeConstants.IMG_GEN;
        } else if (request instanceof ImageEditDTO.Request) {
            return ServiceTypeConstants.IMG_EDIT;
        } else {
            return "unknown";
        }
    }
}