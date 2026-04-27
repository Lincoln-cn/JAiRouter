package org.unreal.modelrouter.adapter.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 响应映射器
 *
 * 负责处理响应数据的转换和映射，包括非流式和流式响应的处理。
 *
 * @author JAiRouter Team
 * @since v2.3.1.2
 */
@Component("adapterResponseMapper")
public class ResponseMapper {

    private static final Logger logger = LoggerFactory.getLogger(ResponseMapper.class);

    private final ObjectMapper objectMapper;

    public ResponseMapper(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 映射非流式响应
     *
     * @param clientResponse 客户端响应
     * @param targetType 目标类型
     * @param <T> 类型
     * @return 映射后的响应实体
     */
    public <T> Mono<ResponseEntity<T>> mapResponse(final ClientResponse clientResponse,final Class<T> targetType) {
        HttpStatus status = HttpStatus.valueOf(clientResponse.statusCode().value());

        logger.debug("映射响应：status={}, targetType={}", status, targetType.getSimpleName());

        // 处理错误响应
        if (status.is5xxServerError()) {
            return Mono.error(new ResponseStatusException(status, "服务器错误"));
        }
        if (status.is4xxClientError()) {
            return Mono.error(new ResponseStatusException(status, "客户端错误"));
        }

        // 处理成功响应
        return clientResponse.bodyToMono(targetType)
                .map(body -> ResponseEntity.status(status).body(body));
    }

    /**
     * 映射流式响应块
     *
     * @param dataBuffer 数据块
     * @return 映射后的字符串
     */
    public String mapStreamChunk(final DataBuffer dataBuffer) {
        byte[] bytes = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(bytes);
        DataBufferUtils.release(dataBuffer);

        String chunk = new String(bytes, StandardCharsets.UTF_8);
        logger.trace("映射流式块：length={}", chunk.length());

        return chunk;
    }

    /**
     * 处理响应错误
     *
     * @param clientResponse 客户端响应
     * @param instanceName 实例名称
     * @param path 请求路径
     * @return 错误 Mono
     */
    public Mono<Throwable> handleResponseError(
            final ClientResponse clientResponse,
            final String instanceName,
            final String path) {

        HttpStatus status = HttpStatus.valueOf(clientResponse.statusCode().value());

        return clientResponse.bodyToMono(String.class)
                .flatMap(errorBody -> {
                    if (status.value() == 401) {
                        logger.error("下游服务认证失败 (401): instance={}, path={}, response={}",
                                instanceName, path, status);
                    } else if (status.value() == 400) {
                        logger.error("下游服务请求错误 (400): instance={}, path={}, response={}",
                                instanceName, path, status);
                    } else if (status.value() == 503) {
                        logger.error("下游服务不可用 (503): instance={}, path={}, response={}",
                                instanceName, path, status);
                    } else {
                        logger.error("下游服务错误：instance={}, path={}, status={}",
                                instanceName, path, status);
                    }

                    return Mono.just((Throwable) new ResponseStatusException(status, errorBody));
                })
                .defaultIfEmpty(new ResponseStatusException(status, "未知错误"));
    }

    /**
     * 将 JSON 字符串转换为对象
     *
     * @param json JSON 字符串
     * @param targetType 目标类型
     * @param <T> 类型
     * @return 转换后的对象
     */
    public <T> T fromJson(final String json,final Class<T> targetType) {
        try {
            return objectMapper.readValue(json, targetType);
        } catch (JsonProcessingException e) {
            logger.error("JSON 解析失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 将对象转换为 JSON 字符串
     *
     * @param object 对象
     * @return JSON 字符串
     */
    public String toJson(final Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.error("JSON 序列化失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查响应状态
     *
     * @param clientResponse 客户端响应
     * @return 是否成功
     */
    public boolean isSuccess(final ClientResponse clientResponse) {
        return clientResponse.statusCode().is2xxSuccessful();
    }

    /**
     * 检查是否是服务器错误
     *
     * @param clientResponse 客户端响应
     * @return 是否是服务器错误
     */
    public boolean isServerError(final ClientResponse clientResponse) {
        return clientResponse.statusCode().is5xxServerError();
    }

    /**
     * 检查是否是客户端错误
     *
     * @param clientResponse 客户端响应
     * @return 是否是客户端错误
     */
    public boolean isClientError(final ClientResponse clientResponse) {
        return clientResponse.statusCode().is4xxClientError();
    }
}
