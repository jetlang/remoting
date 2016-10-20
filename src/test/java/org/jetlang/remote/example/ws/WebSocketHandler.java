package org.jetlang.remote.example.ws;

public interface WebSocketHandler<T> {

    T onOpen(WebSocketConnection connection, HttpRequest headers);

    void onMessage(WebSocketConnection connection, T state, String msg);

    void onClose(WebSocketConnection connection, T state);

    void onError(WebSocketConnection connection, T state, String msg);

    void onBinaryMessage(WebSocketConnection connection, T state, byte[] result, int size);

    default void onPing(WebSocketConnection connection, T state, byte[] result, int size, StringDecoder charset) {
        connection.sendPong(result, 0, size);
    }

    default void onPong(WebSocketConnection connection, T state, byte[] result, int size) {

    }
}
