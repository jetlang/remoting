package org.jetlang.web;

import org.jetlang.fibers.Fiber;

public interface HttpHandler<T> extends Handler<T> {

    @Override
    default NioReader.State start(SessionDispatcherFactory.SessionDispatcher<T> dispatcher, HttpRequest headers, HttpResponse response, HeaderReader<T> headerReader, NioWriter writer, T sessionState) {
        return dispatcher.dispatch(this, headers, response, headerReader, writer, sessionState);
    }

    void handle(Fiber dispatchFiber, HttpRequest headers, HttpResponse writer, T sessionState);
}
