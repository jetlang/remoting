package org.jetlang.remote.acceptor;

import org.jetlang.channels.Subscriber;
import org.jetlang.remote.core.HeartbeatEvent;
import org.jetlang.remote.core.ReadTimeoutEvent;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 5:48 PM
 */
public interface JetlangSession {

    Object getSessionId();

    Subscriber<SessionTopic> getSubscriptionRequestChannel();

    Subscriber<String> getUnsubscribeChannel();

    Subscriber<LogoutEvent> getLogoutChannel();

    Subscriber<HeartbeatEvent> getHeartbeatChannel();

    Subscriber<SessionMessage<?>> getSessionMessageChannel();

    Subscriber<SessionRequest> getSessionRequestChannel();

    Subscriber<ReadTimeoutEvent> getReadTimeoutChannel();

    Subscriber<SessionCloseEvent> getSessionCloseChannel();

    /**
     * Attempts to disconnect the client. This call is synchronous to the client.
     *
     * @return true if close succeeds
     */
    boolean disconnect();

    /**
     * publish raw bytes. The bytes should be correctly formatted with the topic included. The bytes will
     * be published asynchronously, so the byte array should be a thread safe copy of the data.
     */
    void publish(byte[] data);

    <T> void publish(final String topic, final T msg);
}
