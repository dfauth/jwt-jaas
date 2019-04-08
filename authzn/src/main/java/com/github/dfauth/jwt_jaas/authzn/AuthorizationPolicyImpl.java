package com.github.dfauth.jwt_jaas.authzn;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class AuthorizationPolicyImpl extends AuthorizationPolicy {

    ResourceHierarchy<String, Directive> hierarchy = new ResourceHierarchy<>();

    public AuthorizationPolicyImpl(Directive directive) {
        this(Collections.singletonList(directive));
    }

    public AuthorizationPolicyImpl(List<Directive> directives) {
        directives.forEach(d -> {
            hierarchy.add(new SimpleResource<>(d.getPermission().getResource(), d));
        });
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
