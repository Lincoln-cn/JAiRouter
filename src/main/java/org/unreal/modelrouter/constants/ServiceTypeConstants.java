package org.unreal.modelrouter.constants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 服务类型常量
 * 
 * 统一管理服务类型常量，避免魔法字符串。
 * 
 * @author JAiRouter Team
 * @since v2.1.0
 */
public final class ServiceTypeConstants {

    // 服务类型常量
    public static final String CHAT = "chat";
    public static final String EMBEDDING = "embedding";
    public static final String RERANK = "rerank";
    public static final String TTS = "tts";
    public static final String STT = "stt";
    public static final String IMG_GEN = "imgGen";
    public static final String IMG_EDIT = "imgEdit";

    // 所有支持的服务类型
    private static final Set<String> ALL_SERVICE_TYPES = Collections.unmodifiableSet(
        Arrays.stream(ServiceType.values())
            .map(Enum::name)
            .collect(Collectors.toSet())
    );

    // 服务类型列表（有序）
    private static final List<String> SERVICE_TYPE_LIST = Collections.unmodifiableList(
        Arrays.asList(CHAT, EMBEDDING, RERANK, TTS, STT, IMG_GEN, IMG_EDIT)
    );

    private ServiceTypeConstants() {
        // 防止实例化
    }

    /**
     * 服务类型枚举
     */
    public enum ServiceType {
        CHAT("chat", "聊天模型"),
        EMBEDDING("embedding", "嵌入模型"),
        RERANK("rerank", "重排序模型"),
        TTS("tts", "语音合成"),
        STT("stt", "语音识别"),
        IMG_GEN("imgGen", "图像生成"),
        IMG_EDIT("imgEdit", "图像编辑");

        private final String value;
        private final String description;

        ServiceType(final String value,final String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        /**
         * 从字符串值转换为枚举
         */
        public static ServiceType fromString(final String value) {
            if (value == null) {
                return null;
            }
            for (ServiceType type : values()) {
                if (type.value.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("未知的服务类型：" + value);
        }

        /**
         * 检查是否是有效的服务类型
         */
        public static boolean isValid(final String value) {
            if (value == null) {
                return false;
            }
            for (ServiceType type : values()) {
                if (type.value.equalsIgnoreCase(value)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 获取所有服务类型
     */
    public static Set<String> getAllServiceTypes() {
        return ALL_SERVICE_TYPES;
    }

    /**
     * 获取服务类型列表
     */
    public static List<String> getServiceTypeList() {
        return SERVICE_TYPE_LIST;
    }

    /**
     * 检查是否是有效的服务类型
     */
    public static boolean isValidServiceType(final String serviceType) {
        return ServiceType.isValid(serviceType);
    }

    /**
     * 从字符串转换为服务类型
     */
    public static ServiceType toServiceType(final String serviceType) {
        return ServiceType.fromString(serviceType);
    }

    /**
     * 获取服务类型描述
     */
    public static String getDescription(final String serviceType) {
        ServiceType type = ServiceType.fromString(serviceType);
        return type != null ? type.getDescription() : "未知";
    }
}
