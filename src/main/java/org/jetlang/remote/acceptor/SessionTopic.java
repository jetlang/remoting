package org.jetlang.remote.acceptor;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 3:43 PM
 */
public class SessionTopic<T> {
    private final String topic;
    private final JetlangMessagePublisher<T> session;

    public SessionTopic(String topic, JetlangMessagePublisher<T> session) {
        this.topic = topic;
        this.session = session;
    }

    public void publish(T msg) {
        this.session.publish(topic, msg);
    }

    public String getTopic() {
        return topic;
    }
}
