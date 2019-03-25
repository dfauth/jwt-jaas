package com.github.dfauth.jws_jaas.authzn;

import java.util.Set;

import static com.github.dfauth.jws_jaas.authzn.AuthorizationDecisionEnum.DENY;
import static com.github.dfauth.jws_jaas.authzn.AuthorizationDecision.or;

public abstract class AuthorizationPolicy {

    public final AuthorizationDecision permit(Subject subject, Permission permission) {

        AuthorizationDecision decision =
               directivesFor(permission).stream().map(d ->                                                  // for every directive associated with the given permission, most specific first
                        d.withResolver(getResourceResolver()).decisionContextFor(permission)).map( dc ->    // resolve the directive down to a decision context
                                subject.getPrincipals().stream().map(p ->                                   // for each principal associated with this subject
                                        dc.withPrincipal(p)                                                 // apply the principal to the decision context to get a authorization decision
                                ).reduce(DENY, or)                                                         // reduce it accepting any principal allowed
                        ).findFirst().                                                                      // the first entry has spriority
                        orElse(DENY);                                                                       // but  if none is found, deny
        return new AuthorizationDecisionRunner(decision, subject, permission);
    }

    protected abstract ResourceResolver getResourceResolver();

    abstract Set<Directive> directivesFor(Permission permission);
}
