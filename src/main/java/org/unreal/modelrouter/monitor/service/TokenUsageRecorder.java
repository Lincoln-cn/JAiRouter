package org.unreal.modelrouter.monitor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.monitor.dto.TokenUsageRecordDTO;
import org.unreal.modelrouter.auth.security.model.JwtAuthentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

/**
 * Token 使用量记录服务
 * 用于在请求处理完成后异步记录 Token 使用量
 *
 * @author JAiRouter Team
 * @since 1.9.5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenUsageRecorder {

    private final TokenUsageService tokenUsageService;

    /**
     * 异步记录 Token 使用量
     */
    public void recordTokenUsageAsync(
            final String serviceType,
            final String modelName,
            final String provider,
            final String instanceName,
            final String instanceUrl,
            final Long promptTokens,
            final Long completionTokens,
            final Long totalTokens,
            final String traceId,
            final Boolean isSuccess,
            final String errorCode,
            final String errorMessage,
            final Long responseTimeMs) {

        // 异步获取当前用户信息并记录
        ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(auth -> auth instanceof JwtAuthentication)
            .map(auth -> (JwtAuthentication) auth)
            .map(JwtAuthentication::getPrincipal)
            .map(principal -> principal != null ? principal.toString() : null)
            .defaultIfEmpty(null)
            .publishOn(Schedulers.boundedElastic())
            .subscribe(
                userId -> doRecordTokenUsage(
                    serviceType, modelName, provider, instanceName, instanceUrl,
                    promptTokens, completionTokens, totalTokens, traceId,
                    userId, isSuccess, errorCode, errorMessage, responseTimeMs
                ),
                error -> log.error("获取用户信息失败", error)
            );
    }

    /**
     * 执行 Token 使用量记录（无用户认证）
     */
    public void recordTokenUsageNoAuth(
            final String serviceType,
            final String modelName,
            final String provider,
            final String instanceName,
            final String instanceUrl,
            final Long promptTokens,
            final Long completionTokens,
            final Long totalTokens,
            final String traceId,
            final String clientIp,
            final Boolean isSuccess,
            final String errorCode,
            final String errorMessage,
            final Long responseTimeMs) {

        doRecordTokenUsageWithClientIp(
            serviceType, modelName, provider, instanceName, instanceUrl,
            promptTokens, completionTokens, totalTokens, traceId,
            clientIp, null, isSuccess, errorCode, errorMessage, responseTimeMs
        );
    }

    /**
     * 执行 Token 使用量记录
     */
    private void doRecordTokenUsage(
            final String serviceType,
            final String modelName,
            final String provider,
            final String instanceName,
            final String instanceUrl,
            final Long promptTokens,
            final Long completionTokens,
            final Long totalTokens,
            final String traceId,
            final String userId,
            final Boolean isSuccess,
            final String errorCode,
            final String errorMessage,
            final Long responseTimeMs) {

        try {
            TokenUsageRecordDTO record = TokenUsageRecordDTO.builder()
                    .serviceType(serviceType)
                    .modelName(modelName)
                    .provider(provider)
                    .instanceName(instanceName)
                    .instanceUrl(instanceUrl)
                    .promptTokens(promptTokens != null ? promptTokens : 0L)
                    .completionTokens(completionTokens != null ? completionTokens : 0L)
                    .totalTokens(totalTokens != null ? totalTokens : 0L)
                    .traceId(traceId)
                    .userId(userId)
                    .isSuccess(isSuccess)
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .responseTimeMs(responseTimeMs)
                    .occurredAt(LocalDateTime.now())
                    .build();

            tokenUsageService.recordTokenUsage(record);
            log.debug("Token usage recorded: model={}, totalTokens={}, serviceType={}, userId={}",
                    modelName, totalTokens, serviceType, userId);
        } catch (Exception e) {
            log.error("Failed to record token usage for model: {}", modelName, e);
        }
    }

    /**
     * 执行 Token 使用量记录（带客户端 IP）
     */
    private void doRecordTokenUsageWithClientIp(
            final String serviceType,
            final String modelName,
            final String provider,
            final String instanceName,
            final String instanceUrl,
            final Long promptTokens,
            final Long completionTokens,
            final Long totalTokens,
            final String traceId,
            final String clientIp,
            final String userId,
            final Boolean isSuccess,
            final String errorCode,
            final String errorMessage,
            final Long responseTimeMs) {

        try {
            TokenUsageRecordDTO record = TokenUsageRecordDTO.builder()
                    .serviceType(serviceType)
                    .modelName(modelName)
                    .provider(provider)
                    .instanceName(instanceName)
                    .instanceUrl(instanceUrl)
                    .promptTokens(promptTokens != null ? promptTokens : 0L)
                    .completionTokens(completionTokens != null ? completionTokens : 0L)
                    .totalTokens(totalTokens != null ? totalTokens : 0L)
                    .traceId(traceId)
                    .clientIp(clientIp)
                    .userId(userId)
                    .isSuccess(isSuccess)
                    .errorCode(errorCode)
                    .errorMessage(errorMessage)
                    .responseTimeMs(responseTimeMs)
                    .occurredAt(LocalDateTime.now())
                    .build();

            tokenUsageService.recordTokenUsage(record);
            log.debug("Token usage recorded: model={}, totalTokens={}, serviceType={}, clientIp={}",
                    modelName, totalTokens, serviceType, clientIp);
        } catch (Exception e) {
            log.error("Failed to record token usage for model: {}", modelName, e);
        }
    }
}
