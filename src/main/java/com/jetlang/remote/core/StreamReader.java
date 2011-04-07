package com.jetlang.remote.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;

public class StreamReader {

    private byte[] readBuffer = new byte[1024 * 64];
    private final InputStream stream;
    private final Charset charset;
    private final ObjectByteReader reader;
    private final Runnable onReadTimeout;

    public StreamReader(InputStream stream, Charset charset, ObjectByteReader reader, Runnable onReadTimeout) {
        this.reader = reader;
        this.onReadTimeout = onReadTimeout;
        this.stream = stream;
        this.charset = charset;
    }

    public int readByteAsInt() throws IOException {
        while (true) {
            try {
                int read = stream.read();
                if (read < 0) {
                    throw new IOException("Read End");
                }
                return read;
            } catch (SocketTimeoutException timeout) {
                onReadTimeout.run();
            }
        }
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
        while (true) {
            try {
                int read = stream.read(buffer, offset, length);
                if (read < 0) {
                    throw new IOException("End Read");
                }
                return read;
            } catch (SocketTimeoutException timeout) {
                onReadTimeout.run();
            }
        }
    }

    public int readInt() throws IOException {
        int ch1 = readByteAsInt();
        int ch2 = readByteAsInt();
        int ch3 = readByteAsInt();
        int ch4 = readByteAsInt();
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    public Object readObject(String fromTopic, int msgSize) throws IOException {
        byte[] buffer = fillBuffer(msgSize);
        return reader.readObject(fromTopic, buffer, 0, msgSize);
    }
}
