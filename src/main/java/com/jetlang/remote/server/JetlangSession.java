package com.jetlang.remote.server;

import org.jetlang.channels.Channel;
import org.jetlang.channels.MemoryChannel;
import org.jetlang.fibers.Fiber;

import java.io.IOException;
import java.net.Socket;

public class JetlangSession {

    public final Channel<String> SubscriptionRequest = new MemoryChannel<String>();
    public final Channel<LogoutEvent> Logout = new MemoryChannel<LogoutEvent>();
    public final Channel<HeartbeatEvent> Heartbeat = new MemoryChannel<HeartbeatEvent>();

    private final Socket socket;
    private final Fiber sendFiber;

    public JetlangSession(Socket socket, Fiber sendFiber) {
        this.socket = socket;
        this.sendFiber = sendFiber;
    }

    void onSubscriptionRequest(String topic) {
        SubscriptionRequest.publish(topic);
    }

    public void write(final int byteToWrite) {
        Runnable r = new Runnable() {
            public void run() {
                try {
                    socket.getOutputStream().write(byteToWrite);
                } catch (IOException e) {
                    handleDisconnect(e);
                }
            }
        };
        sendFiber.execute(r);
    }

    private void handleDisconnect(IOException e) {

    }

    public void onLogout() {
        Logout.publish(new LogoutEvent());
    }

    public void onHb() {

        Heartbeat.publish(new HeartbeatEvent());
    }
}
