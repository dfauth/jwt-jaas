package com.github.dfauth.jwt_jaas.common;

import java.time.Instant;
import java.util.Set;

public interface UserToken {
    Set<Role> getRoles();

    String getUserId();

    Instant getExpiry();
}
