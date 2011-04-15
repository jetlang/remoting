package com.jetlang.remote.server;

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

    public void reply(Object replyMsg) {
        session.reply(reqId, reqmsgTopic, replyMsg);
    }
}
