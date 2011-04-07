package com.jetlang.remote.client;

import com.jetlang.remote.core.*;
import com.jetlang.remote.server.MessageStreamWriter;
import org.jetlang.channels.*;
import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.core.DisposingExecutor;
import org.jetlang.fibers.Fiber;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final Map<String, Channel> channels = new LinkedHashMap<String, Channel>();

    public final Channel<ConnectEvent> Connected = new MemoryChannel<ConnectEvent>();
    public final Channel<DisconnectEvent> Disconnected = new MemoryChannel<DisconnectEvent>();
    public final Channel<CloseEvent> Closed = new MemoryChannel<CloseEvent>();
    public final Channel<ReadTimeoutEvent> ReadTimeout = new MemoryChannel<ReadTimeoutEvent>();

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CountDownLatch logoutLatch = new CountDownLatch(1);
    private Disposable hbSchedule;
    public final Channel<HeartbeatEvent> Heartbeat = new MemoryChannel<HeartbeatEvent>();

    public interface ErrorHandler {

        void onUnexpectedDisconnect(Exception msg);

        void onConnectionFailure(Exception failed);

        public class SysOut implements ErrorHandler {

            public void onUnexpectedDisconnect(Exception msg) {
                msg.printStackTrace();
            }

            public void onConnectionFailure(Exception failed) {
                failed.printStackTrace();
            }
        }
    }

    public JetlangTcpClient(SocketConnector socketConnector, Fiber sendFiber,
                            JetlangClientConfig config,
                            Serializer ser,
                            ErrorHandler errorHandler) {
        this.socketConnector = socketConnector;
        this.sendFiber = sendFiber;
        this.config = config;
        this.ser = ser;
        this.errorHandler = errorHandler;
    }

    public <T> Disposable subscribe(final String subject, final Subscribable<T> callback) {
        Channel<T> channel;
        synchronized (channels) {
            //noinspection unchecked
            channel = (Channel<T>) channels.get(subject);
            if (channel == null) {
                channel = new MemoryChannel<T>();
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
        return new Disposable() {
            public void dispose() {
                //unsubscribe immediately
                unSub.dispose();
                Runnable sendUnsub = new Runnable() {

                    public void run() {
                        synchronized (channels) {
                            channels.remove(subject);
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

    private void sendSubscription(String subject, int msgType) {
        if (socket != null) {
            try {
                byte[] bytes = subject.getBytes(charset);
                socket.writeByteAsInt(msgType);
                socket.writeByteAsInt(bytes.length);
                socket.writeBytes(bytes);
            } catch (IOException e) {
                handleDisconnect(false, e);
            }
        }
    }

    private void closeIfNeeded() {
        if (socket != null) {
            socket.tryClose();
            socket = null;
            if (hbSchedule != null) {
                hbSchedule.dispose();
            }
            this.Closed.publish(new CloseEvent());
        }
    }

    private final Runnable connect = new Runnable() {
        public void run() {
            try {
                Socket socket = socketConnector.connect();
                handleConnect(socket);
            } catch (Exception failed) {
                errorHandler.onConnectionFailure(failed);
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
                handleDisconnect(false, exc);
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
        this.socket = new SocketMessageStreamWriter(newSocket, charset, ser.getWriter());
        for (String subscription : channels.keySet()) {
            sendSubscription(subscription, MsgTypes.Subscription);
        }
        final StreamReader stream = new StreamReader(newSocket.getInputStream(), charset, ser.getReader(), onReadTimeout);
        Runnable reader = new Runnable() {

            public void run() {
                try {
                    while (read(stream)) {
                    }
                } catch (IOException failed) {
                    handleDisconnectOnSendFiber(failed);
                }
            }
        };
        Thread readThread = new Thread(reader);
        readThread.start();
        this.Connected.publish(new ConnectEvent());
        if (config.getHeartbeatIntervalInMs() > 0) {
            hbSchedule = sendFiber.scheduleAtFixedRate(hb, config.getHeartbeatIntervalInMs(), config.getHeartbeatIntervalInMs(), TimeUnit.MILLISECONDS);
        }
    }

    private boolean read(StreamReader stream) throws IOException {
        try {
            int msg = stream.readByteAsInt();
            switch (msg) {
                case MsgTypes.Disconnect://logout
                    logoutLatch.countDown();
                    this.Disconnected.publish(new DisconnectEvent());
                    return false;
                case MsgTypes.Heartbeat:
                    this.Heartbeat.publish(new HeartbeatEvent());
                    return true;
                case MsgTypes.Data:
                    int topicSize = stream.readByteAsInt();
                    String topic = stream.readString(topicSize);
                    int msgSize = stream.readInt();
                    Object object = stream.readObject(topic, msgSize);
                    publishData(topic, object);
                    return true;
                default:
                    throw new IOException("Unknown msg: " + msg);
            }
        } catch (SocketTimeoutException readTimeout) {
            this.ReadTimeout.publish(new ReadTimeoutEvent());
            return true;
        }
    }

    private void handleDisconnectOnSendFiber(final Exception e) {
        Runnable exec = new Runnable() {
            public void run() {
                handleDisconnect(false, e);
            }
        };
        sendFiber.execute(exec);
    }

    private void handleDisconnect(boolean expected, Exception msg) {
        closeIfNeeded();
        if (pendingConnect == null && !closed.get()) {
            //should use fixed rate but don't want to introduce a dependency on that method.
            pendingConnect = sendFiber.scheduleWithFixedDelay(connect, 0, 1, TimeUnit.SECONDS);
            if (!expected)
                errorHandler.onUnexpectedDisconnect(msg);
        }

    }

    public <T> Disposable subscribe(String topic, DisposingExecutor clientFiber, Callback<T> cb) {
        return subscribe(topic, new ChannelSubscription<T>(clientFiber, cb));
    }

    public void start() {
        handleDisconnect(true, null);
        sendFiber.start();
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
                            e.printStackTrace();
                        }

                    }
                    if (pendingConnect != null) {
                        pendingConnect.dispose();
                    }
                    closeIfNeeded();
                    closedLatch.countDown();
                    sendFiber.dispose();
                }
            };
            sendFiber.execute(disconnect);
            return closedLatch;
        }
        throw new RuntimeException("Already closed.");
    }


    public Subscriber<DisconnectEvent> getDisconnectChannel() {
        return this.Disconnected;
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

    public <T> void publish(final String topic, final T msg) {
        Runnable r = new Runnable() {
            public void run() {
                if (socket != null) {
                    try {
                        socket.write(topic, msg);
                    } catch (IOException e) {
                        handleDisconnect(false, e);
                    }
                }
            }
        };
        sendFiber.execute(r);
    }
}
