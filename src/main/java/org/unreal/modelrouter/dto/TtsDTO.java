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
            @JsonProperty("stream") Boolean stream,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        // 提供默认值的构造方法
        public Request(String model, String input, String voice) {
            this(model, input, voice, "mp3", 1.0, null, null, null, false, null);
        }

        public Request(String model, String input) {
            this(model, input, "alloy", "mp3", 1.0, null, null, null, false, null);
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

        // 是否为流式请求
        public boolean isStream() {
            return stream != null && stream;
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

    /**
     * TTS 流式响应块 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StreamChunk(
            @JsonProperty("audio_chunk") byte[] audioChunk,
            @JsonProperty("sequence") Integer sequence,
            @JsonProperty("is_final") Boolean isFinal,
            @JsonProperty("timestamp") Long timestamp,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        public StreamChunk(byte[] audioChunk, int sequence, boolean isFinal) {
            this(audioChunk, sequence, isFinal, System.currentTimeMillis(), null);
        }
    }

    /**
     * TTS 错误响应 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(
            @JsonProperty("error") Error error
    ) {

        public record Error(
                @JsonProperty("message") String message,
                @JsonProperty("type") String type,
                @JsonProperty("code") String code,
                @JsonProperty("param") String param
        ) {

            public Error(String message, String type, String code) {
                this(message, type, code, null);
            }
        }
    }

    /**
     * TTS 批量请求 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BatchRequest(
            @JsonProperty("model") String model,
            @JsonProperty("inputs") List<String> inputs,
            @JsonProperty("voice") String voice,
            @JsonProperty("response_format") String responseFormat,
            @JsonProperty("speed") Double speed,
            @JsonProperty("language") String language,
            @JsonProperty("batch_id") String batchId,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        public BatchRequest(String model, List<String> inputs, String voice) {
            this(model, inputs, voice, "mp3", 1.0, null, generateBatchId(), null);
        }

        private static String generateBatchId() {
            return "batch-tts-" + System.currentTimeMillis() + "-" +
                    Integer.toHexString((int)(Math.random() * 0xFFFF));
        }

        public boolean isValid() {
            return model != null && !model.trim().isEmpty()
                    && inputs != null && !inputs.isEmpty()
                    && inputs.stream().allMatch(input -> input != null && !input.trim().isEmpty());
        }
    }

    /**
     * TTS 批量响应 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BatchResponse(
            @JsonProperty("batch_id") String batchId,
            @JsonProperty("results") List<BatchResult> results,
            @JsonProperty("total_count") Integer totalCount,
            @JsonProperty("success_count") Integer successCount,
            @JsonProperty("error_count") Integer errorCount,
            @JsonProperty("created") Long created,
            @JsonProperty("metadata") Map<String, Object> metadata
    ) {

        public record BatchResult(
                @JsonProperty("index") Integer index,
                @JsonProperty("input") String input,
                @JsonProperty("audio") byte[] audio,
                @JsonProperty("success") Boolean success,
                @JsonProperty("error") Error error,
                @JsonProperty("duration") Double duration
        ) {

            public BatchResult(int index, String input, byte[] audio) {
                this(index, input, audio, true, null, null);
            }

            public BatchResult(int index, String input, Error error) {
                this(index, input, null, false, error, null);
            }
        }
    }

    /**
     * 语音配置 DTO
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VoiceConfig(
            @JsonProperty("voice_id") String voiceId,
            @JsonProperty("name") String name,
            @JsonProperty("language") String language,
            @JsonProperty("gender") String gender,
            @JsonProperty("age") String age,
            @JsonProperty("accent") String accent,
            @JsonProperty("description") String description,
            @JsonProperty("sample_rate") Integer sampleRate,
            @JsonProperty("available_formats") List<String> availableFormats
    ) {

        // 预定义的语音选项
        public static final VoiceConfig ALLOY = new VoiceConfig(
                "alloy", "Alloy", "en-US", "neutral", "adult", "american",
                "A balanced and versatile voice", 24000,
                List.of("mp3", "opus", "aac", "flac", "wav", "pcm")
        );

        public static final VoiceConfig ECHO = new VoiceConfig(
                "echo", "Echo", "en-US", "male", "adult", "american",
                "A clear and articulate male voice", 24000,
                List.of("mp3", "opus", "aac", "flac", "wav", "pcm")
        );

        public static final VoiceConfig FABLE = new VoiceConfig(
                "fable", "Fable", "en-US", "male", "adult", "british",
                "A warm and expressive male voice", 24000,
                List.of("mp3", "opus", "aac", "flac", "wav", "pcm")
        );

        public static final VoiceConfig ONYX = new VoiceConfig(
                "onyx", "Onyx", "en-US", "male", "adult", "american",
                "A deep and resonant male voice", 24000,
                List.of("mp3", "opus", "aac", "flac", "wav", "pcm")
        );

        public static final VoiceConfig NOVA = new VoiceConfig(
                "nova", "Nova", "en-US", "female", "young", "american",
                "A bright and energetic female voice", 24000,
                List.of("mp3", "opus", "aac", "flac", "wav", "pcm")
        );

        public static final VoiceConfig SHIMMER = new VoiceConfig(
                "shimmer", "Shimmer", "en-US", "female", "adult", "american",
                "A soft and gentle female voice", 24000,
                List.of("mp3", "opus", "aac", "flac", "wav", "pcm")
        );
    }
}