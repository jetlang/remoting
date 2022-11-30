package org.jetlang.remote.acceptor;

import org.jetlang.core.Disposable;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.core.Serializer;
import org.jetlang.remote.core.TopicReader;
import org.jetlang.web.IoBufferPool;
import org.jetlang.web.NioWriter;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

public class NioJetlangRemotingClientFactory<R, W> implements NioAcceptorHandler.ClientFactory {

    private final Serializer<R, W> serializer;
    private final JetlangSessionConfig config;
    private final Handler<R, W> handler;
    private final NioJetlangSendFiber<W> sendFiber;
    private final TopicReader charset;
    private final IoBufferPool.Default ioBufferPool;

    public interface Handler<R, W> {
        void onNewSession(JetlangNioSession<R, W> session);

        void onUnhandledReplyMsg(SelectionKey key, SocketChannel channel, String dataTopicVal, R readObject);

        void onUnknownMessage(SelectionKey key, SocketChannel channel, int read);

        default void configureAcceptedClient(SelectionKey key, SocketChannel channel) throws SocketException {
            channel.socket().setSendBufferSize(1024 * 1024);
            channel.socket().setReceiveBufferSize(1024 * 1024);
            channel.socket().setTcpNoDelay(true);
        }

        void onHandlerException(Exception failed);

        void onParseFailure(String topic, ByteBuffer buffer, int startingPosition, int dataSizeVal, Throwable failed);
    }

    public NioJetlangRemotingClientFactory(Serializer<R, W> serializer, JetlangSessionConfig config, Handler<R, W> handler, NioJetlangSendFiber<W> sendFiber, TopicReader charset) {
        this.serializer = serializer;
        this.config = config;
        this.handler = handler;
        this.sendFiber = sendFiber;
        this.charset = charset;
        this.ioBufferPool = new IoBufferPool.Default();
    }

    @Override
    public void onAccept(NioFiber fiber, NioControls controls, SelectionKey key, SocketChannel channel) {
        try {
            channel.configureBlocking(false);
        } catch (IOException e) {

        }
        try {
            handler.configureAcceptedClient(key, channel);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        Hb hb = new Hb();
        NioWriter writer = new NioWriter(new Object(), channel, fiber, ioBufferPool.createFor(channel, fiber));
        final JetlangNioSession<R, W> session = new JetlangNioSession<R, W>(fiber, sendFiber, new Id(channel), new JetlangNioSession.ErrorHandler<R>() {
            @Override
            public void onUnhandledReplyMsg(int reqId, String dataTopicVal, R readObject) {
                handler.onUnhandledReplyMsg(key, channel, dataTopicVal, readObject);
            }

            @Override
            public void onUnknownMessage(int read) {
                handler.onUnknownMessage(key, channel, read);
            }

            @Override
            public void onHandlerException(Exception failed) {
                handler.onHandlerException(failed);
            }

            @Override
            public void onParseFailure(String topic, ByteBuffer buffer, int startingPosition, int dataSizeVal, Throwable failed) {
                handler.onParseFailure(topic, buffer, startingPosition, dataSizeVal, failed);
            }
        }, writer);
        Runnable onClose = () -> {
            hb.onClose();
            session.onClose(new SessionCloseEvent());
        };
        final NioJetlangChannelHandler<R> handler = new NioJetlangChannelHandler<R>(channel, session, serializer.getReader(), onClose, charset);
        this.handler.onNewSession(session);
        hb.startHb(fiber, session, handler, config);
        controls.addHandler(handler);
    }

    public static class Hb {

        Disposable ds = () -> {
        };

        public void onClose() {
            ds.dispose();
        }

        public void startHb(NioFiber fiber, JetlangNioSession handler, NioJetlangChannelHandler nioJetlangChannelHandler, JetlangSessionConfig config) {
            Runnable runner = () -> {
                handler.sendHb();
                nioJetlangChannelHandler.checkForReadTimeout(config.getReadTimeoutInMs());
            };
            ds = fiber.scheduleWithFixedDelay(runner, config.getHeartbeatIntervalInMs(), config.getHeartbeatIntervalInMs(), TimeUnit.MILLISECONDS);
        }
    }

    public static class Id {
        private final String name;

        public Id(SocketChannel c) {
            final SocketAddress address = c.socket().getRemoteSocketAddress();
            name = address == null ? "unknown" : address.toString();
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
