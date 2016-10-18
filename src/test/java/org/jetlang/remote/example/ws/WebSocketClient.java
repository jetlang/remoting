package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class WebSocketClient<T> {

    private final NioFiber fiber;
    private final String host;
    private final int port;
    private final Config config;
    private final WebSocketHandler<T> handler;
    private volatile State state = new NotConnected();
    private final Object writeLock = new Object();
    private final String path;
    private static final Charset ascii = Charset.forName("ASCII");
    private static final Charset utf8 = Charset.forName("UTF-8");
    private static final State ClosedForGood = new State() {
        @Override
        public State attemptConnect() {
            return ClosedForGood;
        }

        @Override
        public State stop() {
            return ClosedForGood;
        }

        @Override
        public SendResult send(String msg) {
            return SendResult.Closed;
        }
    };


    public WebSocketClient(NioFiber fiber, String host, int port, Config config, WebSocketHandler<T> handler, String path) {
        this.fiber = fiber;
        this.host = host;
        this.port = port;
        this.config = config;
        this.handler = handler;
        this.path = path;
    }

    private class Connected implements State {

        private WebSocketConnection connection;
        private SocketChannel newChannel;

        public Connected(WebSocketConnection connection, SocketChannel newChannel) {
            this.connection = connection;
            this.newChannel = newChannel;
        }

        @Override
        public State attemptConnect() {
            return this;
        }

        @Override
        public State stop() {
            return doClose(newChannel);
        }

        @Override
        public SendResult send(String msg) {
            return this.connection.send(msg);
        }
    }

    private class WebSocketClientReader implements State, HttpRequestHandler {

        private SocketChannel newChannel;

        public WebSocketClientReader(SocketChannel newChannel, NioWriter writer, NioControls nioControls) {
            this.newChannel = newChannel;
        }

        @Override
        public NioReader.State dispatch(HttpRequest headers, HeaderReader reader, NioWriter writer) {
            System.out.println("headers = " + headers);
            WebSocketConnection connection = new WebSocketConnection(writer);
            WebSocketReader<T> wsReader = new WebSocketReader<>(connection, headers, utf8, handler);
            synchronized (writeLock) {
                state = new Connected(connection, newChannel);
            }
            return wsReader.start();
        }

        @Override
        public State attemptConnect() {
            return this;
        }

        @Override
        public State stop() {
            return doClose(newChannel);
        }

        @Override
        public SendResult send(String msg) {
            return SendResult.Closed;
        }
    }

    private State doClose(SocketChannel channel) {
        fiber.execute((controls) -> controls.close(channel));
        return ClosedForGood;
    }

    private class AwaitingConnection implements State, NioChannelHandler {

        private final SocketChannel newChannel;
        private final NioWriter writer;

        public AwaitingConnection(SocketChannel newChannel, NioWriter writer) {
            this.newChannel = newChannel;
            this.writer = writer;
        }

        @Override
        public State stop() {
            return doClose(newChannel);
        }

        @Override
        public SendResult send(String msg) {
            return SendResult.Closed;
        }

        @Override
        public State attemptConnect() {
            return this;
        }

        @Override
        public boolean onSelect(NioFiber nioFiber, NioControls nioControls, SelectionKey selectionKey) {
            try {
                newChannel.finishConnect();
            } catch (IOException e) {
                synchronized (writeLock) {
                    state = new NotConnected();
                }
                return false;
            }
            writer.send(createHandshake());
            WebSocketClientReader webSocketClientReader = new WebSocketClientReader(newChannel, writer, nioControls);
            synchronized (writeLock) {
                state = webSocketClientReader;
            }
            nioControls.addHandler(new NioReader(newChannel, fiber, nioControls, webSocketClientReader,
                    config.getReadBufferSizeInBytes(),
                    config.getMaxReadLoops()));
            System.out.println("Connected!");
            return false;
        }

        @Override
        public SelectableChannel getChannel() {
            return newChannel;
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
        }
    }

    private class NotConnected implements State {

        @Override
        public State attemptConnect() {
            SocketChannel newChannel = openChannel();
            NioWriter writer = new NioWriter(writeLock, newChannel, fiber);
            AwaitingConnection awaitingConnection = new AwaitingConnection(newChannel, writer);
            fiber.addHandler(awaitingConnection);
            return awaitingConnection;
        }

        @Override
        public State stop() {
            return ClosedForGood;
        }

        @Override
        public SendResult send(String msg) {
            return SendResult.Closed;
        }
    }

    interface State {
        State attemptConnect();

        State stop();

        SendResult send(String msg);
    }

    public void start() {
        synchronized (writeLock) {
            state = state.attemptConnect();
        }
    }

    private ByteBuffer createHandshake() {
        StringBuilder builder = new StringBuilder();
        builder.append("GET " + path + " HTTP/1.1\r\n");
        builder.append("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n");
        builder.append("\r\n");
        return ByteBuffer.wrap(builder.toString().getBytes(ascii));
    }

    public void stop() {
        synchronized (writeLock) {
            state = state.stop();
        }
    }

    private SocketChannel openChannel() {
        try {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            config.configure(channel);
            channel.connect(new InetSocketAddress(host, port));
            return channel;
        } catch (IOException failed) {
            throw new RuntimeException(failed);
        }
    }

    public void send(String msg) {
        state.send(msg);
    }


    public static class Config {

        public void configure(SocketChannel channel) throws IOException {

        }

        public int getReadBufferSizeInBytes() {
            return 1024;
        }

        public int getMaxReadLoops() {
            return 50;
        }
    }
}
