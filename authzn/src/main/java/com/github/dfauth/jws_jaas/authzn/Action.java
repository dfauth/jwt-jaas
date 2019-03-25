package com.github.dfauth.jws_jaas.authzn;

public interface Action<E extends Enum<E>> {

    default <E extends Enum<E> & Action<E>> boolean implies(Action<E> action) {
        return this.equals(action);
    }

}
