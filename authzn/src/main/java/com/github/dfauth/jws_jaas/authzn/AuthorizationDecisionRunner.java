package com.github.dfauth.jws_jaas.authzn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;


public class AuthorizationDecisionRunner implements AuthorizationDecision {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationDecisionRunner.class);
    private final AuthorizationDecision decision;
    private final Subject subject;
    private final Permission permission;

    public AuthorizationDecisionRunner(AuthorizationDecision decision, Subject subject, Permission permission) {
        this.decision = decision;
        this.subject = subject;
        this.permission = permission;
    }

    @Override
    public boolean isAllowed() {
        return decision.isAllowed();
    }

    @Override
    public boolean isDenied() {
        return decision.isDenied();
    }

    @Override
    public AuthorizationDecision or(AuthorizationDecision that) {
        return decision.or(that);
    }

    @Override
    public AuthorizationDecision and(AuthorizationDecision that) {
        return decision.and(that);
    }

    @Override
    public <R> R run(Callable<R> callable) throws SecurityException {
        try {
            return decision.run(callable);
        } catch(SecurityException e) {
            throw new SecurityException(subject+" is not authorized to perform actions "+permission.getActions()+" on resource "+permission.getResource());
        }
    }
}
