package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.util.Map;

public class NioReader {

    private final HeaderReader headerReader;
    private ByteBuffer bb = bufferAllocate(1);
    private final SocketChannel channel;
    private State current;

    public NioReader(SocketChannel channel, NioFiber fiber, NioControls controls, Map<String, Handler> handler) {
        this.channel = channel;
        this.headerReader = new HeaderReader(channel, fiber, controls, handler);
        this.current = headerReader.start();
    }

    public boolean onRead() throws IOException {
        while (channel.read(bb) > 0) {
            bb.flip();
            current.begin(bb);
            State result = current;
            while (result != null) {
                result = current.process(bb);
                if (result != null) {
                    current = result;
                }
            }
            current.end();
            //System.out.println("pre = " + bb);
            bb.compact();
            //System.out.println("compacted bb = " + bb + " " + current);
            if (bb.remaining() == 0 || bb.remaining() < current.minRequiredBytes()) {
                ByteBuffer resize = bufferAllocate(bb.capacity() + Math.max(1024, current.minRequiredBytes()));
                bb.flip();
                bb = resize.put(bb);
                System.out.println("resize = " + bb);
            }
        }
        return current.continueReading();
    }

    public static ByteBuffer bufferAllocate(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
    }

    public void onClosed() {
        current.onClosed();
    }

    interface State {

        default int minRequiredBytes() {
            return 1;
        }

        default void begin(ByteBuffer bb) throws IOException {

        }

        default State process(ByteBuffer bb) {
            if (bb.remaining() < minRequiredBytes()) {
                return null;
            }
            return processBytes(bb);
        }

        State processBytes(ByteBuffer bb);

        default void end() {

        }

        default boolean continueReading() {
            return true;
        }

        default void onClosed() {

        }
    }

    public static class Close implements State {
        @Override
        public State processBytes(ByteBuffer bb) {
            bb.position(bb.position() + bb.remaining());
            return null;
        }

        @Override
        public boolean continueReading() {
            return false;
        }

        @Override
        public void onClosed() {
        }
    }

    public static final State CLOSE = new Close();
}
