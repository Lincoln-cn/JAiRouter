package org.unreal.modelrouter.config.core.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.unreal.modelrouter.config.core.ConfigurationValidator;
import org.unreal.modelrouter.config.core.manager.ConfigValidator;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 服务类型名称解析测试。
 *
 * <p>守护的是一类曾被复制成三份、且三份同时失效的判定：配置里的服务键可能是驼峰
 * （{@code imgGen} / {@code imgEdit}）、连字符小写（{@code img-gen} / {@code img-edit}，
 * 即 {@link ServiceTypeResolver#getServiceConfigKey} 的产物）、下划线或历史别名。
 * 旧实现用未去连字符的小写串去比驼峰常量，比较恒假，导致
 * {@code img-edit} / {@code imgEdit} 解析为 {@code null}——缓存键因此停在
 * {@code imgEdit}，而查询侧用 {@code img-edit} 去找，图像编辑端点 100% 返回 404。</p>
 *
 * <p>最要紧的一条是 {@link RoundTrip}：对<b>每一个</b>枚举值断言
 * {@code resolve(getServiceConfigKey(type)) == type}。将来新增服务类型时，
 * 只要忘了在别名表里登记，这条会立刻红，而不是等到线上 404 才被发现。</p>
 *
 * @author JAiRouter Team
 * @since v3.8.1
 */
@DisplayName("服务类型名称解析")
class ServiceTypeNamesTest {

    private final ServiceTypeResolver resolver = new ServiceTypeResolver();

    @Nested
    @DisplayName("往返不变量")
    class RoundTrip {

        @Test
        @DisplayName("每个枚举值都应该能从 getServiceConfigKey 的产物解析回来")
        void everyEnumRoundTripsThroughConfigKey() {
            for (ServiceType type : ServiceType.values()) {
                final String configKey = resolver.getServiceConfigKey(type);
                assertEquals(type, ServiceTypeNames.resolve(configKey),
                        "getServiceConfigKey(" + type + ") = " + configKey + " 无法解析回原枚举值");
                assertEquals(type, resolver.parseServiceType(type.name()),
                        "枚举名 " + type.name() + " 无法解析回原枚举值");
            }
        }

        @Test
        @DisplayName("getServiceConfigKey 对图像类型产出连字符小写形式")
        void configKeyIsHyphenLowerCaseForImageTypes() {
            assertEquals("img-gen", resolver.getServiceConfigKey(ServiceType.imgGen));
            assertEquals("img-edit", resolver.getServiceConfigKey(ServiceType.imgEdit));
        }
    }

    @Nested
    @DisplayName("被复制两份的旧缺陷（回归）")
    class ImageTypeRegression {

        @ParameterizedTest(name = "imgGen 的写法 {0} 应解析为 imgGen")
        @ValueSource(strings = {"imgGen", "imggen", "IMGGEN", "img-gen", "img_gen", "image-generation", "image-generate"})
        void imgGenVariantsResolve(final String key) {
            assertEquals(ServiceType.imgGen, ServiceTypeNames.resolve(key));
        }

        @ParameterizedTest(name = "imgEdit 的写法 {0} 应解析为 imgEdit")
        @ValueSource(strings = {"imgEdit", "imgedit", "IMGEDIT", "img-edit", "img_edit", "image-edit", "image-editing"})
        void imgEditVariantsResolve(final String key) {
            assertEquals(ServiceType.imgEdit, ServiceTypeNames.resolve(key));
        }

        @Test
        @DisplayName("两个校验器与解析器的判定必须一致（三处副本收敛后的守护）")
        void validatorsAgreeWithResolver() {
            final ConfigurationValidator configurationValidator = new ConfigurationValidator();
            final ConfigValidator configValidator = new ConfigValidator();

            for (ServiceType type : ServiceType.values()) {
                for (String variant : new String[] {type.name(), resolver.getServiceConfigKey(type)}) {
                    assertTrue(configurationValidator.isValidServiceType(variant),
                            "ConfigurationValidator 不认 " + variant);
                    assertTrue(configValidator.isValidServiceType(variant),
                            "ConfigValidator 不认 " + variant);
                    assertTrue(resolver.isValidServiceType(variant),
                            "ServiceTypeResolver 不认 " + variant);
                }
            }
        }
    }

    @Nested
    @DisplayName("其它服务类型与别名")
    class OtherTypes {

        @ParameterizedTest(name = "{0} → chat")
        @ValueSource(strings = {"chat", "CHAT", "chat-completion", "chat-completions"})
        void chatVariants(final String key) {
            assertEquals(ServiceType.chat, ServiceTypeNames.resolve(key));
        }

        @ParameterizedTest(name = "{0} → embedding")
        @ValueSource(strings = {"embedding", "Embedding", "embeddings", "EMBEDDING"})
        void embeddingVariants(final String key) {
            assertEquals(ServiceType.embedding, ServiceTypeNames.resolve(key));
        }

        @ParameterizedTest(name = "{0} → rerank")
        @ValueSource(strings = {"rerank", "re-rank", "RERANK"})
        void rerankVariants(final String key) {
            assertEquals(ServiceType.rerank, ServiceTypeNames.resolve(key));
        }

        @ParameterizedTest(name = "{0} → tts")
        @ValueSource(strings = {"tts", "TTS", "text-to-speech"})
        void ttsVariants(final String key) {
            assertEquals(ServiceType.tts, ServiceTypeNames.resolve(key));
        }

        @ParameterizedTest(name = "{0} → stt")
        @ValueSource(strings = {"stt", "STT", "speech-to-text"})
        void sttVariants(final String key) {
            assertEquals(ServiceType.stt, ServiceTypeNames.resolve(key));
        }
    }

    @Nested
    @DisplayName("无法识别的输入")
    class UnknownInput {

        @ParameterizedTest(name = "\"{0}\" 应解析为 null")
        @ValueSource(strings = {"", "   ", "gpustack", "invalid-type", "img", "image", "chatty"})
        void unknownReturnsNull(final String key) {
            assertNull(ServiceTypeNames.resolve(key));
            assertFalse(ServiceTypeNames.isValid(key));
        }

        @Test
        @DisplayName("null 输入返回 null 而不是抛异常")
        void nullReturnsNull() {
            assertNull(ServiceTypeNames.resolve(null));
            assertNull(ServiceTypeNames.normalize(null));
            assertFalse(ServiceTypeNames.isValid(null));
        }

        @Test
        @DisplayName("解析器对 null 与空白不抛异常")
        void resolverToleratesBlank() {
            assertNull(resolver.parseServiceType(null));
            assertNull(resolver.parseServiceType("  "));
        }
    }

    @Nested
    @DisplayName("归一化")
    class Normalize {

        @Test
        @DisplayName("移除空格、下划线、连字符并转小写")
        void stripsSeparators() {
            assertEquals("imgedit", ServiceTypeNames.normalize("img-edit"));
            assertEquals("imgedit", ServiceTypeNames.normalize("IMG_EDIT"));
            // 归一化只去分隔符，不做语义映射：image-edit 归一化后是 imageedit，
            // 由别名表（resolveAlias）负责映射到 imgEdit
            assertEquals("imageedit", ServiceTypeNames.normalize("Image Edit"));
            assertEquals("chatcompletion", ServiceTypeNames.normalize("chat-completion"));
        }
    }
}
