package org.jetlang.remote.acceptor;

import org.jetlang.channels.Subscriber;
import org.jetlang.core.Callback;
import org.jetlang.fibers.Fiber;
import org.jetlang.remote.core.HeartbeatEvent;
import org.jetlang.remote.core.ReadTimeoutEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * User: mrettig
 * Date: 11/29/11
 * Time: 11:14 AM
 */
public class JetlangFiberSession implements JetlangSession {

    private final JetlangSession session;
    private final Fiber targetFiber;
    private final Map<String, SessionTopic> subscribed = new HashMap<String, SessionTopic>();

    public JetlangFiberSession(JetlangSession session, Fiber targetFiber) {
        this.session = session;
        this.targetFiber = targetFiber;
        session.getSubscriptionRequestChannel().subscribe(targetFiber, new Callback<SessionTopic>() {
            public void onMessage(SessionTopic message) {
                subscribed.put(message.getTopic(), message);
            }
        });
        session.getUnsubscribeChannel().subscribe(targetFiber, new Callback<UnsubscribeEvent>() {
            public void onMessage(UnsubscribeEvent event) {
                subscribed.remove(event.getTopic());
            }
        });
    }

    public Map<String, SessionTopic> getSubscriptions() {
        return subscribed;
    }

    public boolean isSubscribed(String topic) {
        return subscribed.containsKey(topic);
    }

    public Fiber getFiber() {
        return targetFiber;
    }

    public Object getSessionId() {
        return session.getSessionId();
    }

    public Subscriber<SessionTopic> getSubscriptionRequestChannel() {
        return session.getSubscriptionRequestChannel();
    }

    public Subscriber<UnsubscribeEvent> getUnsubscribeChannel() {
        return session.getUnsubscribeChannel();
    }

    public Subscriber<LogoutEvent> getLogoutChannel() {
        return session.getLogoutChannel();
    }

    public Subscriber<HeartbeatEvent> getHeartbeatChannel() {
        return session.getHeartbeatChannel();
    }

    public Subscriber<SessionMessage<?>> getSessionMessageChannel() {
        return session.getSessionMessageChannel();
    }

    public Subscriber<SessionRequest> getSessionRequestChannel() {
        return session.getSessionRequestChannel();
    }

    public Subscriber<ReadTimeoutEvent> getReadTimeoutChannel() {
        return session.getReadTimeoutChannel();
    }

    public Subscriber<SessionCloseEvent> getSessionCloseChannel() {
        return session.getSessionCloseChannel();
    }

    public boolean disconnect() {
        return session.disconnect();
    }

    public void publish(byte[] data) {
        session.publish(data);
    }

}
