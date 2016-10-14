package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.acceptor.NioAcceptorHandler;

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
        NioAcceptorHandler handler = NioAcceptorHandler.create(port, clientFactory, onEnd);
        config.configure(handler.getChannel());
        acceptorFiber.addHandler(handler);
    }

    public interface Config {
        void configure(ServerSocketChannel channel);
    }
}
