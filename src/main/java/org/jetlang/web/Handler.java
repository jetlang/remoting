package org.jetlang.web;

public interface Handler<T> {
    NioReader.State start(SessionDispatcherFactory.SessionDispatcher<T> dispatcher, HttpRequest headers, HttpResponse response, HeaderReader<T> headerReader, NioWriter writer, T sessionState);
}
