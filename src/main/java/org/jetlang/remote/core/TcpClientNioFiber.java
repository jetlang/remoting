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
    private static final int CONNECT_AND_READ = SelectionKey.OP_READ | SelectionKey.OP_CONNECT;
    private final IoBufferPool.Factory pool = new IoBufferPool.Default();

    public TcpClientNioFiber(NioFiber fiber) {
        this.fiber = fiber;
    }

    public static class Writer {

        private final NioWriter writer;

        public Writer(NioWriter writer) {
            this.writer = writer;
        }

        public SendResult write(byte[] data) {
            return write(data, 0, data.length);
        }

        public SendResult write(byte[] toSend, int start, int length) {
            return writer.send(toSend, start, length);
        }

        public SendResult write(ByteBuffer bb) {
            return writer.send(bb);
        }
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
        }

        public void startNewConnection(long delayInMs) {
            if (!closed) {
                SocketChannel chan = factory.createNewSocketChannel();
                boolean connected = false;
                try {
                    connected = chan.connect(factory.getRemoteAddress());
                } catch (IOException e) {
                    factory.onInitialConnectException(chan, e);
                }
                catch (UnresolvedAddressException unresolved){
                    factory.onUnresolvedAddress(chan, unresolved);
                }
                TcpChannelHandler handler = new TcpChannelHandler(chan, factory, this, pool);
                this.fiber.addHandler(handler);
                channels.add(chan);
                handler.connectTimeout = fiber.schedule(() -> {
                    handler.reconnectOnClose = false;
                    this.fiber.close(chan);
                    if (factory.onConnectTimeout(chan)) {
                        startNewConnection(delayInMs);
                    }
                }, delayInMs, TimeUnit.MILLISECONDS);
                if (connected) {
                    handler.onConnect(fiber);
                }
            }
        }
    }

    private static class TcpChannelHandler implements NioChannelHandler {
        private final SocketChannel chan;
        private final TcpClientNioConfig channel;
        private final TcpConnectionState state;
        public Disposable connectTimeout;
        public boolean reconnectOnClose = true;
        ConnectedClient client;
        private final IoBufferPool.Factory ioPool;

        public TcpChannelHandler(SocketChannel chan, TcpClientNioConfig channel, TcpConnectionState state, IoBufferPool.Factory pool) {
            this.chan = chan;
            this.channel = channel;
            this.state = state;
            this.ioPool = pool;
        }

        @Override
        public Result onSelect(NioFiber nioFiber, NioControls controls, SelectionKey key) {
            int readyOps = key.readyOps();
            boolean connect = (readyOps & SelectionKey.OP_CONNECT) != 0;
            if (connect) {
                try {
                    if (chan.finishConnect()) {
                        onConnect(nioFiber);
                    }
                } catch (IOException | NoConnectionPendingException e) {
                    if (state.closed) {
                        return Result.CloseSocket;
                    } else {
                        return Result.Continue;
                    }
                }
            }
            boolean read = (readyOps & SelectionKey.OP_READ) != 0;
            if (read && !client.read(chan)) {
                return Result.CloseSocket;
            }
            return Result.Continue;
        }

        private void onConnect(NioFiber nioFiber) {
            connectTimeout.dispose();
            NioWriter writer = new NioWriter(new Object(), chan, nioFiber, ioPool.createFor(chan, nioFiber));
            client = channel.createClientOnConnect(chan, nioFiber, new Writer(writer));
        }

        @Override
        public SelectableChannel getChannel() {
            return chan;
        }

        @Override
        public int getInterestSet() {
            return CONNECT_AND_READ;
        }

        @Override
        public void onEnd() {
            connectTimeout.dispose();
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
