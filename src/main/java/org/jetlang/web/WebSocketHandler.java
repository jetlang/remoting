package org.jetlang.web;

import java.nio.channels.SocketChannel;

public interface WebSocketHandler<S, T> {

    T onOpen(WebSocketConnection connection, HttpRequest headers, S sessionState);

    void onMessage(WebSocketConnection connection, T state, String msg);

    void onClose(WebSocketConnection connection, T state);

    void onError(WebSocketConnection connection, T state, String msg);

    void onException(WebSocketConnection connection, T state, Exception failed);

    void onBinaryMessage(WebSocketConnection connection, T state, byte[] result, int size);

    default void onPing(WebSocketConnection connection, T state, byte[] result, int size, StringDecoder charset) {
        connection.sendPong(result, 0, size);
    }

    default void onPong(WebSocketConnection connection, T state, byte[] result, int size) {

    }

    void onUnknownException(Throwable processingException, SocketChannel channel);
}
