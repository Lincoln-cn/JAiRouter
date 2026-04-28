package org.unreal.modelrouter.common.dto;

import java.time.LocalDateTime;

public class JwtApiResponse {
    private boolean success;
    private String message;
    private LocalDateTime timestamp;

    public JwtApiResponse() {
    }

    public boolean isSuccess() {
        return this.success;
    }

    public void setSuccess(final boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(final LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String toString() {
        return "JwtApiResponse(success=" + this.isSuccess() + ", message=" + this.getMessage() + ", timestamp=" + this.getTimestamp() + ")";
    }
}
