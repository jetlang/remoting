package org.jetlang.web;

import org.jetlang.fibers.NioFiber;

public interface HttpHandler<T> extends Handler<T> {

    @Override
    default NioReader.State start(HttpRequest headers, HeaderReader<T> headerReader, NioWriter writer, T sessionState) {
        handle(headerReader.getReadFiber(), headers, headerReader.getHttpResponseWriter(), sessionState);
        return headerReader.start();
    }

    void handle(NioFiber readFiber, HttpRequest headers, HttpResponseWriter writer, T sessionState);
}
