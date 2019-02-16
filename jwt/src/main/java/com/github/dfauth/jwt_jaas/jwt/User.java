package com.github.dfauth.jwt_jaas.jwt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class User {
    private String userId;
    private Set<Role> roles;

    User(String userId) {
        this(userId, Collections.emptySet());
    }

    User(String userId, Set<Role> roles) {
        this.userId = userId;
        this.roles = roles;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public String getUserId() {
        return userId;
    }

    public static User of(String userId, Role... roles) {
        return new User(userId, Stream.of(roles).collect(Collectors.toSet()));
    }
}
