package org.jetlang.web;

import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class NioReader<T> implements NioChannelHandler {

    private final HeaderReader<T> headerReader;
    private ByteBuffer bb;
    private final SocketChannel channel;
    private final int maxReadLoops;
    private State current;

    public NioReader(SocketChannel channel, NioFiber fiber, NioControls controls, HttpRequestHandler<T> handler, int readBufferSizeInBytes, int maxReadLoops, SessionFactory<T> fact, SessionDispatcherFactory<T> dispatcherFact) {
        this.channel = channel;
        this.maxReadLoops = maxReadLoops;
        this.headerReader = new HeaderReader<>(channel, fiber, controls, handler, fact, dispatcherFact);
        this.current = headerReader.start();
        this.bb = bufferAllocate(readBufferSizeInBytes);
    }

    public boolean onRead() throws IOException {
        for (int i = 0; i < maxReadLoops; i++) {
            int read = channel.read(bb);
            if (read < 0) {
                return false;
            }
            if (read > 0) {
                bb.flip();
                State result = current;
                while (result != null) {
                    result = current.process(bb);
                    if (result != null) {
                        current = result;
                    }
                }
                bb.compact();
                if (bb.remaining() == 0 || bb.remaining() < current.minRequiredBytes()) {
                    ByteBuffer resize = bufferAllocate(bb.capacity() + Math.max(1024, current.minRequiredBytes()));
                    bb.flip();
                    bb = resize.put(bb);
                }
            } else {
                break;
            }
        }
        return current.continueReading();
    }

    public static ByteBuffer bufferAllocate(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
    }

    public static ByteBuffer bufferAllocateDirect(int size) {
        return ByteBuffer.allocateDirect(size).order(ByteOrder.BIG_ENDIAN);
    }


    public void onClosed() {
        this.headerReader.onClose();
        current.onClosed();
    }

    public interface State {

        default int minRequiredBytes() {
            return 1;
        }

        default State process(ByteBuffer bb) {
            if (bb.remaining() < minRequiredBytes()) {
                return null;
            }
            return processBytes(bb);
        }

        State processBytes(ByteBuffer bb);

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

    @Override
    public boolean onSelect(NioFiber nioFiber, NioControls nioControls, SelectionKey selectionKey) {
        try {
            return onRead();
        } catch (IOException failed) {
            return false;
        } catch (Throwable processingException) {
            headerReader.onException(processingException, channel);
            return false;
        }

    }

    @Override
    public SelectableChannel getChannel() {
        return channel;
    }

    @Override
    public int getInterestSet() {
        return SelectionKey.OP_READ;
    }

    @Override
    public void onEnd() {
        onClosed();
    }

    @Override
    public void onSelectorEnd() {
        try {
            channel.close();
        } catch (IOException e) {
        }
        onEnd();
    }
}
