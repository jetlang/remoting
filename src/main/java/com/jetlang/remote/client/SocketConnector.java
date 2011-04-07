package com.jetlang.remote.client;

import java.io.IOException;
import java.net.Socket;

/**
 * User: mrettig
 * Date: 4/5/11
 * Time: 11:39 AM
 */
public class SocketConnector {

    private final String host;
    private final int port;
    private boolean tcpNoDelay = true;
    private int readTimeoutInMs = 3000;

    public SocketConnector(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public int getReadTimeoutInMs() {
        return readTimeoutInMs;
    }

    public void setReadTimeoutInMs(int readTimeoutInMs) {
        this.readTimeoutInMs = readTimeoutInMs;
    }

    public Socket connect() throws IOException {
        Socket socket = new Socket(host, port);
        socket.setTcpNoDelay(tcpNoDelay);
        socket.setSoTimeout(readTimeoutInMs);
        return socket;
    }
}
