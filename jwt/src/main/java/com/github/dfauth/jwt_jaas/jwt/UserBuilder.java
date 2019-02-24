package com.github.dfauth.jwt_jaas.jwt;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;


public class UserBuilder {
    private String userId;
    private Set<RoleBuilder> roles;
    private Instant expiry;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Set<RoleBuilder> getRoles() {
        return roles;
    }

    public void setRoles(Set<RoleBuilder> roles) {
        this.roles = roles;
    }

    public User build() {
        return new User(userId, roles.stream().map(r -> r.build()).collect(Collectors.toSet()), expiry);
    }

    public UserBuilder withUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public UserBuilder withExpiry(Instant expiry) {
        this.expiry = expiry;
        return this;
    }

    public UserBuilder withRoles(Set<RoleBuilder> roles) {
        this.roles = roles;
        return this;
    }
}
