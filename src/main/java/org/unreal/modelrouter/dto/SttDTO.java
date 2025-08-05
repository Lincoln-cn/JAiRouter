package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public class SttDTO {

    public record Request(
            FilePart file,
            String model,
            String language,
            String prompt,
            @JsonProperty("response_format") String responseFormat,
            Double temperature
    ) {}

    public record Response(
            String text,
            String language,
            Double duration,
            List<Segment> segments
    ) {}

    public record Segment(
            Integer id,
            Integer seek,
            Double start,
            Double end,
            String text,
            List<Integer> tokens,
            Double temperature,
            @JsonProperty("avg_logprob") Double avgLogprob,
            @JsonProperty("compression_ratio") Double compressionRatio,
            @JsonProperty("no_speech_prob") Double noSpeechProb
    ) {}
}