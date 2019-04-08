package com.github.dfauth.jwt_jaas.authzn;

import java.util.Set;
import java.util.concurrent.Callable;

import static com.github.dfauth.jwt_jaas.authzn.AuthorizationDecisionEnum.DENY;
import static com.github.dfauth.jwt_jaas.authzn.AuthorizationDecision.or;

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
        return new AuthorizationDecision(){
            @Override
            public boolean isAllowed() {
                return decision.isAllowed();
            }

            @Override
            public boolean isDenied() {
                return decision.isDenied();
            }

            @Override
            public <R> R run(Callable<R> callable) throws SecurityException {
                try {
                    return decision.run(callable);
                } catch(SecurityException e) {
                    throw new SecurityException(subject+" is not authorized to perform actions "+permission.getActions()+" on resource "+permission.getResource());
                }
            }
        };
    }

    protected abstract ResourceResolver getResourceResolver();

    abstract Set<Directive> directivesFor(Permission permission);
}
