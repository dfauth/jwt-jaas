package com.github.dfauth.jws_jaas.authzn;

public class PermissionDecisionContextImpl implements PermissionDecisionContext {

    final Directive.DirectiveContext directiveContext;

    public PermissionDecisionContextImpl(Directive.DirectiveContext directive) {
        this.directiveContext = directive;
    }

    public AuthorizationDecision withPrincipal(Principal p) {
        return directiveContext.forPrincipal(p);
    }
}
