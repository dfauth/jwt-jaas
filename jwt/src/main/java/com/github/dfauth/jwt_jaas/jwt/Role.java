package com.github.dfauth.jwt_jaas.jwt;

public class Role {
    private String roleName;
    private String systemId;

    Role(String roleName, String systemId) {
        this.roleName = roleName;
        this.systemId = systemId;
    }

    public String getSystemId() {
        return systemId;
    }

    public String getRoleName() {
        return roleName;
    }

    public static Role role(String role) {
        String[] tmp = role.split(":");
        if(tmp.length != 2) {
            throw new IllegalArgumentException("Invalid format - expecting 'systemId:roleName'");
        }
        return new Role(tmp[0], tmp[1]);
    }
}
