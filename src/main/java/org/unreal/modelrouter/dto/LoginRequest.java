package org.unreal.modelrouter.dto;

public class LoginRequest {
    private String username;
    private String password;

    public LoginRequest() {
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String toString() {
        return "LoginRequest(username=" + this.getUsername() + ", password=" + this.getPassword() + ")";
    }
}