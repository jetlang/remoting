package org.jetlang.web;

import org.jetlang.fibers.NioFiber;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public interface IoBufferPool {

    ByteBuffer getWebsocketWriteBuffer(int minSize);

    ByteBuffer getReadBuffer(int readBufferSizeInBytes);

    void returnReadBufferOnClose(ByteBuffer bb);

    interface Factory {
        IoBufferPool createFor(SocketChannel channel, NioFiber fiber);
    }

    class PerSocket implements Factory {

        ByteBuffer reusedWsWriteBuffer;

        @Override
        public IoBufferPool createFor(SocketChannel channel, NioFiber fiber) {
            return new IoBufferPool() {
                @Override
                public ByteBuffer getWebsocketWriteBuffer(int minSize) {
                    if (reusedWsWriteBuffer == null || reusedWsWriteBuffer.capacity() < minSize) {
                        reusedWsWriteBuffer = NioReader.bufferAllocateDirect(minSize);
                    } else {
                        reusedWsWriteBuffer.clear();
                    }
                    return reusedWsWriteBuffer;
                }

                @Override
                public ByteBuffer getReadBuffer(int readBufferSizeInBytes) {
                    return NioReader.bufferAllocate(readBufferSizeInBytes);
                }

                @Override
                public void returnReadBufferOnClose(ByteBuffer bb) {

                }
            };
        }
    }
}
