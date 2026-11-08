package org.unreal.modelrouter.config.core.helper;

import java.util.Locale;

import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

/**
 * 服务类型名称解析：全仓唯一实现。
 *
 * <p>服务类型在配置里可能以多种写法出现——枚举名（{@code imgGen}）、连字符小写
 * （{@code img-gen}，{@link ServiceTypeResolver#getServiceConfigKey} 的产物）、
 * 下划线或空格变体、以及历史别名（{@code chat-completion}、{@code text-to-speech}）。
 * 写入侧与读取侧必须用同一套判定，否则服务配置写进缓存后永远读不到。</p>
 *
 * <p><b>#905 的残留缺陷与修法</b>：此前三处各自维护了一份别名表
 * （{@code ServiceTypeResolver#mapServiceTypeAlias}、
 * {@code ConfigurationValidator#isValidServiceTypeAlias}、
 * {@code ConfigValidator#isValidServiceTypeAlias}），三份都用
 * {@code serviceKey.toLowerCase()} 的**未去连字符**结果去比 {@code ServiceTypeConstants.IMG_GEN}
 * 这类**驼峰**常量，比较恒假；再加上 {@code ServiceType.valueOf(normalizedKey)} 用归一化后的
 * {@code imggen} 去查驼峰枚举值必然抛异常，于是 {@code img-gen} / {@code imgEdit} / {@code img-edit}
 * 全部解析为 {@code null}。后果是 {@code imgEdit} 的缓存键停留在未规范化的 {@code imgEdit}，
 * 而查询侧用 {@code img-edit} 去找，图像编辑端点 100% 返回
 * {@code 404 No instances found ... in service type 'imgEdit'}；{@code imgGen} 只因旧别名表里
 * 硬编码了 {@code "imggen"} 才侥幸能路由，但仍被 {@code getAllInstances()} 等处以
 * {@code null} 过滤掉，导致永不参与健康检查与统计。</p>
 *
 * <p>因此这里改为「枚举名小写比较 + 归一化键匹配」，并把三份副本收敛到这一处。
 * 新增服务类型时只需在 {@link ServiceType} 里加枚举值，本类无需改动。</p>
 *
 * @author JAiRouter Team
 * @since v3.8.1
 */
public final class ServiceTypeNames {

    private ServiceTypeNames() {
        // 工具类，禁止实例化
    }

    /**
     * 归一化服务键：转小写并移除空格、下划线、连字符。
     *
     * @param serviceKey 原始服务键，可为 {@code null}
     * @return 归一化后的键；入参为 {@code null} 时返回 {@code null}
     */
    public static String normalize(final String serviceKey) {
        if (serviceKey == null) {
            return null;
        }
        return serviceKey.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    }

    /**
     * 解析服务类型。
     *
     * @param serviceKey 服务键，支持枚举名、连字符/下划线/空格写法与历史别名
     * @return 服务类型枚举；无法识别时返回 {@code null}（不猜、不回落默认值）
     */
    public static ServiceType resolve(final String serviceKey) {
        if (serviceKey == null) {
            return null;
        }
        final String trimmed = serviceKey.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // 1) 枚举名大小写不敏感精确匹配：imgGen / IMGGEN / imgEdit 等
        for (ServiceType type : ServiceType.values()) {
            if (type.name().equalsIgnoreCase(trimmed)) {
                return type;
            }
        }

        // 2) 归一化键匹配：img-gen / img_edit / "image edit" 等
        return resolveAlias(normalize(trimmed));
    }

    /**
     * 判断服务键是否可识别为已知服务类型。
     *
     * @param serviceKey 服务键
     * @return {@code true} 表示可识别
     */
    public static boolean isValid(final String serviceKey) {
        return resolve(serviceKey) != null;
    }

    private static ServiceType resolveAlias(final String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        switch (normalized) {
            case "chat":
            case "chatcompletion":
            case "chatcompletions":
                return ServiceType.chat;

            case "embedding":
            case "embeddings":
                return ServiceType.embedding;

            case "rerank":
                return ServiceType.rerank;

            case "tts":
            case "texttospeech":
                return ServiceType.tts;

            case "stt":
            case "speechtotext":
                return ServiceType.stt;

            case "imggen":
            case "imagegeneration":
            case "imagegenerate":
                return ServiceType.imgGen;

            case "imgedit":
            case "imageedit":
            case "imageediting":
                return ServiceType.imgEdit;

            default:
                return null;
        }
    }
}
