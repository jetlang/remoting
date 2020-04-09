package org.jetlang.remote.acceptor;

public interface NewSessionHandler<R, W> {

    void onNewSession(ClientPublisher<W> globalPublisher, JetlangSession<R, W> session);

}
