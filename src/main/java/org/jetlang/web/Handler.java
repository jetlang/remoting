package org.jetlang.web;

public interface Handler<T> {
    NioReader.State start(HttpRequest headers, HeaderReader headerReader, NioWriter writer, T sessionState);
}
