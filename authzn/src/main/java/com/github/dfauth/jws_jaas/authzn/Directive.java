package com.github.dfauth.jws_jaas.authzn;

import java.util.Collections;
import java.util.Set;

import static com.github.dfauth.jws_jaas.authzn.AuthorizationDecision.ALLOW;
import static com.github.dfauth.jws_jaas.authzn.AuthorizationDecision.DENY;
import static com.github.dfauth.jws_jaas.authzn.PermissionDecisionContext.NEVER;

public class Directive {
    private final Set<ImmutablePrincipal> principals;
    private final Permission permission;
    private final AuthorizationDecision decision;

    public Directive(ImmutablePrincipal principal, Permission permission) {
        this(Collections.singleton(principal), permission, ALLOW);
    }

    public Directive(ImmutablePrincipal principal, Permission permission, String action) {
        this(Collections.singleton(principal), permission, ALLOW);
    }

    public Directive(Set<ImmutablePrincipal> principals, Permission permission, AuthorizationDecision authznAction) {
        this.principals = principals;
        this.permission = permission;
        this.decision = authznAction;
    }

    public Set<ImmutablePrincipal> getPrincipals() {
        return principals;
    }

    public AuthorizationDecision getDecision() {
        return decision;
    }

    public Permission getPermission() {
        return permission;
    }

    public DirectiveContext withResolver(ResourceResolver resolver) {
        return new DirectiveContext() {
            @Override
            public PermissionDecisionContext decisionContextFor(Permission permission) {
                if(Directive.this.permission.implies(permission, resolver)) {
                    return new PermissionDecisionContextImpl(this);
                }
                return NEVER;
            }

            @Override
            public AuthorizationDecision forPrincipal(Principal p) {
                return principals.contains(p) ? ALLOW : DENY;
            }
        };
    }

    interface DirectiveContext {
        PermissionDecisionContext decisionContextFor(Permission permission);

        AuthorizationDecision forPrincipal(Principal p);
    }
}
