package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.acceptor.NioAcceptorHandler;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class WebSocketAcceptor {

    private final int port;
    private final NioFiber fiber;
    private NioAcceptorHandler.ClientFactory clientFactory;
    private Runnable onEnd;

    public WebSocketAcceptor(int port, NioFiber fiber, NioAcceptorHandler.ClientFactory clientFactory, Runnable onEnd){
        this.port = port;
        this.fiber = fiber;
        this.clientFactory = clientFactory;
        this.onEnd = onEnd;
    }

    public void start(){
        fiber.addHandler(NioAcceptorHandler.create(port, clientFactory, onEnd));
    }
}
