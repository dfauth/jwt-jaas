package com.github.dfauth.jwt_jaas.authzn;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SimpleResource<V> extends Resource<String, V> {

    public static Function<String, Iterable<String>> parseResourceString = p -> Arrays.asList(p.split("\\/")).stream().filter(s -> s.trim().length()>0).collect(Collectors.toSet());

    public SimpleResource(String path) {
        super(path, parseResourceString, Optional.empty());
    }

    public SimpleResource(String path, V payload) {
        super(path, parseResourceString, payload);
    }
}
