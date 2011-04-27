package com.jetlang.remote.core;

import java.io.IOException;
import java.io.OutputStream;

/**
 * User: mrettig
 * Date: 4/27/11
 * Time: 10:36 AM
 */
public class ByteArrayBuffer {

    private byte[] buffer = new byte[1024];
    private int position;


    public void reset() {
        position = 0;
    }

    public void appendIntAsByte(int msgType) {
        resize(1);
        buffer[position++] = (byte) msgType;
    }

    private void resize(int i) {
        if (position + i >= buffer.length) {
            byte[] newBuffer = new byte[buffer.length + i];
            System.arraycopy(buffer, 0, newBuffer, 0, position);
            buffer = newBuffer;
        }
    }

    public void appendInt(int v) {
        appendIntAsByte((v >>> 24) & 0xFF);
        appendIntAsByte((v >>> 16) & 0xFF);
        appendIntAsByte((v >>> 8) & 0xFF);
        appendIntAsByte((v >>> 0) & 0xFF);
    }

    public void append(byte[] bytes, int offset, int length) {
        resize(length);
        System.arraycopy(bytes, offset, buffer, position, length);
        position += length;
    }

    public void append(byte[] bytes) {
        append(bytes, 0, bytes.length);
    }

    public void flushTo(OutputStream socketOutputStream) throws IOException {
        try {
            socketOutputStream.write(buffer, 0, position);
        } finally {
            position = 0;
        }
    }

}
