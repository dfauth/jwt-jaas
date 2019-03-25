package com.github.dfauth.jws_jaas.authzn;

public class DirectiveResource extends SimpleResource<Directive> {

    public DirectiveResource(Directive directive) {
        super(directive.getPermission().getResource(), directive);
    }
}
