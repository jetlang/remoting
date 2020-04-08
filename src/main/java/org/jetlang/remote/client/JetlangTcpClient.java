package org.jetlang.remote.client;

import org.jetlang.channels.Channel;
import org.jetlang.channels.ChannelSubscription;
import org.jetlang.channels.MemoryChannel;
import org.jetlang.channels.Subscribable;
import org.jetlang.channels.Subscriber;
import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.DisposingExecutor;
import org.jetlang.fibers.Fiber;
import org.jetlang.remote.acceptor.MessageStreamWriter;
import org.jetlang.remote.core.CloseableChannel;
import org.jetlang.remote.core.ErrorHandler;
import org.jetlang.remote.core.HeartbeatEvent;
import org.jetlang.remote.core.JetlangRemotingInputStream;
import org.jetlang.remote.core.JetlangRemotingProtocol;
import org.jetlang.remote.core.MsgTypes;
import org.jetlang.remote.core.RawMsg;
import org.jetlang.remote.core.RawMsgHandler;
import org.jetlang.remote.core.ReadTimeoutEvent;
import org.jetlang.remote.core.Serializer;
import org.jetlang.remote.core.SocketMessageStreamWriter;
import org.jetlang.remote.core.TcpSocket;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jetlang.remote.core.RawMsgHandler.NULL_RAW_MSG_HANDLER;

/**
 *
 */
public class JetlangTcpClient implements JetlangClient {

    private MessageStreamWriter socket;
    private final Fiber sendFiber;
    private final JetlangClientConfig config;
    private final Serializer ser;
    private final ErrorHandler errorHandler;
    private final RawMsgHandler rawMsgHandler;
    private static final Charset charset = Charset.forName("US-ASCII");
    private final SocketConnector socketConnector;
    private Disposable pendingConnect;
    private final CloseableChannel.Group channelsToClose = new CloseableChannel.Group();
    private final Map<String, RemoteSubscription> remoteSubscriptions = new LinkedHashMap<String, RemoteSubscription>();

    private <T> CloseableChannel<T> channel() {
        return channelsToClose.add(new MemoryChannel<T>());
    }

    private final Channel<ConnectEvent> Connected = channel();
    private final Channel<CloseEvent> Closed = channel();
    private final Channel<ReadTimeoutEvent> ReadTimeout = channel();
    private final Channel<DeadMessageEvent> DeadMessage = channel();

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CountDownLatch logoutLatch = new CountDownLatch(1);
    private Disposable hbSchedule;
    private final Channel<HeartbeatEvent> Heartbeat = channel();
    private AtomicInteger reqId = new AtomicInteger();
    private final Map<Integer, Req> pendingRequests = Collections.synchronizedMap(new HashMap<Integer, Req>());

    private final SocketWriter socketWriter = new SocketWriter() {
        public <T> boolean send(final String topic, final T msg) {
            if (socket != null) {
                try {
                    socket.write(topic, msg);
                    return true;
                } catch (IOException e) {
                    DeadMessage.publish(new DeadMessageEvent(topic, msg));
                    handleDisconnect(new CloseEvent.WriteException(e));
                }
            } else {
                DeadMessage.publish(new DeadMessageEvent(topic, msg));
            }
            return false;
        }
    };

    public JetlangTcpClient(SocketConnector socketConnector,
                            Fiber sendFiber,
                            JetlangClientConfig config,
                            Serializer ser,
                            ErrorHandler errorHandler) {
        this(socketConnector, sendFiber, config, ser, errorHandler, NULL_RAW_MSG_HANDLER);
    }

    public JetlangTcpClient(SocketConnector socketConnector,
                            Fiber sendFiber,
                            JetlangClientConfig config,
                            Serializer ser,
                            ErrorHandler errorHandler,
                            RawMsgHandler rawMsgHandler) {
        this.socketConnector = socketConnector;
        this.sendFiber = sendFiber;
        this.config = config;
        this.ser = ser;
        this.errorHandler = errorHandler;
        this.rawMsgHandler = rawMsgHandler;
    }

    private class RemoteSubscription<T> {
        private final CloseableChannel<T> channel = channel();
        private final String topic;
        private boolean subscriptionSent = false;

        public RemoteSubscription(String topic) {
            this.topic = topic;
        }

        public Disposable subscribe(Subscribable<T> callback) {
            final Disposable channelDisposable = channel.subscribe(callback);

            sendFiber.execute(new Runnable() {
                public void run() {
                    if (!subscriptionSent) {
                        subscriptionSent = sendSubscription(topic, MsgTypes.Subscription);
                    }
                }
            });

            return new Disposable() {
                public void dispose() {
                    channelDisposable.dispose();

                    sendFiber.execute(new Runnable() {
                        public void run() {
                            unsubscribeIfNecessary();
                        }
                    });
                }
            };
        }

        private void unsubscribeIfNecessary() {
            synchronized (remoteSubscriptions) {
                if (channel.subscriptionCount() == 0 && !channel.isClosed()) {
                    channel.close();
                    channelsToClose.remove(channel);
                    if (subscriptionSent) {
                        sendSubscription(topic, MsgTypes.Unsubscribe);
                    }

                    remoteSubscriptions.remove(topic);
                }
            }
        }

        public void publish(T object) {
            channel.publish(object);
        }

        public void onConnect() {
            subscriptionSent = sendSubscription(topic, MsgTypes.Subscription);
        }
    }

    public <T> Disposable subscribe(final String subject, Subscribable<T> callback) {
        synchronized (remoteSubscriptions) {
            final RemoteSubscription<T> remoteSubscription;
            if (remoteSubscriptions.containsKey(subject)) {
                //noinspection unchecked
                remoteSubscription = (RemoteSubscription<T>) remoteSubscriptions.get(subject);
            } else {
                remoteSubscription = new RemoteSubscription<T>(subject);
                remoteSubscriptions.put(subject, remoteSubscription);
            }
            return remoteSubscription.subscribe(callback);
        }
    }

    private void publishData(String topic, Object object) {
        RemoteSubscription channel;
        synchronized (remoteSubscriptions) {
            channel = remoteSubscriptions.get(topic);
        }
        if (channel != null) {
            //noinspection unchecked
            channel.publish(object);
        }
    }

    private void publishReply(int id, Object reply) {
        Req r = pendingRequests.remove(id);
        if (r != null) {
            //noinspection unchecked
            r.onReply(reply);
        }
    }

    private boolean sendSubscription(String subject, int msgType) {
        if (socket != null) {
            try {
                socket.writeSubscription(msgType, subject, charset);
                return true;
            } catch (IOException e) {
                handleDisconnect(new CloseEvent.WriteException(e));
            }
        }
        return false;
    }

    private void closeIfNeeded(CloseEvent closeCause) {
        if (socket != null) {
            socket.tryClose();
            socket = null;
            if (hbSchedule != null) {
                hbSchedule.dispose();
            }
            if (closed.get()) {
                this.Closed.publish(new CloseEvent.GracefulDisconnect());
            } else {
                this.Closed.publish(closeCause);
            }
        }
    }

    private final Runnable connect = new Runnable() {
        public void run() {
            try {
                Socket socket = socketConnector.connect();
                handleConnect(socket);
            } catch (Exception failed) {
                errorHandler.onException(failed);
                socket = null;
            }
        }
    };

    private final Runnable hb = new Runnable() {
        public void run() {
            try {
                if (socket != null) {
                    socket.writeByteAsInt(MsgTypes.Heartbeat);
                }
            } catch (IOException exc) {
                handleDisconnect(new CloseEvent.WriteException(exc));
            }
        }
    };

    private final Runnable onReadTimeout = new Runnable() {
        public void run() {
            ReadTimeout.publish(new ReadTimeoutEvent());
        }
    };

    private void handleConnect(Socket newSocket) throws IOException {
        this.pendingConnect.dispose();
        this.pendingConnect = null;
        this.socket = new SocketMessageStreamWriter(new TcpSocket(newSocket, errorHandler), charset, ser.getWriter());
        synchronized (remoteSubscriptions) {
            for (RemoteSubscription subscription : remoteSubscriptions.values()) {
                subscription.onConnect();
            }
        }
        final InputStream stream = newSocket.getInputStream();
        final Runnable reader = new Runnable() {
            public void run() {
                final JetlangRemotingProtocol protocol = new JetlangRemotingProtocol(protocolHandler, ser.getReader(), charset, rawMsgHandler.enabled());
                final JetlangRemotingInputStream inputStream = new JetlangRemotingInputStream(stream, protocol, onReadTimeout);
                try {
                    Connected.publish(new ConnectEvent());
                    while (inputStream.readFromStream()) {
                    }
                } catch (IOException failed) {
                    handleReadExceptionOnSendFiber(failed);
                }
            }
        };
        Thread readThread = new Thread(reader, getClass().getSimpleName());
        readThread.start();
        if (config.getHeartbeatIntervalInMs() > 0) {
            hbSchedule = sendFiber.scheduleWithFixedDelay(hb, config.getHeartbeatIntervalInMs(), config.getHeartbeatIntervalInMs(), TimeUnit.MILLISECONDS);
        }
    }

    private final JetlangRemotingProtocol.Handler protocolHandler = new JetlangRemotingProtocol.Handler() {
        public void onMessage(String dataTopicVal, Object readObject) {
            publishData(dataTopicVal, readObject);
        }

        @Override
        public void onRawMsg(RawMsg rawMsg) {
            rawMsgHandler.onRawMsg(rawMsg);
        }

        public void onSubscriptionRequest(String val) {
            errorHandler.onException(new IOException("SubscriptionNotSupported: " + val));
        }

        public void onRequest(int reqId, String dataTopicVal, Object readObject) {
            errorHandler.onException(new IOException("RequestNotSupported: " + dataTopicVal + " val: " + readObject));
        }

        public void onUnsubscribeRequest(String val) {
            errorHandler.onException(new IOException("UnsubscribeNotSupported: " + val));
        }

        @Override
        public void onHandlerException(Exception failed) {
            errorHandler.onException(failed);
        }

        public void onHb() {
            Heartbeat.publish(new HeartbeatEvent());
        }

        public void onLogout() {
            logoutLatch.countDown();
        }

        public void onUnknownMessage(int read) {
            errorHandler.onException(new IOException(read + " not supported"));
        }

        public void onRequestReply(int reqId, String dataTopicVal, Object readObject) {
            publishReply(reqId, readObject);
        }
    };

    private void handleReadExceptionOnSendFiber(final IOException e) {
        Runnable exec = new Runnable() {
            public void run() {
                handleDisconnect(new CloseEvent.ReadException(e));
            }
        };
        sendFiber.execute(exec);
    }

    public void start() {
        pendingConnect = sendFiber.scheduleWithFixedDelay(connect, config.getInitialConnectDelayInMs(), config.getReconnectDelayInMs(), TimeUnit.MILLISECONDS);
        sendFiber.start();
    }

    private void handleDisconnect(CloseEvent event) {
        closeIfNeeded(event);
        if (pendingConnect == null && !closed.get()) {
            //should use fixed rate but don't want to introduce a dependency on that method.
            if (config.getReconnectDelayInMs() > 0) {
                pendingConnect = sendFiber.scheduleWithFixedDelay(connect, config.getReconnectDelayInMs(), config.getReconnectDelayInMs(), TimeUnit.MILLISECONDS);
            }
        }
    }

    public <T> Disposable subscribe(String topic, DisposingExecutor clientFiber, Callback<T> cb) {
        return subscribe(topic, new ChannelSubscription<T>(clientFiber, cb));
    }

    public LogoutResult close(final boolean sendLogoutIfStillConnected) {
        final CountDownLatch closedLatch = new CountDownLatch(1);
        final AtomicBoolean logoutLatchComplete = new AtomicBoolean(false);
        if (closed.compareAndSet(false, true)) {
            Runnable disconnect = new Runnable() {
                public void run() {
                    if (socket != null && sendLogoutIfStillConnected) {
                        try {
                            socket.writeByteAsInt(MsgTypes.Disconnect);
                            boolean result = logoutLatch.await(config.getLogoutLatchTimeout(), config.getLogoutLatchTimeoutUnit());
                            logoutLatchComplete.set(result);
                        } catch (Exception e) {
                            errorHandler.onException(e);
                        }

                    }
                    if (pendingConnect != null) {
                        pendingConnect.dispose();
                    }
                    closeIfNeeded(new CloseEvent.GracefulDisconnect());
                    closedLatch.countDown();
                    sendFiber.dispose();
                    channelsToClose.closeAndClear();
                }
            };
            sendFiber.execute(disconnect);
            return new LogoutResult(logoutLatchComplete, closedLatch);
        }
        throw new RuntimeException("Already closed.");
    }

    private class Req<T> {
        final DisposingExecutor fiber;
        final Callback<T> cb;
        private final AtomicBoolean disposed;

        public Req(DisposingExecutor fiber, Callback<T> cb, AtomicBoolean disposed) {
            this.fiber = fiber;
            this.cb = cb;
            this.disposed = disposed;
        }

        public void onReply(final T reply) {
            if (!disposed.get()) {
                Runnable run = new Runnable() {
                    public void run() {
                        if (disposed.compareAndSet(false, true)) {
                            cb.onMessage(reply);
                        }
                    }
                };
                fiber.execute(run);
            }
        }
    }

    public <T> Disposable request(final String reqTopic,
                                  final Object req,
                                  final DisposingExecutor executor, final Callback<T> callback,
                                  final Callback<TimeoutControls> timeoutRunnable, int timeout, TimeUnit timeUnit) {
        final AtomicBoolean disposed = new AtomicBoolean(false);
        final int id = reqId.incrementAndGet();
        Runnable reqSend = new Runnable() {
            public void run() {
                if (!disposed.get()) {
                    if (socket != null) {
                        pendingRequests.put(id, new Req<T>(executor, callback, disposed));
                        try {
                            socket.writeRequest(id, reqTopic, req);
                        } catch (IOException e) {
                            pendingRequests.remove(id);
                            handleDisconnect(new CloseEvent.WriteException(e));
                        }
                    }
                }
            }
        };
        sendFiber.execute(reqSend);
        if (timeout > 0 && callback != null) {
            Runnable onTimeout = new Runnable() {
                public void run() {
                    if (!disposed.get()) {
                        TimeoutControls controls = new TimeoutControls() {
                            public void cancelRequest() {
                                disposed.set(true);
                                pendingRequests.remove(id);
                            }
                        };
                        timeoutRunnable.onMessage(controls);
                    }
                }
            };
            final Disposable disposable = sendFiber.schedule(onTimeout, timeout, timeUnit);
            return new Disposable() {
                public void dispose() {
                    disposed.set(true);
                    disposable.dispose();
                }
            };
        } else {
            return new Disposable() {
                public void dispose() {
                    disposed.set(true);
                }
            };
        }
    }

    public Subscriber<CloseEvent> getCloseChannel() {
        return Closed;
    }

    public Subscriber<ReadTimeoutEvent> getReadTimeoutChannel() {
        return ReadTimeout;
    }

    public Subscriber<ConnectEvent> getConnectChannel() {
        return Connected;
    }

    public Subscriber<DeadMessageEvent> getDeadMessageChannel() {
        return DeadMessage;
    }

    public <T> void publish(String topic, T msg) {
        publish(topic, msg, null);
    }

    public <T> void publish(final String topic, final T msg, final Runnable onSend) {
        Runnable r = new Runnable() {
            public void run() {
                if (socketWriter.send(topic, msg)) {
                    if (onSend != null)
                        onSend.run();
                }
            }
        };
        sendFiber.execute(r);
    }

    public void execOnSendThread(final Callback<SocketWriter> cb) {
        Runnable r = new Runnable() {
            public void run() {
                cb.onMessage(socketWriter);
            }
        };
        sendFiber.execute(r);
    }
}
