package org.jetlang.web;

public class PathLocator<T> implements HandlerLocator<T> {

    private final PathMatcher<T> matcher;
    private final Handler<T> handler;

    public PathLocator(PathMatcher<T> matcher, Handler<T> handler) {
        this.matcher = matcher;
        this.handler = handler;
    }

    @Override
    public Handler<T> find(HttpRequest headers, T sessionState) {
        if (matcher.handles(headers, sessionState)) {
            return handler;
        }
        return null;
    }
}
