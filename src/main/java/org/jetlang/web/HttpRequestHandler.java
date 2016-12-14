package org.jetlang.web;

public interface HttpRequestHandler<T> {
    NioReader.State dispatch(SessionDispatcherFactory.SessionDispatcher<T> dispatcher, HttpRequest headers, HeaderReader<T> reader, NioWriter writer, T sessionState);

    class Default<T> implements HttpRequestHandler<T> {
        private HandlerLocator.List<T> handlerMap;
        private final Handler<T> defaultHandler;

        public Default(HandlerLocator.List<T> handlerMap, Handler<T> defaultHandler) {
            this.handlerMap = handlerMap;
            this.defaultHandler = defaultHandler;
        }

        @Override
        public NioReader.State dispatch(SessionDispatcherFactory.SessionDispatcher<T> dispatcher, HttpRequest headers, HeaderReader<T> reader, NioWriter writer, T sessionState) {
            Handler<T> h = handlerMap.find(headers, sessionState);
            if (h != null) {
                return h.start(dispatcher, headers, reader, writer, sessionState);
            } else {
                return defaultHandler.start(dispatcher, headers, reader, writer, sessionState);
            }
        }
    }
}
