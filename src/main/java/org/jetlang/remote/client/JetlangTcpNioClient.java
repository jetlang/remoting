package org.jetlang.remote.client;

import org.jetlang.channels.Channel;
import org.jetlang.channels.ChannelSubscription;
import org.jetlang.channels.Subscribable;
import org.jetlang.channels.Subscriber;
import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.DisposingExecutor;
import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.acceptor.NioJetlangProtocolReader;
import org.jetlang.remote.acceptor.NioJetlangRemotingClientFactory;
import org.jetlang.remote.acceptor.NioJetlangSendFiber;
import org.jetlang.remote.core.CloseableChannel;
import org.jetlang.remote.core.ErrorHandler;
import org.jetlang.remote.core.HeartbeatEvent;
import org.jetlang.remote.core.JetlangRemotingProtocol;
import org.jetlang.remote.core.MsgTypes;
import org.jetlang.remote.core.ReadTimeoutEvent;
import org.jetlang.remote.core.Serializer;
import org.jetlang.remote.core.TcpClientNioConfig;
import org.jetlang.remote.core.TcpClientNioFiber;
import org.jetlang.remote.core.TopicReader;

import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
public class JetlangTcpNioClient<R, W> {

    private final JetlangClientConfig config;
    private final Serializer<R, W> ser;
    private final ErrorHandler errorHandler;
    private final TcpClientNioFiber readFiber;

    private final CloseableChannel.Group channelsToClose = new CloseableChannel.Group();
    private final JetlangClientFactory<R, W> clientFactory;
    private final RemoteSubscriptions remoteSubscriptions;
    private final SocketConnector socketConnector;
    private final CountDownLatch logoutLatch = new CountDownLatch(1);

    private <T> CloseableChannel<T> channel() {
        return channelsToClose.newChannel();
    }

    private final Channel<ConnectEvent> Connected = channel();
    private final Channel<CloseEvent> Closed = channel();
    private final Channel<HeartbeatEvent> hb = channel();
    private final Channel<ReadTimeoutEvent> readTimeout = channel();

    private final AtomicBoolean lifecycleLock = new AtomicBoolean();
    private Stopper stopper = null;

    public JetlangTcpNioClient(SocketConnector socketConnector,
                               NioJetlangSendFiber<W> sendFiber,
                               JetlangClientConfig config,
                               Serializer<R, W> ser,
                               ErrorHandler errorHandler,
                               TcpClientNioFiber readFiber, TopicReader topicReader) {
        this.socketConnector = socketConnector;
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
                logoutLatch.countDown();
            }

            @Override
            public void onRequestReply(int reqId, String dataTopicVal, R readObject) {
                errorHandler.onException(new RuntimeException("Req/Reply not supported. " + dataTopicVal + " " + readObject));
            }
        };
        this.clientFactory = new JetlangClientFactory<>(ser, sendFiber, topicReader, msgHandler,
                remoteSubscriptions, Connected, readTimeout, Closed, config, socketConnector.getReadTimeoutInMs());
        this.config = config;
        this.ser = ser;
        this.errorHandler = errorHandler;
        this.readFiber = readFiber;
    }

    public Subscriber<ReadTimeoutEvent> getReadTimeoutChannel(){
        return readTimeout;
    }
    public Subscriber<ConnectEvent> getConnectChannel() {
        return Connected;
    }

    public Subscriber<CloseEvent> getCloseChannel() {
        return Closed;
    }

    public void start() {
        synchronized (lifecycleLock) {
            if(lifecycleLock.get()){
                throw new RuntimeException("Already started");
            }
            CountDownLatch disposeLatch = new CountDownLatch(1);
            TcpClientNioConfig.Default tcpConfig = new TcpClientNioConfig.Default(errorHandler, socketConnector::getInetSocketAddress,
                    clientFactory, config.getInitialConnectDelayInMs(),
                    config.getReconnectDelayInMs(), TimeUnit.MILLISECONDS) {
                @Override
                public void onDispose() {
                    channelsToClose.closeAndClear();
                    disposeLatch.countDown();
                }
            };
            tcpConfig.setReceiveBufferSize(socketConnector.getReceiveBufferSize());
            tcpConfig.setSendBufferSize(socketConnector.getSendBufferSize());
            stopper =  new Stopper(readFiber.connect(tcpConfig), disposeLatch, clientFactory, logoutLatch);
            lifecycleLock.set(true);
        }
    }

    public boolean stop(long timeToWaitForLogout, TimeUnit unit){
        synchronized (lifecycleLock){
            if(stopper != null){
                Stopper t = stopper;
                stopper = null;
                return t.attemptStop(timeToWaitForLogout, unit);
            }
        }
        return false;
    }

    public static class Stopper {

        private final Disposable connect;
        private final CountDownLatch disposeLatch;
        private final JetlangClientFactory<?, ?> clientFactory;
        private final CountDownLatch logout;

        public Stopper(Disposable connect, CountDownLatch disposeLatch, JetlangClientFactory<?, ?> clientFactory, CountDownLatch logout) {
            this.connect = connect;
            this.disposeLatch = disposeLatch;
            this.clientFactory = clientFactory;
            this.logout = logout;
        }

        public boolean attemptStop(long timeToWaitForLogout, TimeUnit unit) {
            boolean result = tryLogout(timeToWaitForLogout, unit);
            connect.dispose();
            try {
                return result && disposeLatch.await(timeToWaitForLogout, unit);
            } catch (InterruptedException e) {
                return false;
            }
        }

        private boolean tryLogout(long timeToWaitForLogout, TimeUnit unit) {
            if(clientFactory.sendLogoutIfConnected()){
                try {
                    return logout.await(timeToWaitForLogout, unit);
                } catch (InterruptedException e) {
                    return false;
                }
            }
            else {
                return false;
            }
        }
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
        private final TopicReader topicReader;
        private final JetlangRemotingProtocol.ClientHandler<R> msgHandler;
        private final RemoteSubscriptions remoteSubscriptions;
        private final Channel<ConnectEvent> connectEventChannel;
        private final Channel<ReadTimeoutEvent> timeout;
        private final Channel<CloseEvent> closed;
        private final JetlangClientConfig config;
        private final int readTimeoutInMs;

        private volatile NioJetlangSendFiber.ChannelState channel;

        public JetlangClientFactory(Serializer<R, W> ser, NioJetlangSendFiber<W> sendFiber,
                                    TopicReader topicReader, JetlangRemotingProtocol.ClientHandler<R> msgHandler,
                                    RemoteSubscriptions remoteSubscriptions,
                                    Channel<ConnectEvent> connectEventChannel,
                                    Channel<ReadTimeoutEvent> timeout,
                                    Channel<CloseEvent> closed,
                                    JetlangClientConfig config,
                                    int readTimeoutInMs) {
            this.ser = ser;
            this.sendFiber = sendFiber;
            this.topicReader = topicReader;
            this.msgHandler = msgHandler;
            this.remoteSubscriptions = remoteSubscriptions;
            this.connectEventChannel = connectEventChannel;
            this.timeout = timeout;
            this.closed = closed;
            this.config = config;
            this.readTimeoutInMs = readTimeoutInMs;
        }

        @Override
        public TcpClientNioFiber.ConnectedClient createClientOnConnect(SocketChannel chan, NioFiber nioFiber, TcpClientNioFiber.Writer writer) {
            //JetlangNioSession<R, W> session = new JetlangNioSession<R, W>(nioFiber, chan, sendFiber, new NioJetlangRemotingClientFactory.Id(chan), errorHandler);
            NioJetlangProtocolReader<R> reader = new NioJetlangProtocolReader<R>(chan, msgHandler, ser.getReader(), topicReader,
                    () -> {
                        timeout.publish(new ReadTimeoutEvent());
                    });
            NioJetlangRemotingClientFactory.Id chanId = new NioJetlangRemotingClientFactory.Id(chan);
            NioJetlangSendFiber.ChannelState newState = new NioJetlangSendFiber.ChannelState(chan, chanId, nioFiber);
            this.sendFiber.onNewSession(newState);
            this.remoteSubscriptions.onConnect();
            this.connectEventChannel.publish(new ConnectEvent());

            Disposable hbSched = sendFiber.scheduleHeartbeat(newState,
                    config.getHeartbeatIntervalInMs(), config.getHeartbeatIntervalInMs(), TimeUnit.MILLISECONDS);
            Disposable readTimeout = nioFiber.scheduleAtFixedRate(() -> {
                reader.checkForReadTimeout(readTimeoutInMs);
            }, readTimeoutInMs, readTimeoutInMs, TimeUnit.MILLISECONDS);
            this.channel = newState;
            return new TcpClientNioFiber.ConnectedClient() {
                @Override
                public boolean read(SocketChannel chan) {
                    return reader.read();
                }

                @Override
                public void onDisconnect() {
                    readTimeout.dispose();
                    hbSched.dispose();
                    sendFiber.handleClose(newState);
                    JetlangClientFactory.this.channel = null;
                    //TODO distinquish disconnects
                    closed.publish(new CloseEvent.GracefulDisconnect());
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

        public boolean sendLogoutIfConnected() {
            NioJetlangSendFiber.ChannelState channel = this.channel;
            if(channel != null){
                this.sendFiber.sendIntAsByte(channel, MsgTypes.Disconnect);
                return true;
            }
            return false;
        }
    }
}
