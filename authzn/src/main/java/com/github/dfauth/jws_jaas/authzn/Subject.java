package com.github.dfauth.jws_jaas.authzn;

import java.util.Set;

public interface Subject {
    Set<Principal> getPrincipals();
}
