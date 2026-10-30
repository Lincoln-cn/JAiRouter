package org.unreal.modelrouter.common.exception;

/**
 * 上传文件超过体积上限（#862）.
 *
 * <p>由 {@code MultipartRequestHandler} 在读取 {@code FilePart} 时抛出。它会被 WebClient 包成
 * {@code WebClientRequestException}（写请求体阶段失败），因此错误映射必须沿 cause 链识别本类型，
 * 否则客户端会拿到 500 + 一段内部异常文本。</p>
 *
 * @since v3.5.8
 */
public class MultipartPayloadTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String filename;

    private final long actualBytes;

    private final long limitBytes;

    /**
     * 构造异常.
     *
     * @param filename    文件名
     * @param actualBytes 已读取的字节数（跨过上限时的累计值）
     * @param limitBytes  配置的上限
     * @param message     面向客户端的文案
     */
    public MultipartPayloadTooLargeException(final String filename, final long actualBytes,
                                             final long limitBytes, final String message) {
        super(message);
        this.filename = filename;
        this.actualBytes = actualBytes;
        this.limitBytes = limitBytes;
    }

    public String getFilename() {
        return filename;
    }

    public long getActualBytes() {
        return actualBytes;
    }

    public long getLimitBytes() {
        return limitBytes;
    }
}
