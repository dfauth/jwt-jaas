package com.github.dfauth.jws_jaas.authzn;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Actions<T extends Action> {

    public static final String ALL = "*";

    private final Set<Action> actions;

    public Actions(Set<T> actions) {
        this.actions = (Set<Action>) actions;
    }

    public static <T extends Action> Actions<T> using(Class<T> actionClass) {
        return new Actions(new HashSet(Arrays.asList(actionClass.getEnumConstants())));
    }

    public Set<Action> parse(String str) {
        return str.contains(ALL) ?
                this.actions :
                Stream.of(str.split(",")).map(s -> s.trim().toUpperCase()).flatMap(n -> actions.stream().filter(a -> n.equals(a.name().toUpperCase()))).collect(Collectors.toSet());
    }
}
