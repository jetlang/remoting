package org.jetlang.remote.core;

import java.nio.ByteBuffer;

public class ByteArrayByteBuffer extends ByteArrayBuffer {

    private ByteBuffer byteBuffer;

    public ByteArrayByteBuffer() {
        byteBuffer = ByteBuffer.wrap(buffer);
    }

    @Override
    protected void afterResize() {
        super.afterResize();
        byteBuffer = ByteBuffer.wrap(buffer);
    }
}
