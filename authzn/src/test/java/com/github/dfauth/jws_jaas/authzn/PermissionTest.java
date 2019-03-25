package com.github.dfauth.jws_jaas.authzn;

import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static com.github.dfauth.jws_jaas.authzn.PermissionTest.WasRunAssertion.State.NOT_RUN;
import static com.github.dfauth.jws_jaas.authzn.PermissionTest.WasRunAssertion.State.WAS_RUN;
import static com.github.dfauth.jws_jaas.authzn.PrincipalType.ROLE;
import static com.github.dfauth.jws_jaas.authzn.PrincipalType.USER;
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
        Permission perm = new TestPermission("/a/b/c/d", Actions.using(TestAction.class).parse("read"));
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
        Permission perm = new TestPermission("/a/b/c/d", Actions.using(TestAction.class).parse("*"));
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

    private void assertAllowed(AuthorizationDecision decision) {
        assertTrue(decision.isAllowed());
    }

    private void assertDenied(AuthorizationDecision decision) {
        assertTrue(decision.isDenied());
    }

    class RolePermission extends Permission {

    }

    class TestPermission extends Permission {

        public TestPermission(String resource, Set<Action> actions) {
            super(resource, actions);
        }

        public TestPermission(String resource, TestAction action) {
            super(resource, action);
        }
    }

    enum TestAction implements Action {
        READ, WRITE
    }

    class AuthorizationPolicyImpl extends AuthorizationPolicy {

        ResourceHierarchy<String, Directive> hierarchy = new ResourceHierarchy<>();

        public AuthorizationPolicyImpl(Directive directive) {
            hierarchy.add(new SimpleResource<>(directive.getPermission().getResource(), directive));
        }

        @Override
        protected ResourceResolver getResourceResolver() {
            return resource1 -> (ResourceResolver.ResourceResolverContext) resource2 ->
                    hierarchy.findAllResourcesInPath(resource2.getIterablePath()).stream().filter((Predicate<Resource<String, Directive>>) resource ->
                            resource1.implies(resource2)).
                            findFirst().
                            isPresent();
        }

        @Override
        Set<Directive> directivesFor(Permission permission) {
            Set<Directive> directives = new HashSet<>();
            hierarchy.walk(resource -> resource.payload.ifPresent(d -> directives.add(d)));
            return directives;
        }
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
