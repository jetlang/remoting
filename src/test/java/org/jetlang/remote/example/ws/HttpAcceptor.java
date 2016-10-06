package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.acceptor.NioAcceptorHandler;

public class HttpAcceptor {

    private final int port;
    private final NioFiber fiber;
    private NioAcceptorHandler.ClientFactory clientFactory;
    private Runnable onEnd;

    public HttpAcceptor(int port, NioFiber fiber, NioAcceptorHandler.ClientFactory clientFactory, Runnable onEnd) {
        this.port = port;
        this.fiber = fiber;
        this.clientFactory = clientFactory;
        this.onEnd = onEnd;
    }

    public void start() {
        fiber.addHandler(NioAcceptorHandler.create(port, clientFactory, onEnd));
    }
}
