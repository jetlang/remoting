package com.jetlang.remote.acceptor;

/**
 * User: mrettig
 * Date: 4/13/11
 * Time: 1:53 PM
 */
public class ClientTcpSocket {

    private final TcpSocket socket;
    private volatile JetlangStreamSession session;

    public ClientTcpSocket(TcpSocket socket) {
        this.socket = socket;
    }

    public TcpSocket getSocket() {
        return socket;
    }

    public void close() {
        socket.close();
    }

    public void setSession(JetlangStreamSession session) {
        this.session = session;
    }

    public void publishIfSubscribed(String topic, byte[] bytes) {
        if (session != null) {
            session.publishIfSubscribed(topic, bytes);
        }
    }
}
