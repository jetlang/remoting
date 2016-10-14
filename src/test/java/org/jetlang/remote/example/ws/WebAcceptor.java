package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.acceptor.NioAcceptorHandler;

public class WebAcceptor {

    private final int port;
    private final NioFiber acceptorFiber;
    private NioAcceptorHandler.ClientFactory clientFactory;
    private Runnable onEnd;

    public WebAcceptor(int port, NioFiber acceptorFiber, NioAcceptorHandler.ClientFactory clientFactory, Runnable onEnd) {
        this.port = port;
        this.acceptorFiber = acceptorFiber;
        this.clientFactory = clientFactory;
        this.onEnd = onEnd;
    }

    public void start() {
        acceptorFiber.addHandler(NioAcceptorHandler.create(port, clientFactory, onEnd));
    }
}
