package com.github.dfauth.jwt_jaas.authzn;

public interface ResourceResolver {

    ResourceResolverContext resource(Resource resource);

    interface ResourceResolverContext {

        public boolean implies(Resource resource);
    }
}


