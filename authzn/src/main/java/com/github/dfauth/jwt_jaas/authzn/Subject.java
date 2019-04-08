package com.github.dfauth.jwt_jaas.authzn;

import java.util.Set;

public interface Subject {
    Set<Principal> getPrincipals();
}
