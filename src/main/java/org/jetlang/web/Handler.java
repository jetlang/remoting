package org.jetlang.web;

public interface Handler<T> {
    NioReader.State start(HttpRequest headers, HeaderReader<T> headerReader, NioWriter writer, T sessionState);
}
