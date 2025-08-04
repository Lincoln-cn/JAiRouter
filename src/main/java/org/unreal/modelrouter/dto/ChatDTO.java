package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 *  '{
 *     "model": "qwen3:4b",
 *     "messages": [
 *       {
 *         "role": "system",
 *         "content": "You are a helpful assistant."
 *       },
 *       {
 *         "role": "user",
 *         "content": "Hello!"
 *       }
 *     ],
 *     "stream": true
 *   }'
 */
public class ChatDTO {

    // 使用 Java Record 簡化 DTO 定義
    public record Request(
            String model,
            List<Message> messages,
            boolean stream,
            @JsonProperty("max_tokens")
            Integer maxTokens
    ) {
    }

    public record Message(
            String role,
            String content
    ) {
    }
}
