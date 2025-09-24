package org.unreal.modelrouter.dto;

import java.util.List;
import java.util.Objects;

public class BatchTokenRevokeRequest {
    private List<String> tokens;

    private String reason; // 可选，撤销原因

    public BatchTokenRevokeRequest() {
    }

    public List<String> getTokens() {
        return this.tokens;
    }

    public void setTokens(List<String> tokens) {
        this.tokens = tokens;
    }

    public String getReason() {
        return this.reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof BatchTokenRevokeRequest other)) return false;
        if (!other.canEqual(this)) return false;
        final Object this$tokens = this.getTokens();
        final Object other$tokens = other.getTokens();
        if (!Objects.equals(this$tokens, other$tokens)) return false;
        final Object this$reason = this.getReason();
        final Object other$reason = other.getReason();
        return Objects.equals(this$reason, other$reason);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof BatchTokenRevokeRequest;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $tokens = this.getTokens();
        result = result * PRIME + ($tokens == null ? 43 : $tokens.hashCode());
        final Object $reason = this.getReason();
        result = result * PRIME + ($reason == null ? 43 : $reason.hashCode());
        return result;
    }

    public String toString() {
        return "BatchTokenRevokeRequest(tokens=" + this.getTokens() + ", reason=" + this.getReason() + ")";
    }
}
