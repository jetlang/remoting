package org.jetlang.web;

import org.jetlang.fibers.Fiber;

public interface HttpSecurity<T> {
    boolean passes(Fiber dispatchFiber, HttpRequest headers, HttpResponse writer, T sessionState);

    static <T> HttpSecurity<T> none() {
        return new HttpSecurity<T>() {
            @Override
            public boolean passes(Fiber dispatchFiber, HttpRequest headers, HttpResponse writer, Object sessionState) {
                return true;
            }
        };
    }
}
