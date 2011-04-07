package com.jetlang.remote.server;

import org.jetlang.core.Callback;
import org.jetlang.core.SynchronousDisposingExecutor;
import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.ThreadFiber;

public class FiberPerSession {

    private final NewSessionHandler fact;
    private final FiberPerSession.FiberFactory fiberFactory;

    public static interface FiberFactory {

        Fiber createForSession(JetlangSession session);

        public static class ThreadFiberFactory implements FiberPerSession.FiberFactory {

            public Fiber createForSession(JetlangSession session) {
                return new ThreadFiber();
            }
        }

    }

    public FiberPerSession(JetlangSessionChannels channels, NewSessionHandler fact, FiberPerSession.FiberFactory fiberFactory) {
        this.fact = fact;
        this.fiberFactory = fiberFactory;
        channels.SessionOpen.subscribe(new SynchronousDisposingExecutor(), onOpen());
    }

    private Callback<JetlangSession> onOpen() {
        return new Callback<JetlangSession>() {

            public void onMessage(JetlangSession jetlangSession) {
                final Fiber fiber = fiberFactory.createForSession(jetlangSession);

                fact.onNewSession(jetlangSession, fiber);

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
        };
    }
}
