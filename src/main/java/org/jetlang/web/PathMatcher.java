package org.jetlang.web;

import java.util.ArrayList;

public interface PathMatcher<T> {

    boolean handles(HttpRequest request, T sessionState);

    static <S> PathMatcher<S> uriEq(String s) {
        return new UriEq<>(s);
    }

    class UriEq<T> implements PathMatcher<T> {
        private String path;

        public UriEq(String path) {
            this.path = path;
        }

        @Override
        public boolean handles(HttpRequest request, T sessionState) {
            return path.equals(request.getRequestUri());
        }
    }

    class List<T> {
        private final ArrayList<PathDispatcher<T>> events = new ArrayList<>();

        public void add(PathMatcher<T> uri, Handler<T> stWebSocketRequestHandler) {
            events.add(pair(uri, stWebSocketRequestHandler));
        }

        private PathDispatcher<T> pair(PathMatcher<T> objectUriEq, Handler<T> handler) {
            return new PathDispatcher<T>(objectUriEq, handler);
        }

        public Handler<T> find(HttpRequest headers, T sessionState) {
            for (PathDispatcher<T> event : events) {
                if (event.matcher.handles(headers, sessionState)) {
                    return event.handler;
                }
            }
            return null;
        }
    }
}
