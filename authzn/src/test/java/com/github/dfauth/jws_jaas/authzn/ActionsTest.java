package com.github.dfauth.jws_jaas.authzn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.util.Set;
import java.util.stream.Stream;

import static org.testng.Assert.assertTrue;

public class ActionsTest {

    private static final Logger logger = LoggerFactory.getLogger(ActionsTest.class);

    @Test
    public void testParse() {

        TestPermission perm = new TestPermission("/a/b/c/d/e/f", Actions.using(TestAction.class).parse("*"));

        Stream.of(TestAction.values()).forEach(v -> {
            assertTrue(perm.getActions().contains(v));
        });

    }

    private class TestPermission extends Permission{

        public TestPermission(String resource, Set<Action> actions) {
            super(resource, actions);
        }

    }

    private enum TestAction implements Action {
        CREATE, READ, UPDATE, DELETE;
    }
}
