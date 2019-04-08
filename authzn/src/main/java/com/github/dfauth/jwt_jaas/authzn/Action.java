package com.github.dfauth.jwt_jaas.authzn;

public interface Action<E extends Enum<E>> {

    String name();

    default <E extends Enum<E> & Action<E>> boolean implies(Action<E> action) {
        return this.equals(action);
    }

}
