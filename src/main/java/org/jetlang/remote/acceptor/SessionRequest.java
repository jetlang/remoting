package org.jetlang.remote.acceptor;

public class SessionRequest<R, W> {
    private final int reqId;
    private final String reqmsgTopic;
    private final R reqmsg;
    private final JetlangMessagePublisher<W> session;

    public SessionRequest(int reqId, String reqmsgTopic, R reqmsg, JetlangMessagePublisher<W> session) {
        this.reqId = reqId;
        this.reqmsgTopic = reqmsgTopic;
        this.reqmsg = reqmsg;
        this.session = session;
    }

    public String getTopic() {
        return reqmsgTopic;
    }

    public R getRequest() {
        return reqmsg;
    }

    public void reply(W replyMsg) {
        session.reply(reqId, reqmsgTopic, replyMsg);
    }

    public void reply(W replyMsg, String replyTopic) {
        session.reply(reqId, replyTopic, replyMsg);
    }

}
