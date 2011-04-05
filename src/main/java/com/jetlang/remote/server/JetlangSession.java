package com.jetlang.remote.server;

import org.jetlang.channels.Channel;
import org.jetlang.channels.MemoryChannel;

import java.net.Socket;

public class JetlangSession {

    public Channel<String> SubscriptionRequest = new MemoryChannel<String>();

    public JetlangSession(Socket socket) {
    }

    void onSubscriptionRequest(String topic) {
        SubscriptionRequest.publish(topic);
    }
}
