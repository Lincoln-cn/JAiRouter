package org.unreal.modelrouter.exceptionhandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.unreal.modelrouter.controller.response.RouterResponse;

@RestControllerAdvice
public class ServerExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServerExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public RouterResponse<Void> handleException(Exception e) {
        logger.error("系统异常", e);
        return RouterResponse.error("系统异常:%s".formatted(e.getMessage()), "500");
    }
}
