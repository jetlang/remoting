package org.jetlang.remote.client;

public interface SocketWriter {
    /**
     * @return true if sent, false if it fails to send due to the socket being disconnected.
     */
    <T> boolean send(String topic, T msg);
}
