package com.github.dfauth.jwt_jaas.jwt;

public class Role {

    public static final String DEFAULT_SYSTEM_ID = "default";

    private String roleName;
    private String systemId;

    Role(String systemId, String roleName) {
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
        String systemId, roleName;
        String[] tmp = role.split(":");
        if(tmp.length != 2) {
            // default system
            systemId = DEFAULT_SYSTEM_ID;
            roleName = tmp[0];
        } else {
            systemId = tmp[0];
            roleName = tmp[1];
        }
        return new Role(systemId, roleName);
    }
}
