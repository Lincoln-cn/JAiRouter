package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class TtsDTO {

    /**
     * TTS 请求 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Request(
            @JsonProperty("model") String model,
            @JsonProperty("input") String input,
            @JsonProperty("voice") String voice,
            @JsonProperty("response_format") String responseFormat,
            @JsonProperty("speed") Double speed,
            @JsonProperty("language") String language,
            @JsonProperty("temperature") Double temperature,
            @JsonProperty("max_tokens") Integer maxTokens,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        // 提供默认值的构造方法
        public Request(String model, String input, String voice) {
            this(model, input, voice, "mp3", 1.0, null, null, null, null);
        }

        public Request(String model, String input) {
            this(model, input, "alloy", "mp3", 1.0, null, null, null, null);
        }

        // 校验方法
        public boolean isValid() {
            return model != null && !model.trim().isEmpty()
                    && input != null && !input.trim().isEmpty();
        }

        // 获取有效的语音类型
        public String getValidVoice() {
            if (voice == null || voice.trim().isEmpty()) {
                return "alloy"; // 默认语音
            }
            return voice;
        }

        // 获取有效的响应格式
        public String getValidResponseFormat() {
            if (responseFormat == null || responseFormat.trim().isEmpty()) {
                return "mp3"; // 默认格式
            }
            // 支持的格式：mp3, opus, aac, flac, wav, pcm
            String format = responseFormat.toLowerCase();
            if (List.of("mp3", "opus", "aac", "flac", "wav", "pcm").contains(format)) {
                return format;
            }
            return "mp3"; // 默认格式
        }

        // 获取有效的速度
        public double getValidSpeed() {
            if (speed == null || speed < 0.25 || speed > 4.0) {
                return 1.0; // 默认速度
            }
            return speed;
        }
    }

    /**
     * TTS 响应 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(
            @JsonProperty("audio") byte[] audio,
            @JsonProperty("content_type") String contentType,
            @JsonProperty("duration") Double duration,
            @JsonProperty("model") String model,
            @JsonProperty("voice") String voice,
            @JsonProperty("response_format") String responseFormat,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("created") Long created,
            @JsonProperty("id") String id
    ) {

        public Response(byte[] audio, String contentType, String model) {
            this(audio, contentType, null, model, null, null, null,
                    System.currentTimeMillis() / 1000, generateId());
        }

        private static String generateId() {
            return "tts-" + System.currentTimeMillis() + "-" +
                    Integer.toHexString((int)(Math.random() * 0xFFFF));
        }
    }
}