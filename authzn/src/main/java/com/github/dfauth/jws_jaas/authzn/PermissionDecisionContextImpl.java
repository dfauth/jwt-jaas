package com.github.dfauth.jws_jaas.authzn;

import static com.github.dfauth.jws_jaas.authzn.AuthorizationDecision.DENY;

public class PermissionDecisionContextImpl implements PermissionDecisionContext {

    final Directive.DirectiveContext directiveContext;

    public PermissionDecisionContextImpl(Directive.DirectiveContext directive) {
        this.directiveContext = directive;
    }

    public AuthorizationDecision withPrincipal(Principal p) {
        return directiveContext.forPrincipal(p);
    }
}
