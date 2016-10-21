package org.jetlang.web;

public interface Handler {
    NioReader.State start(HttpRequest headers, HeaderReader headerReader, NioWriter writer);
}
