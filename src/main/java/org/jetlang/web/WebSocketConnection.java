package org.jetlang.web;

import org.jetlang.core.DisposingExecutor;
import org.jetlang.core.Scheduler;

import java.net.SocketAddress;

public interface WebSocketConnection extends Scheduler, DisposingExecutor {


    /**
     * @return the remote address or null if it can't be resolved.
     */
    SocketAddress getRemoteAddress();

    /**
     * Non-Blocking send of a text message.
     */
    SendResult send(String msg);

    /**
     * Non-Blocking send
     */
    SendResult sendPong(byte[] bytes, int offset, int length);

    /**
     * Non-Blocking send
     */
    SendResult sendPing(byte[] bytes, int offset, int length);

    /**
     * Non-Blocking send of a binary message.
     */
    SendResult sendBinary(byte[] buffer, int offset, int length);

    /**
     * Attempts to close the underlying socket.
     */
    void close();
}
