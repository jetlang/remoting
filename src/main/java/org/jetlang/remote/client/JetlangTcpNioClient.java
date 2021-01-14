package org.jetlang.remote.client;

import org.jetlang.channels.Channel;
import org.jetlang.channels.ChannelSubscription;
import org.jetlang.channels.Subscribable;
import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.DisposingExecutor;
import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.acceptor.JetlangNioSession;
import org.jetlang.remote.acceptor.NioJetlangProtocolReader;
import org.jetlang.remote.acceptor.NioJetlangRemotingClientFactory;
import org.jetlang.remote.acceptor.NioJetlangSendFiber;
import org.jetlang.remote.core.CloseableChannel;
import org.jetlang.remote.core.ErrorHandler;
import org.jetlang.remote.core.HeartbeatEvent;
import org.jetlang.remote.core.JetlangRemotingProtocol;
import org.jetlang.remote.core.MsgTypes;
import org.jetlang.remote.core.Serializer;
import org.jetlang.remote.core.TcpClientNioConfig;
import org.jetlang.remote.core.TcpClientNioFiber;
import org.jetlang.remote.core.TopicReader;

import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class JetlangTcpNioClient<R, W> {

    private final TcpClientNioConfig tcpConfig;
    private final NioJetlangSendFiber<W> sendFiber;
    private final JetlangClientConfig config;
    private final Serializer<R, W> ser;
    private final ErrorHandler errorHandler;
    private final TcpClientNioFiber readFiber;

    private final CloseableChannel.Group channelsToClose = new CloseableChannel.Group();
    private final JetlangClientFactory<R, W> clientFactory;
    private final RemoteSubscriptions remoteSubscriptions;

    private <T> CloseableChannel<T> channel() {
        return channelsToClose.newChannel();
    }

    private final Channel<ConnectEvent> Connected = channel();
    private final Channel<CloseEvent> Closed = channel();
    private final Channel<HeartbeatEvent> hb = channel();

    public JetlangTcpNioClient(SocketConnector socketConnector,
                               NioJetlangSendFiber<W> sendFiber,
                               JetlangClientConfig config,
                               Serializer<R, W> ser,
                               ErrorHandler errorHandler,
                               JetlangNioSession.ErrorHandler<R> nioErrorHandler,
                               TcpClientNioFiber readFiber, TopicReader topicReader) {
        RemoteSubscriptions.SubscriptionWriter writer = new RemoteSubscriptions.SubscriptionWriter() {
            @Override
            public boolean sendSubscription(String topic) {
                return clientFactory.sendRemoteSubscription(topic, MsgTypes.Subscription);
            }

            @Override
            public void sendUnsubscribe(String topic) {
                clientFactory.sendRemoteSubscription(topic, MsgTypes.Unsubscribe);
            }
        };
        this.remoteSubscriptions = new RemoteSubscriptions(sendFiber.getFiber(), writer, channelsToClose);
        JetlangRemotingProtocol.ClientHandler<R> msgHandler = new JetlangRemotingProtocol.ClientHandler<R>(errorHandler, hb, this.remoteSubscriptions) {
            @Override
            public void onLogout() {

            }

            @Override
            public void onRequestReply(int reqId, String dataTopicVal, R readObject) {
                errorHandler.onException(new RuntimeException("Req/Reply not supported. " + dataTopicVal + " " + readObject));
            }
        };
        this.clientFactory = new JetlangClientFactory<>(ser, sendFiber, nioErrorHandler, topicReader, msgHandler);
        this.tcpConfig = new TcpClientNioConfig.Default(errorHandler, socketConnector::getInetSocketAddress,
                clientFactory, config.getInitialConnectDelayInMs(),
                config.getReconnectDelayInMs(), TimeUnit.MILLISECONDS);
        this.sendFiber = sendFiber;
        this.config = config;
        this.ser = ser;
        this.errorHandler = errorHandler;
        this.readFiber = readFiber;
    }

    public Channel<ConnectEvent> getConnectChannel() {
        return Connected;
    }

    public Channel<CloseEvent> getCloseChannel() {
        return Closed;
    }

    public Disposable start() {
        return readFiber.connect(tcpConfig);
    }

    public <T extends R> Disposable subscribe(String topic, DisposingExecutor executor, Callback<T> msg) {
        return subscribe(topic, new ChannelSubscription<>(executor, msg));
    }

    private <T> Disposable subscribe(String topic, Subscribable<T> tChannelSubscription) {
        return remoteSubscriptions.subscribe(topic, tChannelSubscription);
    }

    public void publish(String topic, W msg) {
        clientFactory.publish(topic, msg);
    }

    private static class JetlangClientFactory<R, W> implements TcpClientNioConfig.ClientFactory {

        private final Serializer<R, W> ser;
        private final NioJetlangSendFiber<W> sendFiber;
        private final JetlangNioSession.ErrorHandler<R> errorHandler;
        private final TopicReader topicReader;
        private final JetlangRemotingProtocol.ClientHandler<R> msgHandler;

        private volatile NioJetlangSendFiber.ChannelState channel;

        public JetlangClientFactory(Serializer<R, W> ser, NioJetlangSendFiber<W> sendFiber, JetlangNioSession.ErrorHandler<R> errorHandler,
                                    TopicReader topicReader, JetlangRemotingProtocol.ClientHandler<R> msgHandler) {
            this.ser = ser;
            this.sendFiber = sendFiber;
            this.errorHandler = errorHandler;
            this.topicReader = topicReader;
            this.msgHandler = msgHandler;
        }

        @Override
        public TcpClientNioFiber.ConnectedClient createClientOnConnect(SocketChannel chan, NioFiber nioFiber, TcpClientNioFiber.Writer writer) {
            //JetlangNioSession<R, W> session = new JetlangNioSession<R, W>(nioFiber, chan, sendFiber, new NioJetlangRemotingClientFactory.Id(chan), errorHandler);
            NioJetlangProtocolReader<R> reader = new NioJetlangProtocolReader<R>(chan, msgHandler, ser.getReader(), topicReader,
                    () -> {
                        //handleTimeout});
                    });
            NioJetlangRemotingClientFactory.Id chanId = new NioJetlangRemotingClientFactory.Id(chan);
            this.channel = new NioJetlangSendFiber.ChannelState(chan, chanId, nioFiber);
            this.sendFiber.onNewSession(channel);
            return new TcpClientNioFiber.ConnectedClient() {
                @Override
                public boolean read(SocketChannel chan) {
                    return reader.read();
                }

                @Override
                public void onDisconnect() {
                    sendFiber.handleClose(channel);
                    JetlangClientFactory.this.channel = null;
                }
            };
        }

        public void publish(String topic, W msg) {
            NioJetlangSendFiber.ChannelState channel = this.channel;
            if (channel != null) {
                this.sendFiber.publishWithoutSubscriptionCheck(channel, topic, msg);
            }
        }

        public boolean sendRemoteSubscription(String topic, int msgType) {
            NioJetlangSendFiber.ChannelState channel = this.channel;
            return channel != null && this.sendFiber.writeSubscription(channel, topic, msgType, JetlangTcpClient.charset);
        }
    }
}
