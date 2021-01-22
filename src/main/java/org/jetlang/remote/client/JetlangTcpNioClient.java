package org.jetlang.remote.client;

import org.jetlang.channels.Channel;
import org.jetlang.channels.ChannelSubscription;
import org.jetlang.channels.MemoryChannel;
import org.jetlang.channels.Publisher;
import org.jetlang.channels.Subscribable;
import org.jetlang.channels.Subscriber;
import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.DisposingExecutor;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.acceptor.NioJetlangProtocolReader;
import org.jetlang.remote.core.CloseableChannel;
import org.jetlang.remote.core.ErrorHandler;
import org.jetlang.remote.core.HeartbeatEvent;
import org.jetlang.remote.core.JetlangRemotingProtocol;
import org.jetlang.remote.core.MsgTypes;
import org.jetlang.remote.core.ObjectByteWriter;
import org.jetlang.remote.core.ReadTimeoutEvent;
import org.jetlang.remote.core.Serializer;
import org.jetlang.remote.core.TcpClientNioConfig;
import org.jetlang.remote.core.TcpClientNioFiber;
import org.jetlang.remote.core.TopicReader;
import org.jetlang.web.SendResult;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
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

    interface Sender<W> {

        ConnectedChannel<W> connect(SocketChannel chan, NioFiber nioFiber, TcpClientNioFiber.Writer writer, ObjectByteWriter<W> objWriter, Charset charset, Subscriptions subscriptions);

        SendResult publish(String topic, W msg);

        void sendSubscription(String subject, int subscriptionType);

        SendResult publish(JetlangBuffer buffer);

        SendResult publishMsgType(int msgType);
    }

    private static class Disconnected<T> implements Sender<T> {

        @Override
        public ConnectedChannel<T> connect(SocketChannel chan, NioFiber nioFiber, TcpClientNioFiber.Writer writer, ObjectByteWriter<T> objWriter, Charset charset, Subscriptions subscriptions) {
            ConnectedChannel<T> connectedChannel = new ConnectedChannel<>(writer, objWriter, charset);
            subscriptions.onConnect(connectedChannel);
            return connectedChannel;
        }

        @Override
        public SendResult publish(String topic, T msg) {
            return SendResult.Closed;
        }

        @Override
        public SendResult publish(JetlangBuffer buffer) {
            buffer.buffer.clear();
            return SendResult.Closed;
        }

        @Override
        public SendResult publishMsgType(int msgType) {
            return SendResult.Closed;
        }

        @Override
        public void sendSubscription(String subject, int subType) {

        }
    }

    private static class ConnectedChannel<T> implements Sender<T> {

        private final TcpClientNioFiber.Writer writer;
        private final ObjectByteWriter<T> objWriter;
        private final Charset charset;
        private final JetlangBuffer directMemoryBuffer;

        public ConnectedChannel(TcpClientNioFiber.Writer writer, ObjectByteWriter<T> objWriter, Charset charset) {
            this.writer = writer;
            this.objWriter = objWriter;
            this.charset = charset;
            this.directMemoryBuffer = new JetlangBuffer(128);
        }

        @Override
        public ConnectedChannel<T> connect(SocketChannel chan, NioFiber nioFiber, TcpClientNioFiber.Writer writer, ObjectByteWriter objWriter, Charset charset, Subscriptions subscriptions) {
            throw new RuntimeException("should not connect");
        }

        @Override
        public SendResult publish(String topic, T msg) {
            synchronized (directMemoryBuffer) {
                directMemoryBuffer.appendMsg(topic, msg, objWriter, charset);
                return flush();
            }
        }

        @Override
        public SendResult publishMsgType(int msgType) {
            synchronized (directMemoryBuffer){
                directMemoryBuffer.appendIntAsByte(msgType);
                return flush();
            }
        }

        private SendResult flush() {
            final ByteBuffer buffer = directMemoryBuffer.buffer;
            buffer.flip();
            return writer.write(buffer);
        }

        @Override
        public SendResult publish(JetlangBuffer buffer) {
            return writer.write(buffer.buffer);
        }

        @Override
        public void sendSubscription(String subject, int msgType) {
            synchronized (directMemoryBuffer){
                directMemoryBuffer.appendSubscription(subject, msgType, charset);
                flush();
            }
        }

        public Disconnected<T> onDisconnect() {
            return new Disconnected<>();
        }
    }

    private static class Sub<T> {

        private final String subject;
        private final CloseableChannel<T> channel;
        private Sender sender;

        public Sub(String subject, CloseableChannel<T> channel) {
            this.subject = subject;
            this.channel = channel;
        }

        public Disposable subscribe(Subscribable<T> tChannelSubscription, Sender channel) {
            boolean newSub = this.channel.subscriptionCount() == 0;
            Disposable disposable = this.channel.subscribe(tChannelSubscription);
            if(newSub){
                onConnect(channel);
            }
            return disposable;
        }

        private void onConnect(Sender channel) {
            this.sender = channel;
            if(this.channel.subscriptionCount() > 0) {
                sender.sendSubscription(subject, MsgTypes.Subscription);
            }
        }

        public void onUnsubscription(Disposable unsub) {
            unsub.dispose();
            if(channel.subscriptionCount() == 0){
                sender.sendSubscription(subject, MsgTypes.Unsubscribe);
            }
        }
    }

    private static class Subscriptions implements JetlangRemotingProtocol.MessageDispatcher {

        private final Map<String, Sub> remoteSubscriptions = new LinkedHashMap<String, Sub>();

        private final CloseableChannel.Group channelsToClose;

        public Subscriptions(CloseableChannel.Group channelsToClose) {
            this.channelsToClose = channelsToClose;
        }

        @Override
        public <R> void dispatch(String dataTopicVal, R readObject) {
            Sub<R> channel;
            synchronized (remoteSubscriptions) {
                channel = (Sub<R>) remoteSubscriptions.get(dataTopicVal);
            }
            if (channel != null) {
                channel.channel.publish(readObject);
            }

        }

        public <T> Disposable subscribe(String subject, Subscribable<T> tChannelSubscription, Sender channel) {
            synchronized (remoteSubscriptions) {
                Sub<T> chan = (Sub<T>) remoteSubscriptions.get(subject);
                if (chan == null) {
                    chan = new Sub<>(subject, channelsToClose.add(new MemoryChannel<>()));
                    remoteSubscriptions.put(subject, chan);
                }
                Disposable unsub = chan.subscribe(tChannelSubscription, channel);
                final Sub<T> finalChan = chan;
                return ()->{
                    synchronized (remoteSubscriptions){
                        finalChan.onUnsubscription(unsub);
                    }
                };
            }
        }

        public void onConnect(ConnectedChannel connectedChannel) {
            synchronized (remoteSubscriptions){
                for (Sub<?> value : remoteSubscriptions.values()) {
                    value.onConnect(connectedChannel);
                }
            }
        }
    }

    public JetlangTcpNioClient(SocketConnector socketConnector,
                               JetlangClientConfig config,
                               Serializer<R, W> ser,
                               ErrorHandler errorHandler,
                               TcpClientNioFiber readFiber, TopicReader topicReader) {
        this.socketConnector = socketConnector;
        this.clientFactory = new JetlangClientFactory<>(ser, topicReader,
                Connected, readTimeout, Closed, config,
                socketConnector.getReadTimeoutInMs(), channelsToClose, logoutLatch, errorHandler, hb);
        this.config = config;
        this.ser = ser;
        this.errorHandler = errorHandler;
        this.readFiber = readFiber;
    }

    public Subscriber<ReadTimeoutEvent> getReadTimeoutChannel() {
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
            if (lifecycleLock.get()) {
                throw new RuntimeException("Already started");
            }
            CountDownLatch disposeLatch = new CountDownLatch(1);
            TcpClientNioConfig.Default tcpConfig = new TcpClientNioConfig.Default(errorHandler, socketConnector::getInetSocketAddress,
                    clientFactory, config.getReconnectDelayInMs(),
                    config.getReconnectDelayInMs(), TimeUnit.MILLISECONDS) {
                @Override
                public void onDispose() {
                    channelsToClose.closeAndClear();
                    disposeLatch.countDown();
                }
            };
            tcpConfig.setReceiveBufferSize(socketConnector.getReceiveBufferSize());
            tcpConfig.setSendBufferSize(socketConnector.getSendBufferSize());
            stopper = new Stopper(readFiber.connect(tcpConfig), disposeLatch, clientFactory, logoutLatch);
            lifecycleLock.set(true);
        }
    }

    public boolean stop(long timeToWaitForLogout, TimeUnit unit) {
        synchronized (lifecycleLock) {
            if (stopper != null) {
                Stopper t = stopper;
                stopper = null;
                return t.attemptStop(timeToWaitForLogout, unit);
            }
        }
        return false;
    }

    public void stopNow() {
        synchronized (lifecycleLock) {
            if (stopper != null) {
                Stopper t = stopper;
                stopper = null;
                t.stopNow();
            }
        }
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

        public void stopNow() {
            clientFactory.sendLogoutIfConnected();
            connect.dispose();
        }

        private boolean tryLogout(long timeToWaitForLogout, TimeUnit unit) {
            if (clientFactory.sendLogoutIfConnected()) {
                try {
                    return logout.await(timeToWaitForLogout, unit);
                } catch (InterruptedException e) {
                    return false;
                }
            } else {
                return false;
            }
        }

    }


    public <T extends R> Disposable subscribeOnReadThread(String topic, Callback<T> msg) {
        return subscribe(topic, new Subscribable<T>() {
            private final SynchronousDisposingExecutor unused = new SynchronousDisposingExecutor();

            @Override
            public void onMessage(T t) {
                msg.onMessage(t);
            }

            @Override
            public DisposingExecutor getQueue() {
                return unused;
            }
        });
    }

    public <T extends R> Disposable subscribe(String topic, DisposingExecutor executor, Callback<T> msg) {
        return subscribe(topic, new ChannelSubscription<>(executor, msg));
    }

    public <T extends R> Disposable subscribe(String topic, Subscribable<T> tChannelSubscription) {
        return clientFactory.subscribe(topic, tChannelSubscription);
    }

    public SendResult publish(String topic, W msg) {
        return clientFactory.publish(topic, msg);
    }

    public SendResult publish(JetlangBuffer buffer){
        return clientFactory.publish(buffer);
    }

    private static class JetlangClientFactory<R, W> implements TcpClientNioConfig.ClientFactory {

        private final Serializer<R, W> ser;
        private final TopicReader topicReader;
        private final Channel<ConnectEvent> connectEventChannel;
        private final Channel<ReadTimeoutEvent> timeout;
        private final Channel<CloseEvent> closed;
        private final JetlangClientConfig config;
        private final int readTimeoutInMs;
        private final CountDownLatch logoutLatch;
        private final ErrorHandler errorHandler;
        private final Subscriptions subscriptions;
        private final Publisher<HeartbeatEvent> hb;

        private volatile Sender<W> channel;

        public JetlangClientFactory(Serializer<R, W> ser,
                                    TopicReader topicReader,
                                    Channel<ConnectEvent> connectEventChannel,
                                    Channel<ReadTimeoutEvent> timeout,
                                    Channel<CloseEvent> closed,
                                    JetlangClientConfig config,
                                    int readTimeoutInMs,
                                    CloseableChannel.Group channelsToClose,
                                    CountDownLatch logoutLatch,
                                    ErrorHandler errorHandler,
                                    Publisher<HeartbeatEvent> hb) {
            this.ser = ser;
            this.subscriptions = new Subscriptions(channelsToClose);
            this.hb = hb;
            this.channel = new Disconnected<>();
            this.topicReader = topicReader;
            this.connectEventChannel = connectEventChannel;
            this.timeout = timeout;
            this.closed = closed;
            this.config = config;
            this.readTimeoutInMs = readTimeoutInMs;
            this.logoutLatch = logoutLatch;
            this.errorHandler = errorHandler;
        }

        @Override
        public TcpClientNioFiber.ConnectedClient createClientOnConnect(SocketChannel chan, NioFiber nioFiber, TcpClientNioFiber.Writer writer) {
            JetlangRemotingProtocol.ClientHandler<R> msgHandler = new JetlangRemotingProtocol.ClientHandler<R>(errorHandler, hb, subscriptions) {
                @Override
                public void onLogout() {
                    logoutLatch.countDown();
                }

                @Override
                public void onRequestReply(int reqId, String dataTopicVal, R readObject) {
                    errorHandler.onException(new RuntimeException("Req/Reply not supported. " + dataTopicVal + " " + readObject));
                }
            };
            NioJetlangProtocolReader<R> reader = new NioJetlangProtocolReader<R>(chan, msgHandler, ser.getReader(), topicReader,
                    () -> timeout.publish(new ReadTimeoutEvent()));
            ConnectedChannel<W> connect = channel.connect(chan, nioFiber, writer, ser.getWriter(), JetlangTcpClient.charset, subscriptions);
            this.channel = connect;
            this.connectEventChannel.publish(new ConnectEvent());

            Runnable sendHb = ()->{
                connect.publishMsgType(MsgTypes.Heartbeat);
            };
            Disposable hbSched = nioFiber.scheduleAtFixedRate(sendHb,
                    config.getHeartbeatIntervalInMs(), config.getHeartbeatIntervalInMs(), TimeUnit.MILLISECONDS);
            Disposable readTimeout = nioFiber.scheduleAtFixedRate(() -> {
                reader.checkForReadTimeout(readTimeoutInMs);
            }, readTimeoutInMs, readTimeoutInMs, TimeUnit.MILLISECONDS);

            return new TcpClientNioFiber.ConnectedClient() {
                @Override
                public boolean read(SocketChannel chan) {
                    return reader.read();
                }

                @Override
                public void onDisconnect() {
                    readTimeout.dispose();
                    hbSched.dispose();
                    JetlangClientFactory.this.channel = connect.onDisconnect();
                    //TODO distinquish disconnects
                    closed.publish(new CloseEvent.GracefulDisconnect());
                }
            };
        }

        public SendResult publish(String topic, W msg) {
            return this.channel.publish(topic, msg);
        }

        public boolean sendLogoutIfConnected() {
            return channel.publishMsgType(MsgTypes.Disconnect).equals(SendResult.SUCCESS);
        }

        public <T extends R> Disposable subscribe(String topic, Subscribable<T> tChannelSubscription) {
            return subscriptions.subscribe(topic, tChannelSubscription, channel);
        }

        public SendResult publish(JetlangBuffer buffer) {
            return channel.publish(buffer);
        }
    }
}
