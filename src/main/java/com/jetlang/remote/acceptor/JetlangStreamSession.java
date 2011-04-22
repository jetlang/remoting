package com.jetlang.remote.acceptor;

import com.jetlang.remote.core.HeartbeatEvent;
import com.jetlang.remote.core.MsgTypes;
import com.jetlang.remote.core.ReadTimeoutEvent;
import org.jetlang.channels.MemoryChannel;
import org.jetlang.channels.Subscriber;
import org.jetlang.fibers.Fiber;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class JetlangStreamSession implements JetlangSession {

    private final CloseableChannel.Group allChannels = new CloseableChannel.Group();
    private final CloseableChannel<SessionTopic> SubscriptionRequest = newChannel();
    private final CloseableChannel<String> UnsubscribeRequest = newChannel();

    private <T> CloseableChannel<T> newChannel() {
        return allChannels.add(new MemoryChannel<T>());
    }

    private final CloseableChannel<LogoutEvent> Logout = newChannel();
    private final CloseableChannel<HeartbeatEvent> Heartbeat = newChannel();
    private final CloseableChannel<SessionMessage<?>> Messages = newChannel();
    private final CloseableChannel<ReadTimeoutEvent> ReadTimeout = newChannel();
    private final CloseableChannel<SessionCloseEvent> SessionClose = newChannel();
    private final CloseableChannel<SessionRequest> SessionRequest = newChannel();

    private final Object id;
    private final MessageStreamWriter socket;
    private final Fiber sendFiber;
    private final Set<String> subscriptions = Collections.synchronizedSet(new HashSet<String>());

    public JetlangStreamSession(Object id, MessageStreamWriter socket, Fiber sendFiber) {
        this.id = id;
        this.socket = socket;
        this.sendFiber = sendFiber;
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
            sendFiber.scheduleWithFixedDelay(send, interval, interval, unit);
        }
    }

    void onSubscriptionRequest(String topic) {
        subscriptions.add(topic);
        SubscriptionRequest.publish(new SessionTopic(topic, this));
    }

    public void onUnsubscribeRequest(String top) {
        subscriptions.remove(top);
        UnsubscribeRequest.publish(top);
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
    }

    public boolean disconnect() {
        return socket.tryClose();
    }

    public void onLogout() {
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

    public void reply(final int reqId, final String replyTopic, final Object replyMsg) {
        final Runnable replyRunner = new Runnable() {
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


    public void publishIfSubscribed(final String topic, final byte[] data) {
        if (subscriptions.contains(topic)) {
            Runnable r = new Runnable() {
                public void run() {
                    try {
                        socket.writeBytes(data);
                    } catch (IOException e) {
                        handleDisconnect(e);
                    }
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

    public Subscriber<String> getUnsubscribeChannel() {
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
