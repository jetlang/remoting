package org.jetlang.web;

public interface PathMatcher<T> {

    boolean handles(HttpRequest request, T sessionState);

    static <S> PathMatcher<S> pathEq(String s) {
        return new PathEq<>(s);
    }

    class PathEq<T> implements PathMatcher<T> {
        private String path;

        public PathEq(String path) {
            this.path = path;
        }

        @Override
        public boolean handles(HttpRequest request, T sessionState) {
            return path.equals(request.getPath());
        }
    }
}
