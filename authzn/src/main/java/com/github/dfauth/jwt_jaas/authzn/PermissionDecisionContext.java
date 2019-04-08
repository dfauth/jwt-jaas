package com.github.dfauth.jwt_jaas.authzn;

import static com.github.dfauth.jwt_jaas.authzn.AuthorizationDecisionEnum.DENY;

public interface PermissionDecisionContext {

    PermissionDecisionContext NEVER = p -> DENY;

    AuthorizationDecision withPrincipal(Principal p);
}
