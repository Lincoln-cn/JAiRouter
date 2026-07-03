/*
 * Copyright (c) 2025 JAiRouter Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.unreal.modelrouter.router.adapter.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.router.fallback.FallbackStrategy;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

/**
 * 降级请求处理器 - v2.15.4
 *
 * 从BaseAdapter提取的降级逻辑，负责：
 * 1. 执行降级策略
 * 2. 处理降级异常
 * 3. 构建降级响应
 *
 * 设计模式：Strategy Pattern（策略模式）
 * 注意：不依赖BaseAdapter，避免循环依赖
 *
 * @author JAiRouter Team
 * @since v2.15.4
 */
@Service
public class FallbackRequestProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FallbackRequestProcessor.class);

    @Autowired(required = false)
    private ErrorTracker errorTracker;

    /**
     * 处理降级错误
     *
     * @param throwable 原始异常
     * @param fallbackStrategy 降级策略
     * @return 降级响应或错误
     */
    public Mono<? extends ResponseEntity<?>> handleFallbackError(
            final Throwable throwable,
            final FallbackStrategy<ResponseEntity<?>> fallbackStrategy) {

        try {
            logger.debug("执行降级策略: strategy={}", fallbackStrategy.getClass().getSimpleName());

            ResponseEntity<?> fallbackResponse = fallbackStrategy.fallback((Exception) throwable);

            if (fallbackResponse == null || fallbackResponse.getBody() == null) {
                logger.warn("降级响应为空");
                return handleNullFallback(throwable);
            }

            if (!fallbackResponse.getStatusCode().is2xxSuccessful()) {
                logger.warn("降级响应非成功状态: {}", fallbackResponse.getStatusCode());
                return handleNonSuccessfulFallback(throwable, fallbackResponse);
            }

            logger.info("降级成功: statusCode={}, hasBody={}",
                    fallbackResponse.getStatusCode(), fallbackResponse.getBody() != null);
            if (errorTracker != null) {
                errorTracker.trackError(throwable, "fallback.success");
            }
            return Mono.just(fallbackResponse);

        } catch (Exception e) {
            logger.error("降级处理异常: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }

    /**
     * 处理空降级响应
     */
    public Mono<? extends ResponseEntity<?>> handleNullFallback(final Throwable throwable) {
        if (throwable instanceof DownstreamServiceException) {
            DownstreamServiceException downStreamEx = (DownstreamServiceException) throwable;
            logger.debug("空降级响应，返回原始下游异常: statusCode={}", downStreamEx.getStatusCode());
            return Mono.error(new ResponseStatusException(
                    downStreamEx.getStatusCode(),
                    downStreamEx.getMessage(),
                    downStreamEx));
        } else if (throwable instanceof ResponseStatusException) {
            logger.debug("空降级响应，返回原始响应状态异常");
            return Mono.error((ResponseStatusException) throwable);
        } else {
            logger.debug("空降级响应，返回服务不可用");
            return Mono.error(new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "服务降级且无缓存"));
        }
    }

    /**
     * 处理非成功降级响应
     */
    public Mono<? extends ResponseEntity<?>> handleNonSuccessfulFallback(
            final Throwable throwable,
            final ResponseEntity<?> fallbackResponse) {

        if (throwable instanceof DownstreamServiceException) {
            DownstreamServiceException downStreamEx = (DownstreamServiceException) throwable;
            logger.debug("非成功降级响应，返回原始下游异常: statusCode={}", downStreamEx.getStatusCode());
            return Mono.error(new ResponseStatusException(
                    downStreamEx.getStatusCode(),
                    downStreamEx.getMessage(),
                    downStreamEx));
        } else if (throwable instanceof ResponseStatusException) {
            logger.debug("非成功降级响应，返回原始响应状态异常");
            return Mono.error((ResponseStatusException) throwable);
        } else {
            String message = fallbackResponse.getBody() != null
                    ? fallbackResponse.getBody().toString() : "未知错误";
            logger.debug("非成功降级响应，返回降级状态: statusCode={}", fallbackResponse.getStatusCode());
            return Mono.error(new ResponseStatusException(
                    fallbackResponse.getStatusCode(), message));
        }
    }

    /**
     * 判断是否需要降级
     *
     * @param fallbackStrategy 降级策略
     * @return 是否启用降级
     */
    public boolean isFallbackEnabled(final FallbackStrategy<ResponseEntity<?>> fallbackStrategy) {
        return fallbackStrategy != null;
    }
}