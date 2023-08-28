package org.jetlang.web;

import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

public interface HttpRequestHandler<T> {
    NioReader.State dispatch(SessionDispatcherFactory.SessionDispatcher<T> dispatcher, HttpRequest headers, HttpResponse response, HeaderReader<T> reader, NioWriter writer, T sessionState);

    void onException(Throwable processingException, SocketChannel channel);

    default void onUnsupportedHttpsConnection(SocketAddress remoteAddress){

    }

    interface ExceptionHandler {

        void onException(Throwable processingException, SocketChannel channel);
    }

    class Default<T> implements HttpRequestHandler<T> {
        private HandlerLocator.List<T> handlerMap;
        private final Handler<T> defaultHandler;
        private final ExceptionHandler handler;

        public Default(HandlerLocator.List<T> handlerMap, Handler<T> defaultHandler, ExceptionHandler handler) {
            this.handlerMap = handlerMap;
            this.defaultHandler = defaultHandler;
            this.handler = handler;
        }

        @Override
        public NioReader.State dispatch(SessionDispatcherFactory.SessionDispatcher<T> dispatcher, HttpRequest headers, HttpResponse response, HeaderReader<T> reader, NioWriter writer, T sessionState) {
            Handler<T> h = handlerMap.find(headers, sessionState);
            if (h != null) {
                return h.start(dispatcher, headers, response, reader, writer, sessionState);
            } else {
                return defaultHandler.start(dispatcher, headers, response, reader, writer, sessionState);
            }
        }

        @Override
        public void onException(Throwable processingException, SocketChannel channel) {
            this.handler.onException(processingException, channel);
        }
    }
}
