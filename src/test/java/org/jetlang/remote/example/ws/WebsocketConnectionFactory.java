package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;

import java.nio.channels.SocketChannel;

public class WebsocketConnectionFactory {
    private final WebSocketHandler handler;

    public WebsocketConnectionFactory(WebSocketHandler handler) {
        this.handler = handler;
    }

    public WebSocketConnection onConnection(HttpHeaders headers, SocketChannel channel, NioControls controls) {
        return new WebSocketConnection(headers, channel, controls);
    }
}
