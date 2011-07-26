package org.jetlang.remote.acceptor;

import org.jetlang.fibers.Fiber;

public interface NewFiberSessionHandler {
    void onNewSession(ClientPublisher publisher, JetlangSession jetlangSession, Fiber fiber);
}
