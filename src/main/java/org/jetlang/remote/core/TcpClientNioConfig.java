package org.jetlang.remote.core;

import org.jetlang.fibers.NioFiber;
import org.jetlang.web.NioWriter;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public interface TcpClientNioConfig {

    SocketAddress getRemoteAddress();

    default SocketChannel createNewSocketChannel() {
        try {
            SocketChannel open = SocketChannel.open();
            open.configureBlocking(false);
            open.setOption(StandardSocketOptions.TCP_NODELAY, true);
            return open;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    TcpClientNioFiber.ConnectedClient createClientOnConnect(SocketChannel chan, NioFiber nioFiber, NioWriter writer);

    default boolean onConnectTimeout(SocketChannel chan) {
        return true;
    }

    long getInitialConnectTimeoutInMs();

    long getReconnectDelayInMs();

    void onInitialConnectException(SocketChannel chan, IOException e);

    void onCloseException(SocketChannel chan, IOException e);

    void onUnresolvedAddress(SocketChannel chan, UnresolvedAddressException unresolved);

    default void onDispose(){

    }

    interface ClientFactory {
        TcpClientNioFiber.ConnectedClient createClientOnConnect(SocketChannel chan, NioFiber nioFiber, NioWriter writer);
    }

    class Default implements TcpClientNioConfig {

        private final ErrorHandler handler;
        private ClientFactory clientFactory;
        private final long connectTimeoutInMs;
        private final long reconnectDelayInMs;
        private int receiveBufferSize = -1;
        private int sendBufferSize = -1;
        private final Supplier<SocketAddress> addressSupplier;

        public Default(ErrorHandler handler, Supplier<SocketAddress> addressSupplier, ClientFactory clientFactory,
                       long connectTimeout, long reconnectDelay, TimeUnit timeUnit) {
            this.handler = handler;
            this.clientFactory = clientFactory;
            this.connectTimeoutInMs = toMs(connectTimeout, timeUnit);
            this.reconnectDelayInMs = toMs(reconnectDelay, timeUnit);
            this.addressSupplier = addressSupplier;
        }

        private static long toMs(long reconnectDelay, TimeUnit timeUnit) {
            if (reconnectDelay == -1) {
                return reconnectDelay;
            }
            return timeUnit.toMillis(reconnectDelay);
        }

        public int getReceiveBufferSize() {
            return receiveBufferSize;
        }

        public void setReceiveBufferSize(int receiveBufferSize) {
            this.receiveBufferSize = receiveBufferSize;
        }

        public int getSendBufferSize() {
            return sendBufferSize;
        }

        public void setSendBufferSize(int sendBufferSize) {
            this.sendBufferSize = sendBufferSize;
        }


        @Override
        public SocketChannel createNewSocketChannel() {
            SocketChannel channel = TcpClientNioConfig.super.createNewSocketChannel();
            try {
                if (receiveBufferSize > 0) {
                    channel.setOption(StandardSocketOptions.SO_RCVBUF, receiveBufferSize);
                }
                if (sendBufferSize > 0) {
                    channel.setOption(StandardSocketOptions.SO_SNDBUF, sendBufferSize);
                }
            } catch (IOException failed) {
                handler.onException(failed);
            }
            return channel;
        }

        @Override
        public TcpClientNioFiber.ConnectedClient createClientOnConnect(SocketChannel chan, NioFiber nioFiber, NioWriter writer) {
            return clientFactory.createClientOnConnect(chan, nioFiber, writer);
        }

        @Override
        public void onInitialConnectException(SocketChannel chan, IOException e) {
            handler.onException(e);
        }

        @Override
        public void onUnresolvedAddress(SocketChannel chan, UnresolvedAddressException unresolved) {
            handler.onException(unresolved);
        }

        @Override
        public void onCloseException(SocketChannel chan, IOException e) {
            handler.onException(e);
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return addressSupplier.get();
        }

        @Override
        public long getInitialConnectTimeoutInMs() {
            return connectTimeoutInMs;
        }

        @Override
        public long getReconnectDelayInMs() {
            return reconnectDelayInMs;
        }
    }
}
