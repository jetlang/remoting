package org.jetlang.remote.acceptor;

public interface JetlangMessagePublisher {
    <T> void publish(String topic, T msg);
}
