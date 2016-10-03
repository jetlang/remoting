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

public abstract class WebSocketClientFactory implements NioAcceptorHandler.ClientFactory {
    @Override
    public void onAccept(NioFiber fiber, NioControls controls, SelectionKey key, SocketChannel channel) {
       configureChannel(channel);
       controls.addHandler(createHandler(key, channel));
    }

    protected NioChannelHandler createHandler(SelectionKey key, SocketChannel channel) {
        return new NioChannelHandler() {
            Protocol protocol = new Protocol();
            @Override
            public boolean onSelect(NioFiber nioFiber, NioControls nioControls, SelectionKey selectionKey) {
                try {
                    return protocol.onRead(channel);
                }catch(IOException failed){
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
