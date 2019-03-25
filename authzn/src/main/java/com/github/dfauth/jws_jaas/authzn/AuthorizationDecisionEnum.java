package com.github.dfauth.jws_jaas.authzn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public enum AuthorizationDecisionEnum implements AuthorizationDecision {

    ALLOW(new AllowRunner()),
    DENY(new DenyRunner());

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationDecisionEnum.class);

    private final Runner runner;

    AuthorizationDecisionEnum(Runner runner) {
        this.runner = runner;
    }

    public boolean isAllowed() {
        return this == ALLOW;
    }

    public boolean isDenied() {
        return this == DENY;
    }

    public <R> R run(Callable<R> callable) throws SecurityException {
        return this.runner.run(callable);
    }

    interface Runner {
        <R> R run(Callable<R> callable) throws SecurityException;
    }

    static class AllowRunner implements Runner {

        @Override
        public <R> R run(Callable<R> callable) throws SecurityException {
            try {
                return callable.call();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
    }

    static class DenyRunner implements Runner {

        @Override
        public <R> R run(Callable<R> callable) throws SecurityException {
            throw new SecurityException("Oops");
        }
    }
}
