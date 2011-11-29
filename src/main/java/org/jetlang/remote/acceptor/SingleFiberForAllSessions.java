package org.jetlang.remote.acceptor;

import org.jetlang.core.Callback;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.ThreadFiber;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maintains all subscription state on a single fiber.
 *
 * Should be used if ordering of messages is critical. To guarantee message delivery
 * ordering, the same fiber should be used for all session callbacks and for sending
 * to sessions.
 */
public class SingleFiberForAllSessions implements NewSessionHandler, ClientPublisher {

    private final NewFiberSessionHandler fact;
    private final Fiber fiber;
    private final BufferedSerializer serializer;
    private Map<JetlangSession, State> sessions = new HashMap<JetlangSession, State>();

    public SingleFiberForAllSessions(NewFiberSessionHandler fact, Fiber fiber, BufferedSerializer serializer) {
        this.fact = fact;
        this.fiber = fiber;
        this.serializer = serializer;
    }

    public void onNewSession(ClientPublisher _, final JetlangSession jetlangSession) {

        final State state = new State(jetlangSession);
        sessions.put(jetlangSession, state);

        Callback<SessionTopic> onSubscriber = new Callback<SessionTopic>() {
            public void onMessage(SessionTopic message) {
                 state.subscribe(message);
            }
        };
        jetlangSession.getSubscriptionRequestChannel().subscribe(fiber, onSubscriber);

        Callback<String> onUnSub = new Callback<String>() {
            public void onMessage(String message) {
                state.unsubscribe(message);
            }
        };
        jetlangSession.getUnsubscribeChannel().subscribe(fiber, onUnSub);

        Callback<SessionCloseEvent> onClose = new Callback<SessionCloseEvent>() {
            public void onMessage(SessionCloseEvent sessionCloseEvent) {
                sessions.remove(jetlangSession);
            }
        };
        jetlangSession.getSessionCloseChannel().subscribe(fiber, onClose);

        fact.onNewSession(this, jetlangSession, fiber);
    }

    /**
     * Should be invoked from the single fiber that maintains the sessions. This method is only safe if invoked from that single fiber.
     *
     * The message is serialized at most once.
     */
    public void publishToAllSubscribedClients(String topic, Object msg) {
       byte[] data = null;
        for (State state : sessions.values()) {
            if(state.isSubscribed(topic)){
                if(data == null){
                    data = serializer.createArray(topic, msg);
                }
                state.publish(data);
            }
        }
    }

    private static class State {

        private final JetlangSession session;
        private final Map<String, SessionTopic> subscriptions = new HashMap<String, SessionTopic>();

        public State(JetlangSession session){
            this.session = session;
        }

        public void subscribe(SessionTopic message) {
            subscriptions.put(message.getTopic(), message);
        }

        public void unsubscribe(String message) {
            subscriptions.remove(message);
        }

        public boolean isSubscribed(String topic) {
            return subscriptions.containsKey(topic);
        }

        public void publish(byte[] data) {
            session.publish(data);
        }
    }
}
