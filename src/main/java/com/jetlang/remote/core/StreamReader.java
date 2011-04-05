package com.jetlang.remote.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public class StreamReader {

    private byte[] readBuffer = new byte[1024 * 64];
    private final InputStream stream;
    private final Charset charset = Charset.forName("US-ASCII");

    public StreamReader(InputStream stream) {
        this.stream = stream;
    }

    public int readByteAsInt() throws IOException {
        int read = stream.read();
        if (read < 0) {
            throw new IOException("Read End");
        }
        return read;
    }

    public String readString(int topicSizeInBytes) throws IOException {
        byte[] buffer = fillBuffer(topicSizeInBytes);
        return new String(buffer, 0, topicSizeInBytes, charset);
    }

    private byte[] fillBuffer(int totalReadSize) throws IOException {
        if (readBuffer.length < totalReadSize) {
            readBuffer = new byte[totalReadSize];
        }
        int read = 0;
        do {
            read += read(readBuffer, read, totalReadSize - read);
        } while (totalReadSize - read > 0);
        return readBuffer;
    }

    private int read(byte[] buffer, int offset, int length) throws IOException {
        int read = stream.read(buffer, offset, length);
        if (read < 0) {
            throw new IOException("End Read");
        }
        return read;
    }
}
