package com.github.dfauth.jwt_jaas.authzn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

public class ResourceHierarchy<K,V> {

    private static final Logger logger = LoggerFactory.getLogger(ResourceHierarchy.class);

    private static ResourceHierarchy ROOT = new ResourceHierarchy();

    private Map<K, ResourceHierarchy<K,V>> children = new HashMap<>();
    private final Optional<Resource<K,V>> resource;

    public ResourceHierarchy() {
        resource = Optional.empty();
    }

    public ResourceHierarchy<K,V> add(Resource<K,V> resource) {
        findOrPut(resource);
        return this;
    }

    public ResourceHierarchy<K,V> add(Resource<K,V>... resources) {
        Arrays.stream(resources).forEach(r -> add(r));
        return this;
    }

    public ResourceHierarchy(Resource<K,V> resource) {
        this.resource = Optional.of(resource);
    }

    public Optional<ResourceHierarchy<K, V>> find(Iterable<K> path) {
        return find(path.iterator());
    }

    private Optional<ResourceHierarchy<K,V>> find(Iterator<K> it) {
        if(!it.hasNext()) {
            return Optional.of(this);
        } else {
            Optional<ResourceHierarchy<K, V>> next = find(it.next());
            return next.map(h -> h.find(it)).orElse(Optional.empty());
        }
    }

    public ResourceHierarchy<K, V> findNearest(Iterable<K> path, Consumer<Resource<K,V>> consumer) {
        return findNearest(path.iterator(), consumer);
    }

    public ResourceHierarchy<K, V> findNearest(Iterable<K> path) {
        return findNearest(path.iterator(), r -> {});
    }

    public Optional<Resource<K, V>> findResource(Iterable<K> path) {
        return find(path.iterator()).map(h -> h.resource()).orElse(Optional.empty());
    }

    private ResourceHierarchy<K,V> findNearest(Iterator<K> it, Consumer<Resource<K,V>> consumer) {
        if(!it.hasNext()) {
            return this;
        } else {
            Optional<ResourceHierarchy<K, V>> next = find(it.next());
            return next.map(h -> {
                h.resource().ifPresent(consumer);
                return h.findNearest(it, consumer);
            }).orElseGet(() ->this);
        }
    }

    private Optional<ResourceHierarchy<K,V>> find(K next) {
        return Optional.ofNullable(children.get(next));
    }

    private ResourceHierarchy findOrPut(Resource resource) {
        return findOrPut(resource.getIterablePath().iterator(), resource);
    }

    private ResourceHierarchy findOrPut(Iterator<K> it, Resource resource) {
        if(!it.hasNext()) {
            return this;
        } else {
            K key = it.next();
            Optional<ResourceHierarchy<K, V>> next = find(key);
            return next.orElseGet(() -> {
                ResourceHierarchy < K, V > tmp;
                if (it.hasNext()) {
                    tmp = new ResourceHierarchy();
                } else {
                    tmp = new ResourceHierarchy(resource);
                }
                children.put(key, tmp);
                return tmp;
            }).findOrPut(it, resource);
        }
    }

    public Optional<Resource<K,V>> resource() {
        return resource;
    }

    public Collection<V> findAllInPath(Iterable<K> path) {
        Deque<V> stack = new ArrayDeque();
        findNearest(path, r -> r.getPayload().ifPresent(p -> stack.push(p)));
        return stack;
    }

    public Collection<Resource<K,V>> findAllResourcesInPath(Iterable<K> path) {
        Deque<Resource<K,V>> stack = new ArrayDeque();
        findNearest(path, r -> stack.push(r));
        return stack;
    }

    public void walk(Consumer<Resource<K, V>> consumer) {
        children.values().forEach(c -> c.walk(consumer));
        resource.ifPresent(consumer);
    }
}
