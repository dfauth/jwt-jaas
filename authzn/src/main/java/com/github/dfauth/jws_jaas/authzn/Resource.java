package com.github.dfauth.jws_jaas.authzn;

import java.util.Optional;
import java.util.function.Function;

public class Resource<K,V> {

    private final String key;
    private final Iterable<K> path;
    protected final Optional<V> payload;

    public Resource(String path, Function<String, Iterable<K>> parser, V payload) {
        this(path, parser, Optional.of(payload));
    }

    public Resource(String path, Function<String, Iterable<K>> parser, Optional<V> payload) {
        this.key = path;
        this.path = parser.apply(path);
        this.payload = payload;
    }

    public Iterable<K> getIterablePath() {
        return path;
    }

    public String getPath() {
        return key;
    }

    public Optional<V> getPayload() {
        return payload;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null) return false;
        if(obj == this) return true;
        if(obj instanceof Resource) {
            Resource other = (Resource) obj;
            return this.path.equals(other.path) && this.payload.equals(other.payload);
        }
        return false;
    }
}
