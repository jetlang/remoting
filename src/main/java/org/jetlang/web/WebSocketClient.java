package org.jetlang.web;

import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import java.io.IOException;
import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WebSocketClient<T> {

    private static final Base64.Encoder encoder = Base64.getEncoder();

    private final NioFiber readFiber;
    private final String host;
    private final int port;
    private final Config config;
    private final WebSocketHandler<T> handler;
    private boolean reconnectAllowed = true;
    private volatile State state = new NotConnected();
    private final Object writeLock = new Object();
    private final String path;
    private final List<HttpCookie> cookies = new ArrayList<>();
    private static final Charset ascii = Charset.forName("ASCII");
    private static final Charset utf8 = Charset.forName("UTF-8");
    private static final State ClosedForGood = new State() {

        @Override
        public State stop(NioControls nioControls) {
            return ClosedForGood;
        }

        @Override
        public SendResult send(String msg) {
            return SendResult.Closed;
        }
    };


    public WebSocketClient(NioFiber readFiber, URI uri, Config config, WebSocketHandler<T> handler) {
        this.readFiber = readFiber;
        String scheme = uri.getScheme() == null ? "ws" : uri.getScheme();
        host = uri.getHost() == null ? "localhost" : uri.getHost();
        port = getPort(uri, scheme);
        this.config = config;
        this.handler = handler;
        this.path = getPath(uri);
    }

    private static String getPath(URI uri) {
        String path = uri.getPath();
        return "".equals(path) ? "/" : path;
    }

    private static int getPort(URI uri, String scheme) {
        int port = uri.getPort();
        boolean ssl = scheme.equalsIgnoreCase("wss");
        if (port == -1) {
            if (scheme.equalsIgnoreCase("ws")) {
                port = 80;
            } else if (ssl) {
                port = 443;
            }
        }
        return port;
    }

    public void addCookie(HttpCookie httpCookie) {
        this.cookies.add(httpCookie);
    }

    public void clearCookies() {
        this.cookies.clear();
    }


    private class Connected implements State {

        private WebSocketConnection connection;
        private SocketChannel newChannel;

        public Connected(WebSocketConnection connection, SocketChannel newChannel) {
            this.connection = connection;
            this.newChannel = newChannel;
        }

        @Override
        public State stop(NioControls nioControls) {
            connection.sendClose();
            return doClose(newChannel, nioControls);
        }

        @Override
        public SendResult send(String msg) {
            return this.connection.send(msg);
        }
    }

    private class WebSocketClientReader implements State, HttpRequestHandler {

        private final SocketChannel newChannel;
        private final CountDownLatch latch;

        public WebSocketClientReader(SocketChannel newChannel, NioWriter writer, NioControls nioControls, CountDownLatch latch) {
            this.newChannel = newChannel;
            this.latch = latch;
        }

        @Override
        public NioReader.State dispatch(HttpRequest headers, HeaderReader reader, NioWriter writer) {
            byte[] mask = new byte[]{randomByte(), randomByte(), randomByte(), randomByte()};
            WebSocketConnection connection = new WebSocketConnection(writer, mask, reader.getReadFiber());
            state = new Connected(connection, newChannel);
            WebSocketReader<T> wsReader = new WebSocketReader<>(connection, headers, utf8, handler, () -> WebSocketClient.this.reconnectOnClose(new CountDownLatch(1)));
            latch.countDown();
            return wsReader.start();
        }

        @Override
        public State stop(NioControls nioControls) {
            return doClose(newChannel, nioControls);
        }

        @Override
        public SendResult send(String msg) {
            return SendResult.Closed;
        }
    }

    private void reconnectOnClose(CountDownLatch latch) {
        if (reconnectAllowed) {
            readFiber.schedule(() -> reconnect(latch), config.getConnectTimeout(), config.getConnectTimeoutUnit());
        }
    }

    private State doClose(SocketChannel newChannel, NioControls nioControls) {
        nioControls.close(newChannel);
        return ClosedForGood;
    }


    private class AwaitingConnection implements State, NioChannelHandler {

        private final SocketChannel newChannel;
        private final NioWriter writer;
        private final CountDownLatch latch;
        private boolean connected;

        public AwaitingConnection(SocketChannel newChannel, NioWriter writer, CountDownLatch latch, boolean connected) {
            this.newChannel = newChannel;
            this.writer = writer;
            this.latch = latch;
            this.connected = connected;
        }

        @Override
        public State stop(NioControls nioControls) {
            return doClose(newChannel, nioControls);
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
                return false;
            }
            handleConnection(nioControls);
            connected = true;
            return false;
        }

        public void handleConnection(NioControls nioControls) {
            writer.send(createHandshake());
            WebSocketClientReader webSocketClientReader = new WebSocketClientReader(newChannel, writer, nioControls, latch);
            state = webSocketClientReader;
            nioControls.addHandler(new NioReader(newChannel, readFiber, nioControls, webSocketClientReader,
                    config.getReadBufferSizeInBytes(),
                    config.getMaxReadLoops()));
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
            if (!connected) {
                reconnectOnClose(latch);
            }
        }

        @Override
        public void onSelectorEnd() {
            try {
                newChannel.close();
            } catch (IOException e) {
            }
        }
    }

    private AwaitingConnection attemptConnect(CountDownLatch latch) {
        try {
            SocketChannel channel = SocketChannel.open();
            channel.configureBlocking(false);
            config.configure(channel);
            boolean connected = channel.connect(new InetSocketAddress(host, port));
            NioWriter writer = new NioWriter(writeLock, channel, readFiber);
            AwaitingConnection awaitingConnection = new AwaitingConnection(channel, writer, latch, connected);
            if (!connected) {
                readFiber.addHandler(awaitingConnection);
            }
            return awaitingConnection;
        } catch (IOException failed) {
            throw new RuntimeException(failed);
        }
    }

    private class NotConnected implements State {

        @Override
        public State stop(NioControls nioControls) {
            return ClosedForGood;
        }

        @Override
        public SendResult send(String msg) {
            return SendResult.Closed;
        }
    }

    interface State {

        State stop(NioControls nioControls);

        SendResult send(String msg);
    }

    public CountDownLatch start() {
        CountDownLatch latch = new CountDownLatch(1);
        start(false, latch);
        return latch;
    }

    private void start(boolean isReconnect, CountDownLatch latch) {
        readFiber.execute((nioControls) -> {
            if (isReconnect && !reconnectAllowed) {
                return;
            }
            state = state.stop(nioControls);
            AwaitingConnection pendingConn = attemptConnect(latch);
            if (!pendingConn.connected) {
                if (reconnectAllowed && config.getConnectTimeout() > 0) {
                    Runnable recon = () -> {
                        if (this.state == pendingConn) {
                            this.state = pendingConn.stop(nioControls);
                        }
                    };
                    readFiber.schedule(recon, config.getConnectTimeout(), config.getConnectTimeoutUnit());
                }
                this.state = pendingConn;
            } else {
                pendingConn.handleConnection(nioControls);
            }
        });
    }

    private void reconnect(CountDownLatch latch) {
        start(true, latch);
    }

    private ByteBuffer createHandshake() {
        HttpRequest request = new HttpRequest("GET", path, "HTTP/1.1");
        request.add("Host", host + ':' + port);
        request.add("Connection", "Upgrade");
        request.add("Upgrade", "websocket");
        request.add("Sec-WebSocket-Version", "13");
        if (!cookies.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < cookies.size(); i++) {
                HttpCookie cookie = cookies.get(i);
                if (i > 0) {
                    builder.append("; ");
                }

                builder.append(cookie.toString());
            }
            request.add("Cookie", builder.toString());
        }
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
        return encoder.encodeToString(key);
    }

    public void stop() {
        readFiber.execute((nioControls) -> {
            reconnectAllowed = false;
            state = state.stop(nioControls);
        });
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

        public int getConnectTimeout() {
            return 5;
        }

        public TimeUnit getConnectTimeoutUnit() {
            return TimeUnit.SECONDS;
        }
    }
}
