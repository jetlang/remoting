package org.jetlang.remote.example.ws;

import java.nio.charset.Charset;

public interface WebSocketHandler<T> {
    T onOpen(WebSocketConnection connection);

    void onMessage(WebSocketConnection connection, T state, String msg);

    void onClose(WebSocketConnection connection, T state);

    void onError(WebSocketConnection connection, T state, String msg);

    void onBinaryMessage(WebSocketConnection connection, T state, byte[] result, int size);

    default void onPing(WebSocketConnection connection, T state, byte[] result, int size, Charset charset) {
        String reply = size > 0 ? new String(result, 0, size, charset) : "";
        connection.sendPong(reply);
    }

    default void onPong(WebSocketConnection connection, T state, byte[] result, int size) {

    }
}
