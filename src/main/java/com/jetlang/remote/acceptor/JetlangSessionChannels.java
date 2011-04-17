package com.jetlang.remote.acceptor;

import org.jetlang.channels.Channel;
import org.jetlang.channels.MemoryChannel;

public class JetlangSessionChannels {

    public final Channel<JetlangSession> SessionOpen = new MemoryChannel<JetlangSession>();

    public void publishNewSession(JetlangSession session) {
        SessionOpen.publish(session);
    }

}
