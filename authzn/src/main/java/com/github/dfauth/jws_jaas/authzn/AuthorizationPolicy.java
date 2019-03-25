package com.github.dfauth.jws_jaas.authzn;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.github.dfauth.jws_jaas.authzn.AuthorizationDecision.ALLOW;
import static com.github.dfauth.jws_jaas.authzn.AuthorizationDecision.DENY;

public abstract class AuthorizationPolicy {

    public final AuthorizationDecision permit(Subject subject, Permission permission) {

        // decision context for the given permission
//        Function<Directive, PermissionDecisionContext> f = d -> d.withResolver(getResourceResolver()).decisionContextFor(permission);
//        Function<Directive, Function<Principal, AuthorizationDecision>> f1 = d -> p -> f.apply(d).withPrincipal(p);

        return  subject.getPrincipals().stream().flatMap(              // for each principal
                p -> directivesFor(permission).stream().map(       // for every directive associated with the given permission, most specific first
                        d -> d.withResolver(getResourceResolver()).decisionContextFor(permission).withPrincipal(p)   // decision context for the given permission and principal
                )).
                findFirst(). // the first is the most sepcific
                orElse(DENY);
    }

    protected abstract ResourceResolver getResourceResolver();

    abstract Set<Directive> directivesFor(Permission permission);
}
