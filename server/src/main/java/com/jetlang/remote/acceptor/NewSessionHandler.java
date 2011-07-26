package com.jetlang.remote.acceptor;

public interface NewSessionHandler {

    void onNewSession(ClientPublisher globalPublisher, JetlangSession session);

}
