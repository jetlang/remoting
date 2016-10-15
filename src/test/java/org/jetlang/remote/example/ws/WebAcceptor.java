package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.acceptor.NioAcceptorHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

public class WebAcceptor {

    private final int port;
    private final NioFiber acceptorFiber;
    private NioAcceptorHandler.ClientFactory clientFactory;
    private final Config config;
    private Runnable onEnd;

    public WebAcceptor(int port, NioFiber acceptorFiber, NioAcceptorHandler.ClientFactory clientFactory, Config config, Runnable onEnd) {
        this.port = port;
        this.acceptorFiber = acceptorFiber;
        this.clientFactory = clientFactory;
        this.config = config;
        this.onEnd = onEnd;
    }

    public void start() {
        try {
            final ServerSocketChannel socketChannel = config.configure(port);
            socketChannel.configureBlocking(false);
            NioAcceptorHandler acceptorHandler = new NioAcceptorHandler(socketChannel, clientFactory, onEnd);
            acceptorFiber.addHandler(acceptorHandler);
        } catch (IOException failed) {
            throw new RuntimeException(failed);
        }
    }

    public static class Config {

        public ServerSocketChannel configure(int port) {
            try {
                ServerSocketChannel socketChannel = ServerSocketChannel.open();
                final InetSocketAddress address = new InetSocketAddress(port);
                socketChannel.socket().bind(address);
                return socketChannel;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }
}
