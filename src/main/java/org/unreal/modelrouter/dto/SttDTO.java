package org.unreal.modelrouter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

public class SttDTO {

    public record Request(@RequestParam("model") String model,
                          @RequestParam("file") FilePart file,
                          @RequestParam(value = "language" , defaultValue = "auto") String language) {

    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Response(@JsonProperty("text") String text) {
    }
}
