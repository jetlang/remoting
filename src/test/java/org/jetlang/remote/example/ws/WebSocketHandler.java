package org.jetlang.remote.example.ws;

public interface WebSocketHandler<T> {
    T onOpen(WebSocketConnection connection);

    void onMessage(WebSocketConnection connection, T state, String msg);

    void onClose(WebSocketConnection connection, T state);

    void onError(String msg);

    void onBinaryMessage(WebSocketConnection connection, T state, byte[] result, int size);

    default void onPing(WebSocketConnection connection, T state, byte[] result, int size) {
        connection.sendPong();
    }
}
