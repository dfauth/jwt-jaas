package com.github.dfauth.jws_jaas.authzn;

import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

import static com.github.dfauth.jws_jaas.authzn.PrincipalType.USER;
import static org.testng.Assert.assertTrue;

public class PermissionTest {

    @Test
    public void testPolicy() {

        ImmutablePrincipal fred = USER.of("fred");
        Permission perm = new RolePermission();
        Directive directive = new Directive(fred, perm);
        ResourceHierarchy<String, Directive> hierarchy = new ResourceHierarchy<>();
        hierarchy.add(new SimpleResource<>(perm.getResource(), directive));

        AuthorizationPolicy policy = new AuthorizationPolicy() {

            @Override
            protected ResourceResolver getResourceResolver() {
                return resource1 -> (ResourceResolver.ResourceResolverContext) resource2 ->
                        hierarchy.findAllResourcesInPath(resource2.getIterablePath()).stream().filter((Predicate<Resource<String, Directive>>) resource ->
                                resource2.getPath().equals(resource1.getPath())).
                        findFirst().
                        isPresent();
            }

            @Override
            Set<Directive> directivesFor(Permission permission) {
                return Collections.singleton(directive);
            }
        };
        AuthorizationDecision authorizationDecision = policy.permit(new ImmutableSubject(fred), new RolePermission());
        assertTrue(authorizationDecision.isAllowed());
    }

    class RolePermission extends Permission {

    }

}
