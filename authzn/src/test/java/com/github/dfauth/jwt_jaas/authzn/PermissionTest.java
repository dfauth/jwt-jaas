package com.github.dfauth.jwt_jaas.authzn;

import com.github.dfauth.jwt_jaas.authzn.Assertions.WasRunAssertion;
import org.testng.annotations.Test;

import java.util.Collections;

import static com.github.dfauth.jwt_jaas.authzn.Actions.*;
import static com.github.dfauth.jwt_jaas.authzn.Assertions.assertAllowed;
import static com.github.dfauth.jwt_jaas.authzn.Assertions.assertDenied;
import static com.github.dfauth.jwt_jaas.authzn.PrincipalType.ROLE;
import static com.github.dfauth.jwt_jaas.authzn.PrincipalType.USER;
import static com.github.dfauth.jwt_jaas.authzn.TestUtils.*;
import static org.testng.Assert.*;

public class PermissionTest {

    @Test
    public void testPolicySimplePrincipal() {

        ImmutablePrincipal fred = USER.of("fred");
        Permission perm = new RolePermission();
        Directive directive = new Directive(fred, perm);

        AuthorizationPolicy policy = new AuthorizationPolicyImpl(directive);
        AuthorizationDecision authorizationDecision = policy.permit(new ImmutableSubject(fred), new RolePermission());
        assertTrue(authorizationDecision.isAllowed());
    }

    @Test
    public void testPolicyRole() {

        ImmutableSubject subject = new ImmutableSubject(USER.of("fred"), ROLE.of("admin"), ROLE.of("user"));
        Permission perm = new TestPermission("/a/b/c/d", using(TestAction.class).parse("read"));
        Directive directive = new Directive(ROLE.of("superuser"), perm);
        AuthorizationPolicy policy = new AuthorizationPolicyImpl(directive);

        // expected to fail because of missing super user role
        assertDenied(policy.permit(subject, new TestPermission("/a/b/c/d/e/f/g", TestAction.READ)));

        // add super user role
        Subject subject1 = subject.with(ROLE.of("superuser"));
        assertAllowed(policy.permit(subject1, new TestPermission("/a/b/c/d/e/f/g", TestAction.READ)));

        // change the action
        assertDenied(policy.permit(subject1, new TestPermission("/a/b/c/c/e/f/g", TestAction.WRITE)));

        // change the resource - no longer within the same hierarchy
        assertDenied(policy.permit(subject1, new TestPermission("/a/b/c/c/e/f/g", TestAction.READ)));
    }

    @Test
    public void testRunner() {

        ImmutableSubject subject = new ImmutableSubject(USER.of("fred"), ROLE.of("admin"), ROLE.of("user"));
        Permission perm = new TestPermission("/a/b/c/d", using(TestAction.class).parse("*"));
        Directive directive = new Directive(ROLE.of("superuser"), perm);
        AuthorizationPolicy policy = new AuthorizationPolicyImpl(directive);

        try {
            AuthorizationDecision decision = policy.permit(subject, new TestPermission("/a/b/c/d/e/f/g", TestAction.READ));
            WasRunAssertion a = decision.run(() -> new WasRunAssertion().run());
            assertFalse(a.wasRun()); // expecting authzn failure
        } catch (SecurityException e) {
            // expected in this case
            assertEquals(e.getMessage(), subject+" is not authorized to perform actions "+ Collections.singleton(TestAction.READ)+" on resource /a/b/c/d/e/f/g");
        }

        try {
            // add super user role
            Subject subject1 = subject.with(ROLE.of("superuser"));
            AuthorizationDecision decision = policy.permit(subject1, new TestPermission("/a/b/c/d/e/f/g", TestAction.READ));
            WasRunAssertion a = decision.run(() -> new WasRunAssertion().run());
            assertTrue(a.wasRun()); // expecting authzn failure
        } catch (SecurityException e) {
            fail("Oops, expected it to be authorized");
        }
    }
}
