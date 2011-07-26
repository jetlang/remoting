package org.jetlang.remote.acceptor;

public interface NewSessionHandler {

    void onNewSession(ClientPublisher globalPublisher, JetlangSession session);

}
