package org.jetlang.remote.client;

public class DeadMessageEvent<T> {
    private final String topic;
    private final T message;

    public DeadMessageEvent(String topic, T message) {
        this.topic = topic;
        this.message = message;
    }

    public T getMessage() {
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
