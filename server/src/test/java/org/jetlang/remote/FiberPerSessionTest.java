package org.jetlang.remote;

import org.jetlang.remote.acceptor.FiberPerSession;
import org.jetlang.remote.acceptor.NewFiberSessionHandler;
import org.jetlang.remote.acceptor.NewSessionHandler;

public class FiberPerSessionTest extends IntegrationBase {

    protected NewSessionHandler wrap(NewFiberSessionHandler newFiberSessionHandler) {
        return new FiberPerSession(newFiberSessionHandler, new FiberPerSession.FiberFactory.ThreadFiberFactory());
    }
}
