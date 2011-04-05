package com.jetlang.remote.server;

import org.jetlang.channels.Channel;
import org.jetlang.channels.MemoryChannel;

public class JetlangSessionChannels {

    public final Channel<JetlangSession> SessionOpen = new MemoryChannel<JetlangSession>();
    public final Channel<JetlangSession> SessionClose = new MemoryChannel<JetlangSession>();

    public void publishNewSession(JetlangSession session) {
        SessionOpen.publish(session);
    }

    public void publishSessionEnd(JetlangSession session) {
        SessionClose.publish(session);
    }
}
