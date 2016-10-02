package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.acceptor.NioAcceptorHandler;

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public abstract class WsClientFactory implements NioAcceptorHandler.ClientFactory {
    @Override
    public void onAccept(NioFiber fiber, NioControls controls, SelectionKey key, SocketChannel channel) {
       configureChannel(channel);
       controls.addHandler(createHandler(key, channel));
    }

    protected NioChannelHandler createHandler(SelectionKey key, SocketChannel channel) {
        return new NioChannelHandler() {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            @Override
            public boolean onSelect(NioFiber nioFiber, NioControls nioControls, SelectionKey selectionKey) {
                try {
                    while (channel.read(buffer) > 0) {
                        buffer.flip();
                        System.out.println(new String(buffer.array(), 0, buffer.limit()));
                        buffer.clear();
                    }
                }catch(IOException failed){
                    return false;
                }
                return true;
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
