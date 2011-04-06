package com.jetlang.remote.server;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 3:43 PM
 */
public class SessionTopic {
    private final String topic;
    private final JetlangStreamSession session;

    public SessionTopic(String topic, JetlangStreamSession session) {
        this.topic = topic;
        this.session = session;
    }

    public void publish(Object msg) {
        this.session.publish(topic, msg);
    }

    public String getTopic() {
        return topic;
    }
}
