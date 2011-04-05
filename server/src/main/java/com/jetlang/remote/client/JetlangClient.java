package com.jetlang.remote.client;

import com.jetlang.remote.core.MsgTypes;
import com.jetlang.remote.core.StreamReader;
import org.jetlang.channels.Channel;
import org.jetlang.channels.ChannelSubscription;
import org.jetlang.channels.MemoryChannel;
import org.jetlang.channels.Subscribable;
import org.jetlang.core.Callback;
import org.jetlang.core.Disposable;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.ThreadFiber;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 */
public class JetlangClient {

    private Socket socket;
    private final Fiber sendFiber;
    private static final Charset charset = Charset.forName("US-ASCII");
    private final SocketConnector socketConnector;
    private Disposable pendingConnect;
    private final Map<String, Channel> channels = new LinkedHashMap<String, Channel>();

    public final Channel<ConnectEvent> Connected = new MemoryChannel<ConnectEvent>();
    public final Channel<DisconnectEvent> Disconnected = new MemoryChannel<DisconnectEvent>();
    public final Channel<CloseEvent> Closed = new MemoryChannel<CloseEvent>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CountDownLatch logoutLatch = new CountDownLatch(1);

    public JetlangClient(SocketConnector socketConnector, Fiber sendFiber) {
        this.socketConnector = socketConnector;
        this.sendFiber = sendFiber;
    }

    public <T> void subscribe(final String subject, final Subscribable<T> callback) {
        Runnable sub = new Runnable() {
            public void run() {
                Channel<T> channel = (Channel<T>) channels.get(subject);
                if (channel == null) {
                    channel = new MemoryChannel();
                    channels.put(subject, channel);
                }
                channel.subscribe(callback);
                sendSubscription(subject);
            }
        };
        sendFiber.execute(sub);
    }

    private void sendSubscription(String subject) {
        if (socket != null) {
            try {
                byte[] bytes = subject.getBytes(charset);
                socket.getOutputStream().write(MsgTypes.Subscription);
                socket.getOutputStream().write(bytes.length);
                socket.getOutputStream().write(bytes);
            } catch (IOException e) {
                handleDisconnect();
            }
        }
    }

    private void closeIfNeeded() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            socket = null;
            this.Closed.publish(new CloseEvent());
        }
    }

    private final Runnable connect = new Runnable() {
        public void run() {
            try {
                Socket socket = socketConnector.connect();
                handleConnect(socket);
            } catch (Exception failed) {
                failed.printStackTrace();
                socket = null;
            }
        }
    };

    private void handleConnect(Socket newSocket) throws IOException {
        this.pendingConnect.dispose();
        this.pendingConnect = null;
        this.socket = newSocket;
        for (String subscription : channels.keySet()) {
            sendSubscription(subscription);
        }
        final StreamReader stream = new StreamReader(newSocket.getInputStream());
        Runnable reader = new Runnable() {

            public void run() {
                try {
                    while (read(stream)) {
                    }
                } catch (IOException failed) {
                    handleDisconnectOnSendFiber();
                }
            }
        };
        Thread readThread = new Thread(reader);
        readThread.start();
        this.Connected.publish(new ConnectEvent());
    }

    private boolean read(StreamReader stream) throws IOException {
        int msg = stream.readByteAsInt();
        switch (msg) {
            case MsgTypes.Disconnect://logout
                logoutLatch.countDown();
                this.Disconnected.publish(new DisconnectEvent());
                return false;
            default:
                error("Unknown msg: " + msg);
        }
        return false;
    }

    private void error(String s) {
        System.err.println(s);
    }

    private void handleDisconnectOnSendFiber() {
        Runnable exec = new Runnable() {
            public void run() {
                handleDisconnect();
            }
        };
        sendFiber.execute(exec);
    }

    private void handleDisconnect() {
        closeIfNeeded();
        if (pendingConnect == null && !closed.get()) {
            //should use fixed rate but don't want to introduce a dependency on that method.
            pendingConnect = sendFiber.scheduleWithFixedDelay(connect, 0, 1, TimeUnit.SECONDS);
        }
    }

    public <T> void subscribe(String topic, ThreadFiber clientFiber, Callback<T> cb) {
        subscribe(topic, new ChannelSubscription<T>(clientFiber, cb));
    }

    public void start() {
        handleDisconnect();
        sendFiber.start();
    }

    public CountDownLatch close(final boolean sendLogoutIfStillConnected) {
        final CountDownLatch closedLatch = new CountDownLatch(1);
        if (closed.compareAndSet(false, true)) {
            Runnable disconnect = new Runnable() {
                public void run() {
                    if (socket != null && sendLogoutIfStillConnected) {
                        try {
                            socket.getOutputStream().write(MsgTypes.Disconnect);
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
}
