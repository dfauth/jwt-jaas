package com.github.dfauth.jwt_jaas.authzn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import static com.github.dfauth.jwt_jaas.authzn.Assertions.*;
import static com.github.dfauth.jwt_jaas.authzn.Assertions.assertAllowed;
import static com.github.dfauth.jwt_jaas.authzn.Assertions.assertDenied;
import static com.github.dfauth.jwt_jaas.authzn.AuthorizationDecision.and;
import static com.github.dfauth.jwt_jaas.authzn.AuthorizationDecision.or;
import static com.github.dfauth.jwt_jaas.authzn.AuthorizationDecisionEnum.ALLOW;
import static com.github.dfauth.jwt_jaas.authzn.AuthorizationDecisionEnum.DENY;
import static org.testng.Assert.fail;

public class AuthorizationDecisionTest {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationDecisionTest.class);

    @Test
    public void testCompositionOfAuthorizationDecisions() {
        assertDenied(or.apply(DENY, DENY));
        assertAllowed(or.apply(DENY, ALLOW));
        assertAllowed(or.apply(ALLOW, DENY));
        assertAllowed(or.apply(ALLOW, ALLOW));

        assertDenied(and.apply(DENY, DENY));
        assertDenied(and.apply(DENY, ALLOW));
        assertDenied(and.apply(ALLOW, DENY));
        assertAllowed(and.apply(ALLOW, ALLOW));
    }

    @Test
    public void testRunningOfAuthorizationDecisions() {
        try {
            WasRunAssertion a = DENY.run(() -> new WasRunAssertion().run());
            fail("was expecting a SecurityException");
        } catch (SecurityException e) {
            // expected
        }
        try {
            WasRunAssertion a = ALLOW.run(() -> new WasRunAssertion().run());
            Assertions.assertWasRun(a);
        } catch (SecurityException e) {
            fail(e.getMessage(), e);
        }
    }

}
