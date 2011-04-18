package com.jetlang.remote.acceptor;

import org.jetlang.fibers.Fiber;

public interface NewFiberSessionHandler {
    void onNewSession(JetlangSession jetlangSession, Fiber fiber);
}
