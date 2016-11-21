package org.jetlang.web;

import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.acceptor.NioAcceptorHandler;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class WebDispatcher<S> implements NioAcceptorHandler.ClientFactory {

    private final NioFiber readFiber;
    private final HttpRequestHandler<S> handler;
    private final int readBufferSizeInBytes;
    private final int maxReadLoops;
    private final SessionFactory<S> factory;

    public WebDispatcher(NioFiber readFiber, HttpRequestHandler<S> handler, int readBufferSizeInBytes, int maxReadLoops, SessionFactory<S> factory) {
        this.readFiber = readFiber;
        this.handler = handler;
        this.readBufferSizeInBytes = readBufferSizeInBytes;
        this.maxReadLoops = maxReadLoops;
        this.factory = factory;
    }

    @Override
    public void onAccept(NioFiber acceptorFiber, NioControls acceptorControls, SelectionKey key, SocketChannel channel) {
        readFiber.execute((readControls) -> {
            readControls.addHandler(createHandler(key, channel, readFiber, readControls));
        });
    }

    protected NioChannelHandler createHandler(SelectionKey key, SocketChannel channel, NioFiber fiber, NioControls controls) {
        return new NioReader<S>(channel, fiber, controls, handler, readBufferSizeInBytes, maxReadLoops, factory);
    }
}
