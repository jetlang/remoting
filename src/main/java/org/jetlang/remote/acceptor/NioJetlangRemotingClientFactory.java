package org.jetlang.remote.acceptor;

import org.jetlang.core.Disposable;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.core.RawMsgHandler;
import org.jetlang.remote.core.RawMsgHandlerFactory;
import org.jetlang.remote.core.Serializer;

import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import static org.jetlang.remote.core.RawMsgHandlerFactory.NULL_RAW_MSG_HANDLER_FACTORY;

public class NioJetlangRemotingClientFactory implements NioAcceptorHandler.ClientFactory {

    private final Serializer serializer;
    private final JetlangSessionConfig config;
    private final Handler handler;
    private final RawMsgHandlerFactory rawMsgHandlerFactory;
    private final NioJetlangSendFiber sendFiber;
    private final Charset charset;

    public interface Handler {
        void onNewSession(JetlangNioSession session);

        void onUnhandledReplyMsg(SelectionKey key, SocketChannel channel, String dataTopicVal, Object readObject);

        void onUnknownMessage(SelectionKey key, SocketChannel channel, int read);

        default void configureAcceptedClient(SelectionKey key, SocketChannel channel) throws SocketException {
            channel.socket().setSendBufferSize(1024 * 1024);
            channel.socket().setReceiveBufferSize(1024 * 1024);
            channel.socket().setTcpNoDelay(true);
        }

        void onHandlerException(Exception failed);
    }

    public NioJetlangRemotingClientFactory(Serializer serializer, JetlangSessionConfig config, Handler handler,
                                           NioJetlangSendFiber sendFiber, Charset charset) {
        this(serializer, config, handler, NULL_RAW_MSG_HANDLER_FACTORY, sendFiber, charset);
    }

    public NioJetlangRemotingClientFactory(Serializer serializer, JetlangSessionConfig config, Handler handler,
                                           RawMsgHandlerFactory rawMsgHandlerFactory, NioJetlangSendFiber sendFiber,
                                           Charset charset) {
        this.serializer = serializer;
        this.config = config;
        this.handler = handler;
        this.rawMsgHandlerFactory = rawMsgHandlerFactory;
        this.sendFiber = sendFiber;
        this.charset = charset;
    }

    @Override
    public void onAccept(NioFiber fiber, NioControls controls, SelectionKey key, SocketChannel channel) {
        try {
            handler.configureAcceptedClient(key, channel);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        Hb hb = new Hb();
        RawMsgHandler rawMsgHandler = rawMsgHandlerFactory.rawMsgHandler();
        final JetlangNioSession session = new JetlangNioSession(fiber, channel, sendFiber, new Id(channel), new JetlangNioSession.ErrorHandler() {
            @Override
            public void onUnhandledReplyMsg(int reqId, String dataTopicVal, Object readObject) {
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
        }, rawMsgHandler);

        Runnable onClose = () -> {
            hb.onClose();
            session.onClose(new SessionCloseEvent());
        };
        final NioJetlangChannelHandler handler = new NioJetlangChannelHandler(channel, session, serializer.getReader(), onClose, charset, rawMsgHandler);
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
