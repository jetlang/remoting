package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.acceptor.NioAcceptorHandler;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class WebDispatcher implements NioAcceptorHandler.ClientFactory {

    private final NioFiber readFiber;
    private final HttpRequestHandler handler;
    private final int readBufferSizeInBytes;
    private final int maxReadLoops;

    public WebDispatcher(NioFiber readFiber, HttpRequestHandler handler, int readBufferSizeInBytes, int maxReadLoops) {
        this.readFiber = readFiber;
        this.handler = handler;
        this.readBufferSizeInBytes = readBufferSizeInBytes;
        this.maxReadLoops = maxReadLoops;
    }

    @Override
    public void onAccept(NioFiber acceptorFiber, NioControls acceptorControls, SelectionKey key, SocketChannel channel) {
        readFiber.execute((readControls) -> {
            readControls.addHandler(createHandler(key, channel, readFiber, readControls));
        });
    }

    protected NioChannelHandler createHandler(SelectionKey key, SocketChannel channel, NioFiber fiber, NioControls controls) {
        return new NioChannelHandler() {
            NioReader nioReader = new NioReader(channel, fiber, controls, handler, readBufferSizeInBytes, maxReadLoops);

            @Override
            public boolean onSelect(NioFiber nioFiber, NioControls nioControls, SelectionKey selectionKey) {
                try {
                    return nioReader.onRead();
                } catch (IOException failed) {
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
                nioReader.onClosed();
            }

            @Override
            public void onSelectorEnd() {
                onEnd();
            }
        };
    }
}
