package com.jetlang.remote.acceptor;

import com.jetlang.remote.core.HeartbeatEvent;
import com.jetlang.remote.core.ReadTimeoutEvent;
import org.jetlang.channels.Subscriber;

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
}
