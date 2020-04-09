package org.jetlang.remote.acceptor;

public interface NewFiberSessionHandler<R, W> {
    void onNewSession(ClientPublisher<W> publisher, JetlangFiberSession<R, W> jetlangSession);
}
