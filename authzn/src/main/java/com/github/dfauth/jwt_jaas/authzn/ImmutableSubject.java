package com.github.dfauth.jwt_jaas.authzn;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ImmutableSubject implements Subject {
    private final Principal userPrincipal;
    private final Set<Principal> rolePrincipals;
    private final Set<Principal> all;

    public ImmutableSubject(Principal userPrincipal) {
        this(userPrincipal, Collections.emptySet());
    }

    public ImmutableSubject(Principal userPrincipal, Principal... rolePrincipals) {
        this(userPrincipal, new HashSet(Arrays.asList(rolePrincipals)));
    }

    ImmutableSubject(Principal userPrincipal, Set<Principal> rolePrincipals) {
        this.userPrincipal = userPrincipal;
        this.rolePrincipals = rolePrincipals;
        this.all = getPrincipals();
    }

    @Override
    public Set<Principal> getPrincipals() {
        if(this.all != null) {
            return this.all;
        }
        HashSet<Principal> tmp = new HashSet<>(rolePrincipals);
        tmp.add(userPrincipal);
        return Collections.unmodifiableSet(tmp);
    }

    public Subject with(ImmutablePrincipal p) {
        Set tmp = new HashSet(rolePrincipals);
        tmp.add(p);
        return new ImmutableSubject(userPrincipal, tmp);
    }

    public Principal getUserPrincipal() {
        return userPrincipal;
    }

    @Override
    public String toString() {
        return "user: "+userPrincipal.getName() + " roles: "+rolePrincipals.stream().map(p -> p.getName()).collect(Collectors.toSet());
    }
}
