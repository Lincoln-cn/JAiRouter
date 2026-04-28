package org.unreal.modelrouter.common.dto;

public class LoginRequest {
    private String username;
    private String password;

    public LoginRequest() {
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public String toString() {
        return "LoginRequest(username=" + this.getUsername() + ", password=" + this.getPassword() + ")";
    }
}