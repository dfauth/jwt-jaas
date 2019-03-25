package com.github.dfauth.jws_jaas.authzn;

public class ImmutablePrincipal implements Principal {

    private final PrincipalType principalType;
    private final String source;
    private final String name;

    ImmutablePrincipal(PrincipalType principalType, String name) {
        this(principalType,"default",name);
    }

    ImmutablePrincipal(PrincipalType principalType, String source, String name) {
        this.principalType = principalType;
        this.source = source;
        this.name = name;
    }

    @Override
    public PrincipalType getPrincipalType() {
        return principalType;
    }

    @Override
    public String getSource() {
        return source;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return principalType.hashCode() | source.hashCode() | name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null) {
            return false;
        }
        if(obj == this) {
            return true;
        }
        if(obj instanceof ImmutablePrincipal) {
            ImmutablePrincipal other = (ImmutablePrincipal) obj;
            return principalType.equals(other.principalType) && source.equals(other.source) && name.equals(other.name);
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("ImmutablePrincipal(%s,%s,%s)",principalType,source,name);
    }
}
