package org.jetlang.web;

import org.jetlang.fibers.NioFiber;

public interface WebSocketSecurity<T> {
    boolean passes(NioFiber readFiber, HttpRequest headers, T sessionState);

    static <T> WebSocketSecurity<T> none() {
        return new WebSocketSecurity<T>() {
            @Override
            public boolean passes(NioFiber readFiber, HttpRequest headers, T sessionState) {
                return true;
            }
        };
    }
}
