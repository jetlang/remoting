package org.jetlang.remote.acceptor;

import org.jetlang.core.Callback;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.ThreadFiber;

public class FiberPerSession<R, W> implements NewSessionHandler<R, W> {

    private final NewFiberSessionHandler<R, W> fact;
    private final FiberPerSession.FiberFactory fiberFactory;

    public interface FiberFactory<R, W> {

        Fiber createForSession(JetlangSession<R, W> session);

        class ThreadFiberFactory<R, W> implements FiberPerSession.FiberFactory<R, W> {

            public Fiber createForSession(JetlangSession<R, W> session) {
                return new ThreadFiber();
            }
        }

    }

    public FiberPerSession(NewFiberSessionHandler<R, W> fact, FiberPerSession.FiberFactory<R, W> fiberFactory) {
        this.fact = fact;
        this.fiberFactory = fiberFactory;
    }

    public void onNewSession(ClientPublisher<W> publisher, JetlangSession<R, W> jetlangSession) {
        final Fiber fiber = fiberFactory.createForSession(jetlangSession);

        fact.onNewSession(publisher, new JetlangFiberSession<R, W>(jetlangSession, fiber));

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
