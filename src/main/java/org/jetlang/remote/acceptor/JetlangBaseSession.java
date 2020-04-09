package org.jetlang.remote.acceptor;

import org.jetlang.channels.MemoryChannel;
import org.jetlang.channels.Subscriber;
import org.jetlang.remote.core.CloseableChannel;
import org.jetlang.remote.core.HeartbeatEvent;
import org.jetlang.remote.core.ReadTimeoutEvent;

public abstract class JetlangBaseSession<R, W> implements JetlangSession<R, W>, JetlangMessagePublisher<W> {

    private final CloseableChannel.Group allChannels = new CloseableChannel.Group();

    protected <T> CloseableChannel<T> newChannel() {
        return allChannels.add(new MemoryChannel<T>());
    }

    protected final CloseableChannel<SessionTopic<W>> SubscriptionRequest = newChannel();
    protected final CloseableChannel<String> UnsubscribeRequest = newChannel();
    protected final CloseableChannel<LogoutEvent> Logout = newChannel();
    protected final CloseableChannel<HeartbeatEvent> Heartbeat = newChannel();
    protected final CloseableChannel<SessionMessage<R>> Messages = newChannel();
    protected final CloseableChannel<ReadTimeoutEvent> ReadTimeout = newChannel();
    protected final CloseableChannel<SessionCloseEvent> SessionClose = newChannel();
    protected final CloseableChannel<SessionRequest<R, W>> SessionRequest = newChannel();

    protected final Object id;

    public JetlangBaseSession(Object id) {
        this.id = id;
    }

    public Object getSessionId() {
        return id;
    }

    public abstract void onLogout();

    public abstract void onSubscriptionRequest(String topic);

    public abstract void onUnsubscribeRequest(String topic);

    public void onHb() {
        Heartbeat.publish(new HeartbeatEvent());
    }

    public abstract void publish(final byte[] data);

    public abstract void reply(final int reqId, final String replyTopic, final W replyMsg);

    public abstract void publishIfSubscribed(String topic, final byte[] data);

    public Subscriber<SessionTopic<W>> getSubscriptionRequestChannel() {
        return SubscriptionRequest;
    }

    public Subscriber<LogoutEvent> getLogoutChannel() {
        return Logout;
    }

    public Subscriber<HeartbeatEvent> getHeartbeatChannel() {
        return Heartbeat;
    }

    public Subscriber<SessionMessage<R>> getSessionMessageChannel() {
        return Messages;
    }

    public Subscriber<ReadTimeoutEvent> getReadTimeoutChannel() {
        return ReadTimeout;
    }

    public Subscriber<SessionCloseEvent> getSessionCloseChannel() {
        return SessionClose;
    }

    public void onMessage(String topic, R msg) {
        Messages.publish(new SessionMessage<>(topic, msg));
    }

    public Subscriber<String> getUnsubscribeChannel() {
        return UnsubscribeRequest;
    }

    public Subscriber<SessionRequest<R, W>> getSessionRequestChannel() {
        return SessionRequest;
    }

    public void onRequest(int reqId, String reqmsgTopic, R reqmsg) {
        SessionRequest.publish(new SessionRequest<R,W>(reqId, reqmsgTopic, reqmsg, this));
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
