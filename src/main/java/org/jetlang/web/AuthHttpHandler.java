package org.jetlang.web;

import org.jetlang.fibers.NioFiber;

public class AuthHttpHandler<T> implements HttpHandler<T> {

    private final HttpHandler<T> target;
    private final HttpSecurity<T> security;

    public AuthHttpHandler(HttpHandler<T> target, HttpSecurity<T> security) {
        this.target = target;
        this.security = security;
    }

    @Override
    public void handle(NioFiber readFiber, HttpRequest headers, HttpResponse writer, T sessionState) {
        if (security.passes(readFiber, headers, writer, sessionState)) {
            target.handle(readFiber, headers, writer, sessionState);
        }
    }

}
