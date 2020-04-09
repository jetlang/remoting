package org.jetlang.remote.client;

public interface SocketWriter<T> {
    /**
     * @return true if sent, false if it fails to send due to the socket being disconnected.
     */
    boolean send(String topic, T msg);
}
