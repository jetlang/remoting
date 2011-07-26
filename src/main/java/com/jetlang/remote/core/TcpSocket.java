package com.jetlang.remote.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: mrettig
 * Date: 4/13/11
 * Time: 9:44 AM
 */
public class TcpSocket implements ClosableOutputStream {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Socket socket;
    private final ErrorHandler errorHandler;
    private final SocketAddress remoteSocketAddress;

    public TcpSocket(Socket socket, ErrorHandler errorHandler) {
        this.socket = socket;
        this.errorHandler = errorHandler;
        this.remoteSocketAddress = socket.getRemoteSocketAddress();
    }

    public boolean close() {
        if (closed.compareAndSet(false, true)) {
            if (socket.isClosed()) {
                return true;
            } else {
                try {
                    socket.close();
                    return true;
                } catch (IOException e) {
                    errorHandler.onException(e);
                }
            }
        }
        return false;
    }

    public Socket getSocket() {
        return socket;
    }

    public SocketAddress getRemoteSocketAddress() {
        return remoteSocketAddress;
    }

    public OutputStream getOutputStream() throws IOException {
        return socket.getOutputStream();
    }

    public InputStream getInputStream() throws IOException {
        return socket.getInputStream();
    }
}
