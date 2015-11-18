package org.jetlang.remote.acceptor;

import org.jetlang.channels.MemoryChannel;
import org.jetlang.channels.Subscriber;
import org.jetlang.remote.core.CloseableChannel;
import org.jetlang.remote.core.HeartbeatEvent;
import org.jetlang.remote.core.MsgTypes;
import org.jetlang.remote.core.ReadTimeoutEvent;

public abstract class JetlangBaseSession implements JetlangSession, JetlangMessagePublisher {

    private final CloseableChannel.Group allChannels = new CloseableChannel.Group();

    protected <T> CloseableChannel<T> newChannel() {
        return allChannels.add(new MemoryChannel<T>());
    }

    protected final CloseableChannel<SessionTopic> SubscriptionRequest = newChannel();
    protected final CloseableChannel<String> UnsubscribeRequest = newChannel();
    protected final CloseableChannel<LogoutEvent> Logout = newChannel();
    protected final CloseableChannel<HeartbeatEvent> Heartbeat = newChannel();
    protected final CloseableChannel<SessionMessage<?>> Messages = newChannel();
    protected final CloseableChannel<ReadTimeoutEvent> ReadTimeout = newChannel();
    protected final CloseableChannel<SessionCloseEvent> SessionClose = newChannel();
    protected final CloseableChannel<SessionRequest> SessionRequest = newChannel();

    protected final Object id;

    public JetlangBaseSession(Object id) {
        this.id = id;
    }

    public Object getSessionId() {
        return id;
    }

    public abstract void write(final int byteToWrite);

    public final void onLogout() {
        write(MsgTypes.Disconnect);
        Logout.publish(new LogoutEvent());
        afterLogout();
    }

    protected abstract void afterLogout();

    public void onHb() {
        Heartbeat.publish(new HeartbeatEvent());
    }

    public abstract <T> void publish(final String topic, final T msg);

    public abstract void publish(final byte[] data);

    public abstract void reply(final int reqId, final String replyTopic, final Object replyMsg);

    public abstract void publishIfSubscribed(String topic, final byte[] data);

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
