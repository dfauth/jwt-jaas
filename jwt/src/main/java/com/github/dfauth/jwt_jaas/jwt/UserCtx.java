package com.github.dfauth.jwt_jaas.jwt;

public class UserCtx {

    private String token;
    private User user;

    public UserCtx(String token, User user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public User getUser() {
        return user;
    }
}
