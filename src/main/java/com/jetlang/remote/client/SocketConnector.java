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
    private final JetlangClientConfig config;
    private boolean tcpNoDelay = true;

    public SocketConnector(String host, int port, JetlangClientConfig config) {
        this.host = host;
        this.port = port;
        this.config = config;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public Socket connect() throws IOException {
        Socket socket = new Socket(host, port);
        socket.setTcpNoDelay(tcpNoDelay);
        socket.setSoTimeout(config.getHeartbeatIntervalInMs() * 2);
        return socket;
    }
}
