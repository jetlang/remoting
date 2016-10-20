package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.webbitserver.helpers.Base64;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class WebSocketClient<T> {

    private final NioFiber readFiber;
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
        public State stop() {
            return ClosedForGood;
        }

        @Override
        public SendResult send(String msg) {
            return SendResult.Closed;
        }
    };


    public WebSocketClient(NioFiber readFiber, String host, int port, Config config, WebSocketHandler<T> handler, String path) {
        this.readFiber = readFiber;
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
        public State stop() {
            connection.sendClose();
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
            byte[] mask = new byte[]{randomByte(), randomByte(), randomByte(), randomByte()};
            WebSocketConnection connection = new WebSocketConnection(writer, mask);
            state = new Connected(connection, newChannel);
            WebSocketReader<T> wsReader = new WebSocketReader<>(connection, headers, utf8, handler);
            return wsReader.start();
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
        readFiber.execute((controls) -> controls.close(channel));
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
        public boolean onSelect(NioFiber nioFiber, NioControls nioControls, SelectionKey selectionKey) {
            try {
                newChannel.finishConnect();
            } catch (IOException e) {
                state = new NotConnected();
                return false;
            }
            writer.send(createHandshake());
            WebSocketClientReader webSocketClientReader = new WebSocketClientReader(newChannel, writer, nioControls);
            state = webSocketClientReader;
            nioControls.addHandler(new NioReader(newChannel, readFiber, nioControls, webSocketClientReader,
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

    private State attemptConnect() {
        SocketChannel newChannel = openChannel();
        NioWriter writer = new NioWriter(writeLock, newChannel, readFiber);
        AwaitingConnection awaitingConnection = new AwaitingConnection(newChannel, writer);
        readFiber.addHandler(awaitingConnection);
        return awaitingConnection;
    }

    private class NotConnected implements State {

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

        State stop();

        SendResult send(String msg);
    }

    public void start() {
        readFiber.execute(() -> {
            state = state.stop();
            state = attemptConnect();
        });
    }

    private ByteBuffer createHandshake() {
        HttpRequest request = new HttpRequest("GET", path, "HTTP/1.1");
        request.add("Host", host + ':' + port);
        request.add("Connection", "Upgrade");
        request.add("Upgrade", "websocket");
        request.add("Sec-WebSocket-Version", "13");
        request.add("Sec-WebSocket-Key", secKey());
        return request.toByteBuffer(ascii);
    }

    private static byte randomByte() {
        return (byte) ((int) (Math.random() * 256.0D));
    }

    private static String secKey() {
        byte[] key = new byte[16];
        for (int i = 0; i < 16; ++i) {
            key[i] = randomByte();
        }
        return Base64.encode(key);
    }

    public void stop() {
        readFiber.execute(() -> {
            state = state.stop();
        });
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

    public SendResult send(String msg) {
        return state.send(msg);
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
