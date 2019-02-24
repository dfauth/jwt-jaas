package com.github.dfauth.jwt_jaas.jwt;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class User {

    private String userId;
    private Set<Role> roles;
    private Instant expiry;

    User(String userId) {
        this(userId, Collections.emptySet(), defaultExpiry());
    }

    User(String userId, Set<Role> roles) {
        this(userId, roles, defaultExpiry());
    }

    User(String userId, Set<Role> roles, Instant expiry) {
        this.userId = userId;
        this.roles = roles;
        this.expiry = expiry;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getExpiry() {
        return expiry;
    }

    private static Instant defaultExpiry() {
        return Instant.now().plusSeconds(60*60);
    }

    public static User of(String userId, Role... roles) {
        return of(userId, defaultExpiry(), roles);
    }

    public static User of(String userId, Instant expiry, Role... roles) {
        return new User(userId, Stream.of(roles).collect(Collectors.toSet()), expiry);
    }

}
