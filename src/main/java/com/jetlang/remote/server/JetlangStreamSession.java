package com.jetlang.remote.server;

import com.jetlang.remote.core.HeartbeatEvent;
import com.jetlang.remote.core.MsgTypes;
import com.jetlang.remote.core.ReadTimeoutEvent;
import org.jetlang.channels.Channel;
import org.jetlang.channels.MemoryChannel;
import org.jetlang.channels.Subscriber;
import org.jetlang.fibers.Fiber;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class JetlangStreamSession implements JetlangSession {

    public final Channel<SessionTopic> SubscriptionRequest = new MemoryChannel<SessionTopic>();
    public final Channel<String> UnsubscribeRequest = new MemoryChannel<String>();
    public final Channel<LogoutEvent> Logout = new MemoryChannel<LogoutEvent>();
    public final Channel<HeartbeatEvent> Heartbeat = new MemoryChannel<HeartbeatEvent>();
    public final Channel<SessionMessage<?>> Messages = new MemoryChannel<SessionMessage<?>>();
    public final Channel<ReadTimeoutEvent> ReadTimeout = new MemoryChannel<ReadTimeoutEvent>();
    public final Channel<SessionCloseEvent> SessionClose = new MemoryChannel<SessionCloseEvent>();

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
            sendFiber.scheduleAtFixedRate(send, interval, interval, unit);
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
}
