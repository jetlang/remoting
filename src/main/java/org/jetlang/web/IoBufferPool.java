package org.jetlang.web;

import org.jetlang.fibers.NioFiber;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public interface IoBufferPool {


    ByteBuffer beginRead(int readBufferSizeInBytes);

    ByteBuffer returnBufferAfterRead(ByteBuffer bb);

    void returnReadBufferOnClose(ByteBuffer bb);

    ByteBuffer beginWebSocketWrite(int minSize);

    void endWebSocketWrite(ByteBuffer bb);

    interface Factory {
        IoBufferPool createFor(SocketChannel channel, NioFiber fiber);
    }

    class Default implements Factory {

        private final ThreadLocal<ByteBuffer> threadLocalWriteBuffer = new ThreadLocal<>();

        @Override
        public IoBufferPool createFor(SocketChannel channel, NioFiber fiber) {
            return new IoBufferPool() {
                @Override
                public ByteBuffer beginWebSocketWrite(int minSize) {
                    ByteBuffer reusedWsWriteBuffer = threadLocalWriteBuffer.get();
                    if (reusedWsWriteBuffer == null || reusedWsWriteBuffer.capacity() < minSize) {
                        reusedWsWriteBuffer = NioReader.bufferAllocateDirect(minSize);
                        threadLocalWriteBuffer.set(reusedWsWriteBuffer);
                    } else {
                        reusedWsWriteBuffer.clear();
                    }
                    return reusedWsWriteBuffer;
                }

                @Override
                public void endWebSocketWrite(ByteBuffer bb) {

                }

                @Override
                public ByteBuffer beginRead(int readBufferSizeInBytes) {
                    return NioReader.bufferAllocate(readBufferSizeInBytes);
                }

                @Override
                public ByteBuffer returnBufferAfterRead(ByteBuffer bb) {
                    //reuse for next read
                    return bb;
                }

                @Override
                public void returnReadBufferOnClose(ByteBuffer bb) {

                }
            };
        }
    }
}
