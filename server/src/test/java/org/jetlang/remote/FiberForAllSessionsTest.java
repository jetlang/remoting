package org.jetlang.remote;

import org.jetlang.fibers.Fiber;
import org.jetlang.fibers.ThreadFiber;
import org.jetlang.remote.acceptor.FiberForAllSessions;
import org.jetlang.remote.acceptor.NewFiberSessionHandler;
import org.jetlang.remote.acceptor.NewSessionHandler;
import org.junit.After;

public class FiberForAllSessionsTest extends IntegrationBase {
    Fiber f = new ThreadFiber();

    public FiberForAllSessionsTest() {
        f.start();
    }

    @After
    public void shutdown() {
        f.dispose();
        super.shutdown();
    }

    protected NewSessionHandler wrap(NewFiberSessionHandler newFiberSessionHandler) {
        return new FiberForAllSessions(newFiberSessionHandler, f, serAdapter.createBuffered());
    }
}
