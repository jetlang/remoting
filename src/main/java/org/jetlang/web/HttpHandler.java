package org.jetlang.web;

import org.jetlang.fibers.NioFiber;

public interface HttpHandler<T> extends Handler<T> {

    @Override
    default NioReader.State start(SessionDispatcherFactory.SessionDispatcher<T> dispatcher, HttpRequest headers, HeaderReader<T> headerReader, NioWriter writer, T sessionState) {
        return dispatcher.dispatch(this, headers, headerReader, writer, sessionState);
    }

    void handle(NioFiber readFiber, HttpRequest headers, HttpResponse writer, T sessionState);
}
