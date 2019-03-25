package com.github.dfauth.jws_jaas.authzn;

import static com.github.dfauth.jws_jaas.authzn.AuthorizationDecisionEnum.DENY;

public interface PermissionDecisionContext {

    PermissionDecisionContext NEVER = p -> DENY;

    AuthorizationDecision withPrincipal(Principal p);
}
