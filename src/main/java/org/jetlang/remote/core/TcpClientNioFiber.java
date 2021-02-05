package org.jetlang.remote.core;

import org.jetlang.core.Disposable;
import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.web.IoBufferPool;
import org.jetlang.web.NioWriter;
import org.jetlang.web.SendResult;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.NoConnectionPendingException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TcpClientNioFiber {

    private final NioFiber fiber;
    private final IoBufferPool.Factory pool = new IoBufferPool.Default();

    public TcpClientNioFiber(NioFiber fiber) {
        this.fiber = fiber;
    }

    public interface ConnectedClient {

        boolean read(SocketChannel chan);

        void onDisconnect();
    }

    public Disposable connect(TcpClientNioConfig channel) {
        TcpConnectionState state = new TcpConnectionState(channel, fiber, pool);
        fiber.execute(() -> {
            state.startNewConnection(channel.getInitialConnectTimeoutInMs());
        });
        return () -> fiber.execute(state::close);
    }

    private static class TcpConnectionState {

        public boolean closed;
        private final List<SocketChannel> channels = new ArrayList<>();
        private final TcpClientNioConfig factory;
        private final NioFiber fiber;
        private final IoBufferPool.Factory pool;

        public TcpConnectionState(TcpClientNioConfig factory, NioFiber fiber, IoBufferPool.Factory pool) {
            this.factory = factory;
            this.fiber = fiber;
            this.pool = pool;
        }

        public void close() {
            closed = true;
            for (SocketChannel channel : new ArrayList<>(channels)) {
                fiber.close(channel);
            }
            factory.onDispose();
        }

        public void startNewConnection(long delayInMs) {
            if (!closed) {
                SocketChannel chan = factory.createNewSocketChannel();
                boolean connected = false;
                try {
                    connected = chan.connect(factory.getRemoteAddress());
                } catch (IOException e) {
                    factory.onInitialConnectException(chan, e);
                } catch (UnresolvedAddressException unresolved) {
                    factory.onUnresolvedAddress(chan, unresolved);
                }
                ReadHandler readHandler = new ReadHandler(chan, factory, this);
                ConnectHandler connect = new ConnectHandler(chan, factory, this, pool, readHandler);
                channels.add(chan);
                connect.connectTimeout = fiber.schedule(() -> {
                    readHandler.reconnectOnClose = false;
                    this.fiber.close(chan);
                    if (factory.onConnectTimeout(chan)) {
                        startNewConnection(delayInMs);
                    }
                }, delayInMs, TimeUnit.MILLISECONDS);
                if (connected) {
                    connect.onConnect(fiber);
                }
                else {
                    this.fiber.addHandler(connect);
                }
                this.fiber.addHandler(readHandler);
            }
        }
    }

    private static class ConnectHandler implements NioChannelHandler {
        private final SocketChannel chan;
        private final TcpClientNioConfig channel;
        private final TcpConnectionState state;
        public Disposable connectTimeout;
        private final IoBufferPool.Factory ioPool;
        private final ReadHandler readHandler;

        public ConnectHandler(SocketChannel chan, TcpClientNioConfig channel, TcpConnectionState state, IoBufferPool.Factory pool,
                              ReadHandler readHandler) {
            this.chan = chan;
            this.channel = channel;
            this.state = state;
            this.ioPool = pool;
            this.readHandler = readHandler;
        }

        @Override
        public Result onSelect(NioFiber nioFiber, NioControls controls, SelectionKey key) {
            try {
                if (chan.finishConnect()) {
                    onConnect(nioFiber);
                    return Result.RemoveHandler;
                }
                return Result.Continue;
            } catch (IOException | NoConnectionPendingException e) {
                readHandler.reconnectOnClose = false;
                return Result.CloseSocket;
            }
        }

        private void onConnect(NioFiber nioFiber) {
            connectTimeout.dispose();
            NioWriter writer = new NioWriter(new Object(), chan, nioFiber, ioPool.createFor(chan, nioFiber));
            readHandler.client = channel.createClientOnConnect(chan, nioFiber, writer);
        }

        @Override
        public SelectableChannel getChannel() {
            return chan;
        }

        @Override
        public int getInterestSet() {
            return SelectionKey.OP_CONNECT;
        }

        @Override
        public void onEnd() {
        }

        @Override
        public void onSelectorEnd() {
            onEnd();
        }
    }

    private static class ReadHandler implements NioChannelHandler {
        private final SocketChannel chan;
        private final TcpClientNioConfig channel;
        private final TcpConnectionState state;
        public boolean reconnectOnClose = true;
        ConnectedClient client;

        public ReadHandler(SocketChannel chan, TcpClientNioConfig channel, TcpConnectionState state) {
            this.chan = chan;
            this.channel = channel;
            this.state = state;
        }

        @Override
        public Result onSelect(NioFiber nioFiber, NioControls controls, SelectionKey key) {
            if (!client.read(chan)) {
                return Result.CloseSocket;
            }
            return Result.Continue;
        }

        @Override
        public SelectableChannel getChannel() {
            return chan;
        }

        @Override
        public int getInterestSet() {
            return SelectionKey.OP_READ;
        }

        @Override
        public void onEnd() {
            try {
                chan.close();
            } catch (IOException e) {
                channel.onCloseException(chan, e);
            }
            state.channels.remove(chan);
            boolean isReconnect = false;
            if (client != null) {
                client.onDisconnect();
                isReconnect = true;
            }
            long connectTimeout = isReconnect ? channel.getReconnectDelayInMs() : channel.getInitialConnectTimeoutInMs();
            if (reconnectOnClose && connectTimeout >= 0) {
                state.startNewConnection(connectTimeout);
            }
        }

        @Override
        public void onSelectorEnd() {
            onEnd();
        }
    }

}
