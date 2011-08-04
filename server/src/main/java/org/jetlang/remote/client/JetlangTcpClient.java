package org.jetlang.remote.client;

import org.jetlang.remote.acceptor.MessageStreamWriter;
import org.jetlang.channels.*;
import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.DisposingExecutor;
import org.jetlang.fibers.Fiber;
import org.jetlang.remote.core.*;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 */
public class JetlangTcpClient implements JetlangClient {

    private MessageStreamWriter socket;
    private final Fiber sendFiber;
    private final JetlangClientConfig config;
    private final Serializer ser;
    private final ErrorHandler errorHandler;
    private static final Charset charset = Charset.forName("US-ASCII");
    private final SocketConnector socketConnector;
    private Disposable pendingConnect;
    private final CloseableChannel.Group channelsToClose = new CloseableChannel.Group();
    private final Map<String, CloseableChannel> channels = new LinkedHashMap<String, CloseableChannel>();

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

    public JetlangTcpClient(SocketConnector socketConnector,
                            Fiber sendFiber,
                            JetlangClientConfig config,
                            Serializer ser,
                            ErrorHandler errorHandler) {
        this.socketConnector = socketConnector;
        this.sendFiber = sendFiber;
        this.config = config;
        this.ser = ser;
        this.errorHandler = errorHandler;
    }

    public <T> Disposable subscribe(final String subject, Subscribable<T> callback) {
        CloseableChannel<T> channel;
        synchronized (channels) {
            //noinspection unchecked
            channel = (CloseableChannel<T>) channels.get(subject);
            if (channel == null) {
                channel = channel();
                channels.put(subject, channel);
            } else {
                throw new RuntimeException("Subscription Already Exists: " + subject);
            }
        }
        final Disposable unSub = channel.subscribe(callback);
        Runnable sub = new Runnable() {
            public void run() {
                sendSubscription(subject, MsgTypes.Subscription);
            }
        };
        sendFiber.execute(sub);
        final CloseableChannel<T> toRemove = channel;
        return new Disposable() {
            public void dispose() {
                //unsubscribe immediately
                unSub.dispose();
                Runnable sendUnsub = new Runnable() {

                    public void run() {
                        synchronized (channels) {
                            channels.remove(subject);
                            toRemove.close();
                            channelsToClose.remove(toRemove);
                            sendSubscription(subject, MsgTypes.Unsubscribe);
                        }
                    }
                };
                sendFiber.execute(sendUnsub);
            }
        };
    }

    private void publishData(String topic, Object object) {
        Channel channel;
        synchronized (channels) {
            channel = channels.get(topic);
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

    private void sendSubscription(String subject, int msgType) {
        if (socket != null) {
            try {
                socket.writeSubscription(msgType, subject, charset);
            } catch (IOException e) {
                handleDisconnect(new CloseEvent.WriteException(e));
            }
        }
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
        for (String subscription : channels.keySet()) {
            sendSubscription(subscription, MsgTypes.Subscription);
        }
        final StreamReader stream = new StreamReader(newSocket.getInputStream(), charset, ser.getReader(), onReadTimeout);
        Runnable reader = new Runnable() {

            public void run() {
                try {
                    Connected.publish(new ConnectEvent());
                    while (read(stream)) {
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

    private boolean read(StreamReader stream) throws IOException {
        try {
            int msg = stream.readByteAsInt();
            switch (msg) {
                case MsgTypes.Disconnect://logout
                    logoutLatch.countDown();
                    return false;
                case MsgTypes.Heartbeat:
                    this.Heartbeat.publish(new HeartbeatEvent());
                    return true;
                case MsgTypes.Data:
                    String topic = stream.readStringWithSize();
                    Object object = stream.readObjectWithSize(topic);
                    publishData(topic, object);
                    return true;
                case MsgTypes.DataReply:
                    int reqId = stream.readInt();
                    String reqTopic = stream.readStringWithSize();
                    Object reply = stream.readObjectWithSize(reqTopic);
                    publishReply(reqId, reply);
                    return true;
                default:
                    throw new IOException("Unknown msg: " + msg);
            }
        } catch (SocketTimeoutException ignored) {
            this.ReadTimeout.publish(new ReadTimeoutEvent());
            return true;
        }
    }

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

    public CountDownLatch close(final boolean sendLogoutIfStillConnected) {
        final CountDownLatch closedLatch = new CountDownLatch(1);
        if (closed.compareAndSet(false, true)) {
            Runnable disconnect = new Runnable() {
                public void run() {
                    if (socket != null && sendLogoutIfStillConnected) {
                        try {
                            socket.writeByteAsInt(MsgTypes.Disconnect);
                            logoutLatch.await(1, TimeUnit.SECONDS);
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
            return closedLatch;
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
                if (socket != null) {
                    try {
                        socket.write(topic, msg);
                        if (onSend != null)
                            onSend.run();
                    } catch (IOException e) {
                        DeadMessage.publish(new DeadMessageEvent(topic, msg));
                        handleDisconnect(new CloseEvent.WriteException(e));
                    }
                } else {
                    DeadMessage.publish(new DeadMessageEvent(topic, msg));
                }
            }
        };
        sendFiber.execute(r);
    }
}
