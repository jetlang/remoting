package com.jetlang.remote.server;

import org.jetlang.fibers.Fiber;

public interface NewSessionHandler {
    void onNewSession(JetlangSession jetlangSession, Fiber fiber);
}
