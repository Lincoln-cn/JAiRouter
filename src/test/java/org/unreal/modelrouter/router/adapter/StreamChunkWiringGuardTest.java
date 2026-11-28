package org.unreal.modelrouter.router.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式分块变换的接线守卫（issue #1242）。
 *
 * <p>缺陷形态：{@code ClaudeAdapter} / {@code GeminiAdapter} 各自实现了
 * {@code transformStreamChunk}，但 {@code BaseAdapter} 调用的是
 * {@code StreamingRequestProcessor} 的 8 参重载，该重载把 {@code transformChunkFn} 传 null，
 * 处理器于是永远走 {@code responseTransformer.transformStreamChunk}（identity）——
 * 子类的实现从未参与调用链，OpenAI 入站 + Anthropic/Gemini 上游时正文整段丢失。</p>
 *
 * <p>为什么用源码守卫而不是行为测试：这条缺陷的本质是「两段代码没有接上」，
 * 任何只测 {@code transformStreamChunk} 本身的单测在接线断开时依然会绿
 * （{@code ClaudeAdapterTest} / {@code GeminiAdapterTest} 里的转换用例就是这种形态）。
 * 直接调用又需要把 {@code WebClient}、下游 SSE 流、配额与记账全部桩起来，
 * 成本远高于这条断言的收益。所以这里读源码钉住三处接线点，
 * 与 {@code StartupSecretKeyCheckerSourceTest}、{@code SlaBootstrapTokenOptimisticLockGuardTest}
 * 的取舍一致。</p>
 */
@DisplayName("流式分块变换的接线（issue #1242）")
class StreamChunkWiringGuardTest {

    private static final Path BASE_ADAPTER =
            Path.of("src/main/java/org/unreal/modelrouter/router/adapter/BaseAdapter.java");
    private static final Path PROCESSOR = Path.of(
            "src/main/java/org/unreal/modelrouter/router/adapter/processor/StreamingRequestProcessor.java");

    private static String read(final Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("BaseAdapter 把适配器自己的分块变换交给处理器")
    void baseAdapterPassesItsOwnChunkTransformer() throws IOException {
        final String source = read(BASE_ADAPTER);
        assertTrue(source.contains("this::transformStreamChunk"),
                "BaseAdapter 必须把 this::transformStreamChunk 传给处理器，否则子类的 "
                        + "transformStreamChunk 永不参与调用链（issue #1242）");
    }

    @Test
    @DisplayName("处理器支持「该事件不下发」并在管道里滤掉")
    void processorHonoursNullChunks() throws IOException {
        final String source = read(PROCESSOR);
        assertTrue(source.contains("if (transformed == null)"),
                "transformAndWrapChunk 必须允许返回 null，否则无正文事件会 NPE 在 data() 上");
        assertTrue(source.contains(".filter(event -> event != droppedChunk())"),
                "map 之后必须滤掉丢弃哨兵，否则客户端收到无意义占位帧");
        assertTrue(source.contains("if (sse == droppedChunk())"),
                "调用点必须在命中哨兵时提前返回，跳过缓存与记账");
        assertTrue(source.contains("return droppedChunk();"),
                "Reactor 的 map 不允许返回 null，丢弃必须用哨兵表示");
    }

    @Test
    @DisplayName("守卫本身确实读到了源码（反空转）")
    void guardActuallyReadsSources() throws IOException {
        final String base = read(BASE_ADAPTER);
        final String processor = read(PROCESSOR);
        assertTrue(base.length() > 10_000, "BaseAdapter 源码过短，可能读错了文件：" + base.length());
        assertTrue(processor.length() > 30_000, "StreamingRequestProcessor 源码过短，可能读错了文件：" + processor.length());
        assertTrue(base.contains("class BaseAdapter"), "读到的文件不是 BaseAdapter");
        assertTrue(processor.contains("class StreamingRequestProcessor"), "读到的文件不是 StreamingRequestProcessor");
        // 两个不同的文件，避免 Path 配错导致两次读到同一份
        assertNotEquals(base, processor, "两个源码路径指向了同一份内容");
    }
}
