package org.jetlang.web;

import org.jetlang.fibers.Fiber;

public class AuthHttpHandler<T> implements HttpHandler<T> {

    private final HttpHandler<T> target;
    private final HttpSecurity<T> security;

    public AuthHttpHandler(HttpHandler<T> target, HttpSecurity<T> security) {
        this.target = target;
        this.security = security;
    }

    @Override
    public void handle(Fiber dispatchFiber, HttpRequest headers, HttpResponse writer, T sessionState) {
        if (security.passes(dispatchFiber, headers, writer, sessionState)) {
            target.handle(dispatchFiber, headers, writer, sessionState);
        }
    }

}
