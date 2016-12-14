package org.jetlang.web;

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
}
