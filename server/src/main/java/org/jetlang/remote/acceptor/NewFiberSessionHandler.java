package org.jetlang.remote.acceptor;

public interface NewFiberSessionHandler {
    void onNewSession(ClientPublisher publisher, JetlangFiberSession jetlangSession);
}
