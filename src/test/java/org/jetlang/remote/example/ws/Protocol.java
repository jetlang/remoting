package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Protocol {

    private ByteBuffer bb = ByteBuffer.allocate(1);
    private final SocketChannel channel;
    private State current;

    public Protocol(SocketChannel channel, NioFiber fiber, NioControls controls, WebSocketHandler handler) {
        this.channel = channel;
        this.current = new HeaderReader(channel, fiber, controls, handler).start();
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
            System.out.println("pre = " + bb);
            bb.compact();
            System.out.println("compacted bb = " + bb + " " + current);
            if (bb.remaining() == 0 || bb.remaining() < current.minRequiredBytes()) {
                ByteBuffer resize = ByteBuffer.allocate(bb.capacity() + Math.max(1024, current.minRequiredBytes()));
                bb.flip();
                bb = resize.put(bb);
                System.out.println("bb = " + bb);
            }
        }
        return true;
    }

    interface State {

        default int minRequiredBytes() {
            return 1;
        }

        default void begin(ByteBuffer bb) throws IOException {

        }

        State processBytes(ByteBuffer bb);

        default void end() {

        }
    }

}
