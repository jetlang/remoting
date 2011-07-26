package org.jetlang.remote.acceptor;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 11:11 AM
 */
public class SessionMessage<T> {

    private final T msg;
    private final String topic;

    public SessionMessage(String topic, T msg) {
        this.topic = topic;
        this.msg = msg;
    }

    public String getTopic() {
        return topic;
    }

    public T getMessage() {
        return msg;
    }
}
