package com.jetlang.remote.server;

import com.jetlang.remote.core.HeartbeatEvent;
import org.jetlang.channels.Subscriber;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 5:48 PM
 */
public interface JetlangSession {

    Subscriber<SessionTopic> getSubscriptionRequestChannel();

    Subscriber<LogoutEvent> getLogoutChannel();

    Subscriber<HeartbeatEvent> getHeartbeatChannel();

    Subscriber<SessionMessage<?>> getSessionMessageChannel();

}
