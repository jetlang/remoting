package org.jetlang.remote.client;

public class DeadMessageEvent {
    private final String topic;
    private final Object message;

    public DeadMessageEvent(String topic, Object message) {
        this.topic = topic;
        this.message = message;
    }

    public Object getMessage() {
        return message;
    }

    public String getTopic() {
        return topic;
    }

    @Override
    public String toString() {
        return "DeadMessageEvent{" +
                "topic='" + topic + '\'' +
                ", message=" + message +
                '}';
    }
}
