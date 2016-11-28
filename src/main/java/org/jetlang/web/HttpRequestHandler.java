package org.jetlang.web;

import java.util.Map;

public interface HttpRequestHandler<T> {
    NioReader.State dispatch(SessionDispatcherFactory.SessionDispatcher<T> dispatcher, HttpRequest headers, HeaderReader<T> reader, NioWriter writer, T sessionState);

    class Default<T> implements HttpRequestHandler<T> {
        private Map<String, Handler<T>> handlerMap;
        private final Handler<T> defaultHandler;

        public Default(Map<String, Handler<T>> handlerMap, Handler<T> defaultHandler) {
            this.handlerMap = handlerMap;
            this.defaultHandler = defaultHandler;
        }

        @Override
        public NioReader.State dispatch(SessionDispatcherFactory.SessionDispatcher<T> dispatcher, HttpRequest headers, HeaderReader<T> reader, NioWriter writer, T sessionState) {
            Handler<T> h = handlerMap.get(headers.getRequestUri());
            if (h != null) {
                return h.start(dispatcher, headers, reader, writer, sessionState);
            } else {
                return defaultHandler.start(dispatcher, headers, reader, writer, sessionState);
            }
        }
    }
}
