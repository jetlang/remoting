package com.jetlang.remote.acceptor;

import org.jetlang.fibers.Fiber;

public interface NewSessionHandler {
    void onNewSession(JetlangSession jetlangSession, Fiber fiber);
}
