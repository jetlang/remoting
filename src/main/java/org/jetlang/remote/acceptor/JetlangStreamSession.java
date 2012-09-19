package org.jetlang.remote.acceptor;

import org.jetlang.channels.MemoryChannel;
import org.jetlang.channels.Subscriber;
import org.jetlang.core.Disposable;
import org.jetlang.fibers.Fiber;
import org.jetlang.remote.core.*;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class JetlangStreamSession implements JetlangSession {

    private final CloseableChannel.Group allChannels = new CloseableChannel.Group();

    private <T> CloseableChannel<T> newChannel() {
        return allChannels.add(new MemoryChannel<T>());
    }

    private final CloseableChannel<SessionTopic> SubscriptionRequest = newChannel();
    private final CloseableChannel<UnsubscribeEvent> UnsubscribeRequest = newChannel();
    private final CloseableChannel<LogoutEvent> Logout = newChannel();
    private final CloseableChannel<HeartbeatEvent> Heartbeat = newChannel();
    private final CloseableChannel<SessionMessage<?>> Messages = newChannel();
    private final CloseableChannel<ReadTimeoutEvent> ReadTimeout = newChannel();
    private final CloseableChannel<SessionCloseEvent> SessionClose = newChannel();
    private final CloseableChannel<SessionRequest> SessionRequest = newChannel();

    private final Object id;
    private final MessageStreamWriter socket;
    private final Fiber sendFiber;
    private final ErrorHandler errorHandler;
    private final Set<String> subscriptions = Collections.synchronizedSet(new HashSet<String>());

    private volatile Runnable hbStopper = new Runnable() {
        public void run() {
        }
    };

    public JetlangStreamSession(Object id, MessageStreamWriter socket, Fiber sendFiber, ErrorHandler errorHandler) {
        this.id = id;
        this.socket = socket;
        this.sendFiber = sendFiber;
        this.errorHandler = errorHandler;
    }

    public Object getSessionId() {
        return id;
    }

    public void startHeartbeat(int interval, TimeUnit unit) {
        if (interval > 0) {
            Runnable send = new Runnable() {

                public void run() {
                    write(MsgTypes.Heartbeat);
                }
            };
            final Disposable disposeHb = sendFiber.scheduleWithFixedDelay(send, interval, interval, unit);
            hbStopper = new Runnable() {
                AtomicBoolean stopped = new AtomicBoolean(false);

                public void run() {
                    if (stopped.compareAndSet(false, true)) {
                        disposeHb.dispose();
                    }
                }
            };
        }
    }

    void onSubscriptionRequest(String topic) {
        subscriptions.add(topic);
        SubscriptionRequest.publish(new SessionTopic(topic, this));
    }

    public void onUnsubscribeRequest(String top) {
        subscriptions.remove(top);
        UnsubscribeRequest.publish(new UnsubscribeEvent(top));
    }

    public void write(final int byteToWrite) {
        Runnable r = new Runnable() {
            public void run() {
                try {
                    socket.writeByteAsInt(byteToWrite);
                } catch (IOException e) {
                    handleDisconnect(e);
                }
            }
        };
        sendFiber.execute(r);
    }

    private void handleDisconnect(IOException e) {
        socket.tryClose();
        errorHandler.onException(e);
    }

    public boolean disconnect() {
        return socket.tryClose();
    }

    public void onLogout() {
        hbStopper.run();
        Logout.publish(new LogoutEvent());
    }

    public void onHb() {
        Heartbeat.publish(new HeartbeatEvent());
    }

    public <T> void publish(final String topic, final T msg) {
        Runnable r = new Runnable() {
            public void run() {
                try {
                    socket.write(topic, msg);
                } catch (IOException e) {
                    handleDisconnect(e);
                }
            }
        };
        sendFiber.execute(r);
    }

    public void publish(final byte[] data) {
        Runnable r = new Runnable() {
            public void run() {
                writeBytesOnSendFiberThread(data);
            }
        };
        sendFiber.execute(r);
    }

    private void writeBytesOnSendFiberThread(byte[] data) {
        try {
            socket.writeBytes(data);
        } catch (IOException e) {
            handleDisconnect(e);
        }
    }

    public void reply(final int reqId, final String replyTopic, final Object replyMsg) {
        Runnable replyRunner = new Runnable() {
            public void run() {
                try {
                    socket.writeReply(reqId, replyTopic, replyMsg);
                } catch (IOException e) {
                    handleDisconnect(e);
                }
            }
        };
        sendFiber.execute(replyRunner);
    }


    public void publishIfSubscribed(String topic, final byte[] data) {
        if (subscriptions.contains(topic)) {
            Runnable r = new Runnable() {
                public void run() {
                    writeBytesOnSendFiberThread(data);
                }
            };
            sendFiber.execute(r);
        }
    }

    public Subscriber<SessionTopic> getSubscriptionRequestChannel() {
        return SubscriptionRequest;
    }

    public Subscriber<LogoutEvent> getLogoutChannel() {
        return Logout;
    }

    public Subscriber<HeartbeatEvent> getHeartbeatChannel() {
        return Heartbeat;
    }

    public Subscriber<SessionMessage<?>> getSessionMessageChannel() {
        return Messages;
    }

    public Subscriber<ReadTimeoutEvent> getReadTimeoutChannel() {
        return ReadTimeout;
    }

    public Subscriber<SessionCloseEvent> getSessionCloseChannel() {
        return SessionClose;
    }

    public void onMessage(String topic, Object msg) {
        Messages.publish(new SessionMessage<Object>(topic, msg));
    }

    public Subscriber<UnsubscribeEvent> getUnsubscribeChannel() {
        return UnsubscribeRequest;
    }

    public Subscriber<SessionRequest> getSessionRequestChannel() {
        return SessionRequest;
    }

    public void onRequest(int reqId, String reqmsgTopic, Object reqmsg) {
        SessionRequest.publish(new SessionRequest(reqId, reqmsgTopic, reqmsg, this));
    }

    public void onClose(SessionCloseEvent sessionCloseEvent) {
        try {
            SessionClose.publish(sessionCloseEvent);
        } finally {
            allChannels.closeAndClear();
        }
    }

    public void onReadTimeout(ReadTimeoutEvent readTimeoutEvent) {
        ReadTimeout.publish(readTimeoutEvent);
    }
}
