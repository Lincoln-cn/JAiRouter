package org.unreal.modelrouter.exceptionhandler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.controller.response.RouterResponse; // 确保引入您项目中的Response类
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalControllerExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalControllerExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<RouterResponse<Void>> handleResponseStatusException(ResponseStatusException ex) {

        // 在这里，您可以确定地捕获到异常
        logger.error("通过 @RestControllerAdvice 捕获到响应状态异常: status={}, reason={}", ex.getStatusCode(), ex.getReason(), ex);

        RouterResponse<Void> errorResponse = RouterResponse.error(
                "请求处理失败: " + ex.getMessage(),
                String.valueOf(ex.getStatusCode().value())
        );

        return new ResponseEntity<>(errorResponse, ex.getStatusCode());
    }
}