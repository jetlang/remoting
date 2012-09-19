package org.jetlang.remote.acceptor;

public class UnsubscribeEvent {
    private String topic;

    public UnsubscribeEvent(String topic) {
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }
}
