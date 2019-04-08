package com.github.dfauth.jwt_jaas.authzn;

public interface Principal extends java.security.Principal {
    PrincipalType getPrincipalType();
    String getSource();
    String getName();
}