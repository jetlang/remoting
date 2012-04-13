package org.jetlang.remote.acceptor;

import org.jetlang.core.Callback;
import org.jetlang.fibers.Fiber;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Maintains all subscription state on a single fiber.
 * <p/>
 * Should be used if ordering of messages is critical. To guarantee message delivery
 * ordering, the same fiber should be used for all session callbacks and for sending
 * to sessions.
 */
public class FiberForAllSessions implements NewSessionHandler, ClientPublisher {

    private final NewFiberSessionHandler fact;
    private final Fiber fiber;
    private final BufferedSerializer serializer;
    private final Map<JetlangSession, JetlangFiberSession> sessions = new IdentityHashMap<JetlangSession, JetlangFiberSession>();

    public FiberForAllSessions(NewFiberSessionHandler fact, Fiber fiber, BufferedSerializer serializer) {
        this.fact = fact;
        this.fiber = fiber;
        this.serializer = serializer;
    }

    public void onNewSession(ClientPublisher _, final JetlangSession jetlangSession) {

        final CountDownLatch latch = new CountDownLatch(1);
        //create the new session on the fiber.
        //must wait for session to be created before continuing to prevent racing socket read thread events vs session fiber events.
        Runnable newSub = new Runnable() {
            public void run() {
                try {
                    JetlangFiberSession fiberSession = new JetlangFiberSession(jetlangSession, fiber);
                    sessions.put(jetlangSession, fiberSession);
                    Callback<SessionCloseEvent> onClose = new Callback<SessionCloseEvent>() {
                        public void onMessage(SessionCloseEvent sessionCloseEvent) {
                            sessions.remove(jetlangSession);
                        }
                    };
                    jetlangSession.getSessionCloseChannel().subscribe(fiber, onClose);

                    fact.onNewSession(FiberForAllSessions.this, fiberSession);
                } finally {
                    latch.countDown();
                }
            }
        };
        fiber.execute(newSub);
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public Collection<JetlangFiberSession> getAllSessions() {
        return sessions.values();
    }

    /**
     * Should be invoked from the single fiber that maintains the sessions. This method is only safe if invoked from that single fiber.
     * <p/>
     * The message is serialized at most once.
     */
    public void publishToAllSubscribedClients(String topic, Object msg) {
        byte[] data = null;
        for (JetlangFiberSession state : sessions.values()) {
            if (state.isSubscribed(topic)) {
                if (data == null) {
                    data = serializer.createArray(topic, msg);
                }
                state.publish(data);
            }
        }
    }
}
