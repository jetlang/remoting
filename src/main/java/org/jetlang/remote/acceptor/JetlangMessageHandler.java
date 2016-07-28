package org.jetlang.remote.acceptor;

import org.jetlang.remote.core.JetlangRemotingProtocol;
import org.jetlang.remote.core.ReadTimeoutEvent;

public interface JetlangMessageHandler extends JetlangRemotingProtocol.Handler {
    Object getSessionId();

    void onReadTimeout(ReadTimeoutEvent readTimeoutEvent);
}
