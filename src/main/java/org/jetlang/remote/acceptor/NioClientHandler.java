package org.jetlang.remote.acceptor;

import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class NioClientHandler implements NioChannelHandler {

    private final SocketChannel socket;
    private final Reader r;

    public interface Reader {
        boolean onRead(NioFiber nioFiber, NioControls controls, SelectionKey key, SocketChannel channel);
    }

    public NioClientHandler(SocketChannel socket, Reader r) {
        this.socket = socket;
        this.r = r;
    }

    public static NioClientHandler create(String host, int port, Reader reader) {
        try {
            final SocketChannel channel = SocketChannel.open(new InetSocketAddress(host, port));
            channel.socket().setTcpNoDelay(true);
            return new NioClientHandler(channel, reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SocketChannel getSocket() {
        return socket;
    }

    @Override
    public boolean onSelect(NioFiber nioFiber, NioControls controls, SelectionKey key) {
        return r.onRead(nioFiber, controls, key, socket);
    }

    @Override
    public SelectableChannel getChannel() {
        return socket;
    }

    @Override
    public int getInterestSet() {
        return SelectionKey.OP_READ;
    }

    @Override
    public void onEnd() {
        onSelectorEnd();
    }

    @Override
    public void onSelectorEnd() {
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
