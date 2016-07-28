package org.jetlang.remote.acceptor;

import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class NioAcceptorHandler implements NioChannelHandler {

    private final ServerSocketChannel channel;
    private final ClientFactory clientHandler;
    private final Runnable onEnd;

    public NioAcceptorHandler(ServerSocketChannel channel, ClientFactory clientHandler, Runnable onEnd) {
        this.channel = channel;
        this.clientHandler = clientHandler;
        this.onEnd = onEnd;
    }

    public interface ClientFactory {

        void onAccept(NioFiber fiber, NioControls controls, SelectionKey key, SocketChannel channel);

    }

    @Override
    public boolean onSelect(NioFiber nioFiber, NioControls controls, SelectionKey key) {
        try {
            final SocketChannel accept = channel.accept();
            clientHandler.onAccept(nioFiber, controls, key, accept);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public SelectableChannel getChannel() {
        return channel;
    }

    @Override
    public int getInterestSet() {
        return SelectionKey.OP_ACCEPT;
    }

    @Override
    public void onEnd() {
        try {
            channel.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        onEnd.run();
    }

    @Override
    public void onSelectorEnd() {
        onEnd();
    }

    public static NioAcceptorHandler create(int port, ClientFactory clientHandler, Runnable onEnd) {
        try {
            final ServerSocketChannel socketChannel = ServerSocketChannel.open();
            final InetSocketAddress address = new InetSocketAddress(port);
            socketChannel.socket().bind(address);
            socketChannel.configureBlocking(false);
            return new NioAcceptorHandler(socketChannel, clientHandler, onEnd);
        } catch (Exception failed) {
            throw new RuntimeException(failed);
        }

    }
}
