package com.github.dfauth.jws_jaas.authzn;

import java.util.concurrent.Callable;
import java.util.function.BinaryOperator;

public interface AuthorizationDecision {

    boolean isAllowed();

    boolean isDenied();

    BinaryOperator<AuthorizationDecision> or = (AuthorizationDecision _this, AuthorizationDecision _that) ->  _this.or(_that);

    BinaryOperator<AuthorizationDecision> and = (AuthorizationDecision _this, AuthorizationDecision _that) ->  _this.and(_that);

    default AuthorizationDecision or(AuthorizationDecision that) {
        return isAllowed() ? this : that;
    }

    default AuthorizationDecision and(AuthorizationDecision that) {
        return isDenied() ? that : this;
    }

    <R> R run(Callable<R> callable) throws SecurityException;

}
