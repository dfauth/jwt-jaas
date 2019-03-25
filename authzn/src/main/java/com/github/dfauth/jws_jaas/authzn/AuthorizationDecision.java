package com.github.dfauth.jws_jaas.authzn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.function.BinaryOperator;

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

    public static BinaryOperator<AuthorizationDecision> or = (AuthorizationDecision _this, AuthorizationDecision _that) ->  _this.or(_that);

    public static BinaryOperator<AuthorizationDecision> and = (AuthorizationDecision _this, AuthorizationDecision _that) ->  _this.and(_that);

    public AuthorizationDecision or(AuthorizationDecision that) {
        return isAllowed() ? ALLOW : that;
    }

    public AuthorizationDecision and(AuthorizationDecision that) {
        return isDenied() ? DENY : this;
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

}
