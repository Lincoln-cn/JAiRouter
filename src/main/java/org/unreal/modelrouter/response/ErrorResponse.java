package org.unreal.modelrouter.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * "{\"message\": \"%s\", \"type\": \"internal_error\", \"code\": \"error\"}"
 */
public class ErrorResponse {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ErrorResponse.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String message;
    private String type = "internal_error";
    private String code = "error";

    private ErrorResponse(Builder builder) {
        this.message = builder.message;
        this.type = builder.type;
        this.code = builder.code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            log.error("Error converting ErrorResponse to JSON: {}", e.getMessage());
        }
        return "";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String message;
        private String type = "";
        private String code = "error";

        private Builder() {
        }

        public Builder message(String message) {
            if (message == null) {
                throw new IllegalArgumentException("Message cannot be null");
            }
            this.message = message;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public ErrorResponse build() {
            if (this.message == null) {
                throw new IllegalStateException("Message must be set before building ErrorResponse");
            }
            return new ErrorResponse(this);
        }
    }
}
