package com.github.dfauth.jws_jaas.authzn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.Callable;

public enum AuthorizationDecision {

    ALLOW,
    DENY;

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationDecision.class);

    public boolean isAllowed() {
        return this == ALLOW;
    }

    public boolean isDenied() {
        return this == DENY;
    }

    public AuthorizationDecision compose(AuthorizationDecision that) {
        return isDenied() ? this : that;
    }

    public <R> R run(Callable<R> callable) throws SecurityException {
        try {
            if(isAllowed()) {
                return callable.call();
            } else {
                throw new SecurityException("Oops, not allowed");
            }
        } catch (SecurityException e) {
            logger.info(e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.info(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public static Optional<AuthorizationDecision> compose(Optional<AuthorizationDecision> o1, Optional<AuthorizationDecision> o2) {
        return o1.map(
                a1 -> o2.map(
                        a2 -> Optional.of(a1.compose(a2))
                ).orElse(o1)
        ).orElse(o2);
    }

}
