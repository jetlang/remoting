package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Protocol {

    private final ByteBuffer bb = ByteBuffer.allocate(1024);
    private final SocketChannel channel;
    private State current;

    public Protocol(SocketChannel channel, NioFiber fiber, NioControls controls, WebsocketConnectionFactory fact, WebSocketHandler handler) {
        this.channel = channel;
        this.current = new HeaderReader(channel, fiber, controls, fact, handler).start();
    }

    public boolean onRead() throws IOException {
        while (channel.read(bb) > 0) {
            bb.flip();
            current.begin(bb);
            State result = current;
            while (result != null) {
                result = current.processBytes(bb);
                if (result != null) {
                    current = result;
                }
            }
            current.end();
            bb.compact();
        }
        return true;
    }

    interface State {
        default void begin(ByteBuffer bb) throws IOException {

        }

        State processBytes(ByteBuffer bb);

        default void end() {

        }
    }

}
