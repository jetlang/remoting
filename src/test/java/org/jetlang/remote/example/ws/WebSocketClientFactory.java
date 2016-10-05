package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.acceptor.NioAcceptorHandler;

import java.io.IOException;
import java.net.SocketException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class WebSocketClientFactory implements NioAcceptorHandler.ClientFactory {

    private final WebSocketHandler handler;

    public WebSocketClientFactory(WebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onAccept(NioFiber fiber, NioControls controls, SelectionKey key, SocketChannel channel) {
        configureChannel(channel);
        controls.addHandler(createHandler(key, channel, fiber, controls));
    }

    protected NioChannelHandler createHandler(SelectionKey key, SocketChannel channel, NioFiber fiber, NioControls controls) {
        return new NioChannelHandler() {
            Protocol protocol = new Protocol(channel, fiber, controls, handler);
            @Override
            public boolean onSelect(NioFiber nioFiber, NioControls nioControls, SelectionKey selectionKey) {
                try {
                    return protocol.onRead();
                } catch (IOException failed) {
                    return false;
                }

            }
            @Override
            public SelectableChannel getChannel() {
                return channel;
            }

            @Override
            public int getInterestSet() {
                return SelectionKey.OP_READ;
            }

            @Override
            public void onEnd() {

            }

            @Override
            public void onSelectorEnd() {

            }
        };
    }

    protected void configureChannel(SocketChannel channel) {
        try {
            channel.socket().setTcpNoDelay(true);
        } catch (SocketException e) {

        }
    }
}
