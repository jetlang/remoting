package org.jetlang.remote.core;

import org.jetlang.fibers.NioFiber;

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

    TcpClientNioFiber.ConnectedClient createClientOnConnect(SocketChannel chan, NioFiber nioFiber, TcpClientNioFiber.Writer writer);

    default boolean onConnectTimeout(SocketChannel chan) {
        return true;
    }

    int getInitialConnectTimeoutInMs();

    int getReconnectDelayInMs();

    void onInitialConnectException(SocketChannel chan, IOException e);

    void onCloseException(SocketChannel chan, IOException e);

    void onUnresolvedAddress(SocketChannel chan, UnresolvedAddressException unresolved);

    interface ClientFactory {
        TcpClientNioFiber.ConnectedClient createClientOnConnect(SocketChannel chan, NioFiber nioFiber, TcpClientNioFiber.Writer writer);
    }

    class Default implements TcpClientNioConfig{

        private final ErrorHandler handler;
        private ClientFactory clientFactory;
        private final int connectTimeoutInMs;
        private final int reconnectDelayInMs;
        private final Supplier<SocketAddress> addressSupplier;

        public Default(ErrorHandler handler, Supplier<SocketAddress> addressSupplier, ClientFactory clientFactory,
                       int connectTimeout, int reconnectDelay, TimeUnit timeUnit){
            this.handler = handler;
            this.clientFactory = clientFactory;
            this.connectTimeoutInMs = toMs(connectTimeout, timeUnit);
            this.reconnectDelayInMs = toMs(reconnectDelay, timeUnit);
            this.addressSupplier = addressSupplier;
        }

        private static int toMs(int reconnectDelay, TimeUnit timeUnit) {
            if(reconnectDelay == -1){
                return reconnectDelay;
            }
            return (int)timeUnit.toMillis(reconnectDelay);
        }

        @Override
        public TcpClientNioFiber.ConnectedClient createClientOnConnect(SocketChannel chan, NioFiber nioFiber, TcpClientNioFiber.Writer writer) {
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
        public int getInitialConnectTimeoutInMs() {
            return connectTimeoutInMs;
        }

        @Override
        public int getReconnectDelayInMs() {
            return reconnectDelayInMs;
        }
    }
}
