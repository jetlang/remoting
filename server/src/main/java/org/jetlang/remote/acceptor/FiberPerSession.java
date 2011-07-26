package org.jetlang.remote.acceptor;

import org.jetlang.core.Callback;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.ThreadFiber;

public class FiberPerSession implements NewSessionHandler {

    private final NewFiberSessionHandler fact;
    private final FiberPerSession.FiberFactory fiberFactory;

    public interface FiberFactory {

        Fiber createForSession(JetlangSession session);

        class ThreadFiberFactory implements FiberPerSession.FiberFactory {

            public Fiber createForSession(JetlangSession session) {
                return new ThreadFiber();
            }
        }

    }

    public FiberPerSession(NewFiberSessionHandler fact, FiberPerSession.FiberFactory fiberFactory) {
        this.fact = fact;
        this.fiberFactory = fiberFactory;
    }

    public void onNewSession(ClientPublisher publisher, JetlangSession jetlangSession) {
        final Fiber fiber = fiberFactory.createForSession(jetlangSession);

        fact.onNewSession(publisher, jetlangSession, fiber);

        //start fiber after session is initialized.
        fiber.start();

        Callback<SessionCloseEvent> onClose = new Callback<SessionCloseEvent>() {

            public void onMessage(SessionCloseEvent sessionCloseEvent) {
                Runnable stopFiber = new Runnable() {
                    public void run() {
                        fiber.dispose();
                    }
                };
                fiber.execute(stopFiber);
            }
        };
        jetlangSession.getSessionCloseChannel().subscribe(new SynchronousDisposingExecutor(), onClose);
    }
}
