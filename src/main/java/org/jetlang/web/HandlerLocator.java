package org.jetlang.web;

import java.util.ArrayList;

public interface HandlerLocator<T> {

    Handler<T> find(HttpRequest headers, T sessionState);

    class List<T> implements HandlerLocator<T> {
        private final ArrayList<HandlerLocator<T>> events = new ArrayList<>();

        public void add(PathMatcher<T> matcher, Handler<T> handler) {
            add(new PathLocator<T>(matcher, handler));
        }

        public void add(HandlerLocator<T> locator) {
            events.add(locator);
        }

        @Override
        public Handler<T> find(HttpRequest headers, T sessionState) {
            for (HandlerLocator<T> event : events) {
                Handler<T> h = event.find(headers, sessionState);
                if (h != null) {
                    return h;
                }
            }
            return null;
        }
    }

}
