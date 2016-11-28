package org.jetlang.web;

public interface SessionDispatcherFactory<S> {

    SessionDispatcher<S> createOnNewSession(S session, HttpRequest headers);

    class OnReadThreadDispatcher<S> implements SessionDispatcherFactory<S> {

        @Override
        public SessionDispatcher<S> createOnNewSession(S session, HttpRequest headers) {
            return new OnReadThread<>();
        }
    }

    class OnReadThread<S> implements SessionDispatcher<S> {

        @Override
        public <T> WebSocketHandler<S, T> createOnNewSession(WebSocketHandler<S, T> handler, HttpRequest headers, S sessionState) {
            return handler;
        }

        @Override
        public NioReader.State dispatch(HttpHandler<S> handler, HttpRequest headers, HeaderReader<S> headerReader, NioWriter writer, S sessionState) {
            handler.handle(headerReader.getReadFiber(), headers, headerReader.getHttpResponseWriter(), sessionState);
            return headerReader.start();
        }
    }

    interface SessionDispatcher<S> {
        <T> WebSocketHandler<S, T> createOnNewSession(WebSocketHandler<S, T> handler, HttpRequest headers, S sessionState);

        NioReader.State dispatch(HttpHandler<S> handler, HttpRequest headers, HeaderReader<S> headerReader, NioWriter writer, S sessionState);
    }

}
