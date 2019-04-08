package com.github.dfauth.jwt_jaas.authzn;

import java.util.Set;

public class TestUtils {

    static class RolePermission extends Permission {

    }

    static class TestPermission extends Permission {

        public TestPermission(String resource, Set<Action> actions) {
            super(resource, actions);
        }

        public TestPermission(String resource, TestAction action) {
            super(resource, action);
        }
    }

    enum TestAction implements Action {
        READ, WRITE
    }
}
