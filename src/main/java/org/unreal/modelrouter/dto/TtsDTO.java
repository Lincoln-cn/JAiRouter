package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TtsDTO {

    public record Request(
            String model,
            String input,
            String voice,
            @JsonProperty("response_format") String responseFormat,
            Double speed
    ) {}

    // TTS typically returns binary audio data, so no specific response DTO needed
}