package org.jetlang.web;

import org.jetlang.fibers.NioFiber;

public interface HttpSecurity<T> {
    boolean passes(NioFiber readFiber, HttpRequest headers, HttpResponseWriter writer, T sessionState);
}
