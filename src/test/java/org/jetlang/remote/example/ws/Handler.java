package org.jetlang.remote.example.ws;

public interface Handler {
    NioReader.State start(HttpRequest headers, HeaderReader headerReader, NioWriter writer);
}
