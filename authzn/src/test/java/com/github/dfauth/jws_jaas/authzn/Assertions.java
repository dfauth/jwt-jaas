package com.github.dfauth.jws_jaas.authzn;

import org.testng.asserts.Assertion;

import static com.github.dfauth.jws_jaas.authzn.Assertions.WasRunAssertion.State.NOT_RUN;
import static com.github.dfauth.jws_jaas.authzn.Assertions.WasRunAssertion.State.WAS_RUN;
import static org.testng.Assert.assertTrue;

public class Assertions {

    public static void assertAllowed(AuthorizationDecision decision) {
        assertTrue(decision.isAllowed());
    }

    public static void assertDenied(AuthorizationDecision decision) {
        assertTrue(decision.isDenied());
    }

    public static void assertWasRun(WasRunAssertion a) {
        assertTrue(a.wasRun());
    }

    static class WasRunAssertion {
        private State state = NOT_RUN;
        public WasRunAssertion run() {
            state = WAS_RUN;
            return this;
        }

        public State state() {
            return state;
        }

        public boolean wasRun() {
            return state.wasRun();
        }

        static enum State {
            NOT_RUN, WAS_RUN;

            public boolean wasRun() {
                return this == WAS_RUN;
            }
        }
    }
}
