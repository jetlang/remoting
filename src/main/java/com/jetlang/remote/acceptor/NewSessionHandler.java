package com.jetlang.remote.acceptor;

public interface NewSessionHandler {

    public void onNewSession(ClientPublisher globalPublisher, JetlangSession session);

}
