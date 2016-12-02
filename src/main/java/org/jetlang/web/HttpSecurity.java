package org.jetlang.web;

import org.jetlang.fibers.NioFiber;

public interface HttpSecurity<T> {
    boolean passes(NioFiber readFiber, HttpRequest headers, HttpResponseWriter writer, T sessionState);

    static <T> HttpSecurity<T> none() {
        return new HttpSecurity<T>() {
            @Override
            public boolean passes(NioFiber readFiber, HttpRequest headers, HttpResponseWriter writer, Object sessionState) {
                return true;
            }
        };
    }
}
