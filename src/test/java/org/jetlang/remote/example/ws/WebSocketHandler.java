package org.jetlang.remote.example.ws;

public interface WebSocketHandler {
    void onOpen(WebSocketConnection connection);

    void onMessage(WebSocketConnection connection, String msg);

    void onClose(WebSocketConnection connection);
}
