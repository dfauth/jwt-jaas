package com.github.dfauth.jwt_jaas.jwt;

public class RoleBuilder {
    private String roleName;
    private String systemId;


    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public Role build() {
        return new Role(systemId, roleName);
    }
}
