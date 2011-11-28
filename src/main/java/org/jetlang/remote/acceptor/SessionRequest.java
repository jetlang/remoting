package org.jetlang.remote.acceptor;

public class SessionRequest {
    private final int reqId;
    private final String reqmsgTopic;
    private final Object reqmsg;
    private final JetlangStreamSession session;

    public SessionRequest(int reqId, String reqmsgTopic, Object reqmsg, JetlangStreamSession session) {
        this.reqId = reqId;
        this.reqmsgTopic = reqmsgTopic;
        this.reqmsg = reqmsg;
        this.session = session;
    }

    public String getTopic() {
        return reqmsgTopic;
    }

    public Object getRequest() {
        return reqmsg;
    }

    public void reply(Object replyMsg) {
        session.reply(reqId, reqmsgTopic, replyMsg);
    }

    public void reply(Object replyMsg, String replyTopic) {
        session.reply(reqId, replyTopic, replyMsg);
    }

}
