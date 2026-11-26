package org.unreal.modelrouter.common.exception;

/**
 * 标记异常：其消息已人工确认可以原样返回给调用方（issue #1197 / #1198）。
 *
 * <p><b>为什么需要它：</b>控制器里的 catch-all 分支过去一律写成
 * {@code RouterResponse.error("xxx失败: " + ex.getMessage(), code)}。里面的 {@code ex} 可能是
 * 任何一个框架异常 —— 数据库连接串、文件路径、JSON 解析位置、JWT 校验细节都会被拼进客户端可见的
 * 响应体（CodeQL {@code java/error-message-exposure}，见 #92 / #93 / #1193）。</p>
 *
 * <p>但「全部不外显」也不对：像「API 密钥名称不能为空」这类业务校验语，本来就是写给调用方看的。
 * 于是把口径收敛成一条规则 —— <b>只有显式声明为 client-facing 的异常才允许外显</b>：</p>
 *
 * <pre>
 *   // 业务校验：抛 ClientFacingException，消息会原样到达调用方
 *   throw new ClientFacingException("采样率必须在 0~1 之间");
 *
 *   // 第三方 / 框架异常：被 ClientErrors 收敛成固定文案，原文只进日志
 *   .onErrorResume(e -&gt; Mono.error(ClientErrors.api(e, ErrorCode.LOGIN_FAILED, "登录失败")))
 * </pre>
 *
 * <p><b>继承它的类应当是业务语义明确的校验异常</b>（如参数合法性、状态前置条件），不要用它包装
 * 框架异常 —— 那样等于把原来那处拼接搬了个地方。</p>
 *
 * <p>它同时被 {@link ApiExceptions#wrap} 与
 * {@link org.unreal.modelrouter.common.exceptionhandler.ReactiveGlobalExceptionHandler} 识别，
 * 因此无论是控制器内联捕获还是冒泡到中央处理器，口径一致。</p>
 *
 * @author JAiRouter Team
 * @since 3.9.0
 */
public class ClientFacingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 可选的对外业务错误码；为空时由调用点决定（通常就近用 {@code ErrorCode.INVALID_REQUEST}）。 */
    private final String errorCode;

    /**
     * 构造 client-facing 异常（不带错误码）。
     *
     * @param message 可原样返回给调用方的消息
     */
    public ClientFacingException(final String message) {
        this(null, message);
    }

    /**
     * 构造 client-facing 异常（带错误码）。
     *
     * @param errorCode 对外的业务错误码，可为空
     * @param message   可原样返回给调用方的消息
     */
    public ClientFacingException(final String errorCode, final String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造 client-facing 异常（保留原因链，便于日志定位）。
     *
     * @param errorCode 对外的业务错误码，可为空
     * @param message   可原样返回给调用方的消息
     * @param cause     根因（不会外显，只进日志）
     */
    public ClientFacingException(final String errorCode, final String message, final Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * 对外的业务错误码。
     *
     * @return 错误码，可能为 {@code null}
     */
    public String getErrorCode() {
        return errorCode;
    }
}
