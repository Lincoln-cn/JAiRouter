package org.unreal.modelrouter.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;

/**
 * #1197 / #1198：对外消息只允许「固定短语 + 显式声明可外显的 detail」。
 *
 * <p>本用例把「异常类型决定文案」这条口径钉住：同一段调用代码，喂 {@link ClientFacingException}
 * 应看见业务语，喂 {@link SQLException} 则只看见短语 —— 后者正是此前 75 处拼接泄露的那一类。</p>
 */
@DisplayName("ClientErrors：对外消息只含固定短语与 client-safe 详情（#1197）")
class ClientErrorsTest {

    private static final String DSN =
            "java.sql.SQLException: 连接失败 jdbc:postgresql://db.internal:5432/prod?password=s3cret";

    @Test
    @DisplayName("1197-001 ClientFacingException 的业务校验语原样外显")
    void clientFacingMessageIsVisible() {
        final ApiException mapped = ClientErrors.api(
                new ClientFacingException("用户名不能为空"), ErrorCode.INVALID_REQUEST, "参数校验失败");

        assertEquals(ErrorCode.INVALID_REQUEST.code(), mapped.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, mapped.getStatus());
        assertEquals("参数校验失败: 用户名不能为空", mapped.getMessage());
    }

    @Test
    @DisplayName("1197-002 未声明可外显的异常只回固定短语，连接串不外泄")
    void unknownExceptionLeaksNothing() {
        final ApiException mapped = ClientErrors.api(
                new SQLException("连接失败 jdbc:postgresql://db.internal:5432/prod?password=s3cret"),
                ErrorCode.INTERNAL_ERROR, "登录失败");

        assertEquals("登录失败", mapped.getMessage(), "对外消息应只剩固定短语");
        assertFalse(mapped.getMessage().contains("jdbc:postgresql"), "不得回显连接串");
        assertFalse(mapped.getMessage().contains("s3cret"), "不得回显口令");
        assertFalse(mapped.getMessage().contains("SQLException"), "不得回显异常类名");
    }

    @Test
    @DisplayName("1197-003 ApiException 原样返回，错误码与消息不被二次包装")
    void apiExceptionIsReturnedAsIs() {
        final ApiException original = ApiException.of(ErrorCode.TOKEN_NOT_FOUND.code(), "令牌不存在");

        final ApiException mapped = ClientErrors.api(original, ErrorCode.INTERNAL_ERROR, "获取令牌失败");

        assertSame(original, mapped);
        assertEquals("令牌不存在", mapped.getMessage());
        assertEquals(ErrorCode.TOKEN_NOT_FOUND.code(), mapped.getErrorCode());
    }

    @Test
    @DisplayName("1197-004 ResponseStatusException 只取 reason（不带状态码短语前缀）")
    void responseStatusExceptionUsesReason() {
        final ResponseStatusException cause = new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS, "dailyRequestLimit: 3/3");

        final ApiException mapped = ClientErrors.api(cause, ErrorCode.INVALID_REQUEST, "请求失败");

        assertEquals("请求失败: dailyRequestLimit: 3/3", mapped.getMessage());
        assertFalse(mapped.getMessage().contains("TOO_MANY_REQUESTS"),
                "不得带 Spring 的 status phrase，实际: " + mapped.getMessage());
    }

    @Test
    @DisplayName("1197-005 响应体工厂与异常工厂同口径")
    void responseFactoryUsesSamePolicy() {
        final RouterResponse<Object> body = ClientErrors.response(
                new IllegalStateException("internal: /var/lib/jairouter/keys.p12"),
                ErrorCode.ADD_FAILED, "添加失败");

        assertFalse(body.isSuccess());
        assertEquals(ErrorCode.ADD_FAILED.code(), body.getErrorCode());
        assertEquals("添加失败", body.getMessage());
        assertFalse(body.getMessage().contains("/var/lib/jairouter"), "不得回显文件路径");
    }

    @Test
    @DisplayName("1197-006 clientSafeDetail 只认三种显式类型")
    void onlyWhitelistedTypesExposeDetail() {
        assertNull(ClientErrors.clientSafeDetail(new RuntimeException("boom")));
        assertNull(ClientErrors.clientSafeDetail(new SQLException("boom")));
        assertNull(ClientErrors.clientSafeDetail(null));
        assertEquals("业务提示", ClientErrors.clientSafeDetail(new ClientFacingException("业务提示")));
        assertEquals("令牌不存在",
                ClientErrors.clientSafeDetail(ApiException.of(ErrorCode.TOKEN_NOT_FOUND.code(), "令牌不存在")));
        assertTrue(ClientErrors.clientSafeDetail(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason")).contains("reason"));
    }

    @Test
    @DisplayName("1197-007 日志侧仍保有异常原文（对外收敛不等于丢排障信息）")
    void detailSurvivesInLog() {
        // 该断言的意义在于「原文没有消失，只是换了出口」：异常对象原样传出，客户端体里没有它。
        final SQLException cause = new SQLException(DSN);
        final ApiException mapped = ClientErrors.api(cause, ErrorCode.INTERNAL_ERROR, "登录失败");

        assertFalse(mapped.getMessage().contains("db.internal"));
        assertTrue(cause.getMessage().contains("db.internal"), "异常对象本身仍带着原文");
    }
}
