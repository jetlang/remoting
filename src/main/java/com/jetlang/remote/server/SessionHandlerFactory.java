package com.jetlang.remote.server;

import org.jetlang.fibers.Fiber;

public interface SessionHandlerFactory {
    void onNewSession(JetlangSession jetlangSession, Fiber fiber);
}
