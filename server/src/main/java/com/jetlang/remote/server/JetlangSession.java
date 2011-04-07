package com.jetlang.remote.server;

import com.jetlang.remote.core.HeartbeatEvent;
import com.jetlang.remote.core.ReadTimeoutEvent;
import org.jetlang.channels.Subscriber;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 5:48 PM
 */
public interface JetlangSession {

    Subscriber<SessionTopic> getSubscriptionRequestChannel();

    Subscriber<String> getUnsubscribeChannel();

    Subscriber<LogoutEvent> getLogoutChannel();

    Subscriber<HeartbeatEvent> getHeartbeatChannel();

    Subscriber<SessionMessage<?>> getSessionMessageChannel();

    Subscriber<ReadTimeoutEvent> getReadTimeoutChannel();

    Subscriber<SessionCloseEvent> getSessionCloseChannel();
}
