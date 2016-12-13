package org.jetlang.web;

public class PathDispatcher<T> {

    final PathMatcher<T> matcher;
    final Handler<T> handler;

    public PathDispatcher(PathMatcher<T> matcher, Handler<T> handler) {
        this.matcher = matcher;
        this.handler = handler;
    }
}
