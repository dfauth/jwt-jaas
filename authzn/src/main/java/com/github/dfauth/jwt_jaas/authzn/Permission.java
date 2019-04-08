package com.github.dfauth.jwt_jaas.authzn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;

public abstract class Permission {

    private static final Logger logger = LoggerFactory.getLogger(Permission.class);

    private static final String ROOT_RESOURCE = "ROOT";

    private final String resource;
    private final Set<Action> actions;
    

    public Permission() {
        this(ROOT_RESOURCE, Collections.emptySet());
    }

    public Permission(String resource) {
        this(resource, Collections.emptySet());
    }

    public Permission(Set<Action> actions) {
        this(ROOT_RESOURCE, actions);
    }

    public Permission(String resource, Action action) {
        this(resource, Collections.singleton(action));
    }

    public Permission(String resource, Set<Action> actions) {
        this.resource = resource;
        this.actions = actions;
    }

//    public Resource getResource() {
//        return new SimpleResource(resource);
//    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null) {
            return false;
        }
        if(obj == this) {
            return true;
        }
        if(getClass().equals(obj.getClass())) {
            Permission other = getClass().cast(obj);
            return this.resource.equals(other.resource) && this.actions.equals(other.actions);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return resource.hashCode() | actions.hashCode();
    }

    public boolean implies(Permission permission, ResourceResolver resourceResolver) {
        if(permission == null) {
            logger.warn("null permission object passed to implies method of permission");
            return false;
        }
        if(permission == this) {
            return true;
        }
        // if the permission parameter is the same class or a subclass of this permission
        if(this.getClass().isAssignableFrom(permission.getClass())) {
            // test if this resource implies the resource in the permission
            if(resourceResolver.resource(new SimpleResource(this.resource)).implies(new SimpleResource(permission.resource))) {
                // test if this action implies the action in thr permission
                // special case - no actions on either side
                return (this.actions.isEmpty() && permission.actions.isEmpty()) ||
                        this.actions.containsAll(permission.actions);
            }
        }
        return false;
    }

    public String getResource() {
        return resource;
    }

    public Set<Action> getActions() {
        return this.actions;
    }

//    public abstract Optional<AuthorizationDecision> isAuthorizedBy(ResourceActionAuthorizationContext ctx);
}
