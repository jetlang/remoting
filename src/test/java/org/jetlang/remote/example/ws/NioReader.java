package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;

public class NioReader {

    private final HeaderReader headerReader;
    private ByteBuffer bb;
    private final SocketChannel channel;
    private final int maxReadLoops;
    private State current;

    public NioReader(SocketChannel channel, NioFiber fiber, NioControls controls, HttpRequestHandler handler, int readBufferSizeInBytes, int maxReadLoops) {
        this.channel = channel;
        this.maxReadLoops = maxReadLoops;
        this.headerReader = new HeaderReader(channel, fiber, controls, handler);
        this.current = headerReader.start();
        this.bb = bufferAllocate(readBufferSizeInBytes);
    }

    public boolean onRead() throws IOException {
        for (int i = 0; i < maxReadLoops && channel.read(bb) > 0; i++) {
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
            bb.compact();
            if (bb.remaining() == 0 || bb.remaining() < current.minRequiredBytes()) {
                ByteBuffer resize = bufferAllocate(bb.capacity() + Math.max(1024, current.minRequiredBytes()));
                bb.flip();
                bb = resize.put(bb);
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
