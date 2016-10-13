package org.jetlang.remote.example.ws;

public interface HttpRequestHandler {
    NioReader.State dispatch(HttpRequest headers, HeaderReader reader, NioWriter writer);
}
