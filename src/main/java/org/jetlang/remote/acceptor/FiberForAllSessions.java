package org.jetlang.remote.acceptor;

import org.jetlang.core.Callback;
import org.jetlang.fibers.Fiber;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Maintains all subscription state on a single fiber.
 * Should be used if ordering of messages is critical. To guarantee message delivery
 * ordering, the same fiber should be used for all session callbacks and for sending
 * to sessions.
 */
public class FiberForAllSessions<R, W> implements NewSessionHandler<R, W>, ClientPublisher<W> {

    private final NewFiberSessionHandler<R, W> fact;
    private final Fiber fiber;
    private final BufferedSerializer<W> serializer;
    private final Map<JetlangSession<R, W>, JetlangFiberSession<R, W>> sessions = new IdentityHashMap<JetlangSession<R, W>, JetlangFiberSession<R, W>>();

    public FiberForAllSessions(NewFiberSessionHandler<R, W> fact, Fiber fiber, BufferedSerializer<W> serializer) {
        this.fact = fact;
        this.fiber = fiber;
        this.serializer = serializer;
    }

    public void onNewSession(ClientPublisher<W> unused, final JetlangSession<R, W> jetlangSession) {

        final CountDownLatch latch = new CountDownLatch(1);
        //create the new session on the fiber.
        //must wait for session to be created before continuing to prevent racing socket read thread events vs session fiber events.
        Runnable newSub = new Runnable() {
            public void run() {
                try {
                    JetlangFiberSession<R, W> fiberSession = new JetlangFiberSession<R, W>(jetlangSession, fiber);
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

    public Collection<JetlangFiberSession<R, W>> getAllSessions() {
        return sessions.values();
    }

    /**
     * Should be invoked from the single fiber that maintains the sessions. This method is only safe if invoked from that single fiber.
     * The message is serialized at most once.
     */
    public void publishToAllSubscribedClients(String topic, W msg) {
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
