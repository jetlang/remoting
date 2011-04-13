package com.jetlang.remote.server;

import com.jetlang.remote.core.ClosableOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * User: mrettig
 * Date: 4/13/11
 * Time: 9:44 AM
 */
public class TcpSocket implements ClosableOutputStream {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Socket socket;

    public TcpSocket(Socket socket) {
        this.socket = socket;
    }

    public boolean close() {
        if (closed.compareAndSet(false, true)) {
            try {
                socket.close();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public Socket getSocket() {
        return socket;
    }

    public Object getId() {
        return socket.getInetAddress();
    }

    public OutputStream getOutputStream() throws IOException {
        return socket.getOutputStream();
    }

    public InputStream getInputStream() throws IOException {
        return socket.getInputStream();
    }
}
