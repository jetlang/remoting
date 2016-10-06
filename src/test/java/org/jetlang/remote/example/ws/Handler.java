package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import java.nio.channels.SocketChannel;

public interface Handler {
    Protocol.State start(HttpRequest headers, NioControls controls, SocketChannel channel, NioFiber fiber);
}
