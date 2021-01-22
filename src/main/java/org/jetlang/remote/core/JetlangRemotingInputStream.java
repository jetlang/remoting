package org.jetlang.remote.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class JetlangRemotingInputStream {
    private final Reader inputStream;
    private final JetlangRemotingProtocol<?> protocol;
    private final Runnable onReadTimeout;
    JetlangRemotingProtocol.State nextCommand;

    public JetlangRemotingInputStream(SocketChannel newSocket, JetlangRemotingProtocol protocol, Runnable onReadTimeout) {
        this(buffer -> {
            int read = newSocket.read(buffer);
            buffer.flip();
            return read;
        }, protocol, onReadTimeout);
    }

    public interface Reader {
        int read(ByteBuffer buffer) throws IOException;
    }

    public JetlangRemotingInputStream(InputStream inputStream, JetlangRemotingProtocol<?> protocol, Runnable onReadTimeout){
        this(createReader(inputStream), protocol, onReadTimeout);
    }

    public JetlangRemotingInputStream(Reader inputStream, JetlangRemotingProtocol<?> protocol, Runnable onReadTimeout) {
        this.inputStream = inputStream;
        this.protocol = protocol;
        this.onReadTimeout = onReadTimeout;
        nextCommand = protocol.root;
    }

    private static Reader createReader(InputStream inputStream) {
        return new Reader() {
            private byte[] toRead = new byte[50];

            @Override
            public int read(ByteBuffer buffer) throws IOException {
                if (toRead.length < buffer.remaining()) {
                    toRead = new byte[buffer.remaining()];
                }
                int read = inputStream.read(toRead, 0, buffer.remaining());
                if (read > 0) {
                    buffer.put(toRead, 0, read);
                    buffer.flip();
                }
                return read;
            }
        };
    }

    public boolean readFromStream() throws IOException {
        final ByteBuffer buffer = protocol.buffer;
        int read = attemptRead(buffer);
        if (read < 0) {
            return false;
        }
        while (buffer.remaining() >= nextCommand.getRequiredBytes()) {
            nextCommand = nextCommand.run();
        }
        buffer.compact();
        if (nextCommand.getRequiredBytes() > buffer.capacity()) {
            protocol.resizeBuffer(nextCommand.getRequiredBytes());
        }
        return true;
    }

    private int attemptRead(ByteBuffer buff) throws IOException {
        while (true) {
            try {
                return inputStream.read(buff);
            } catch (SocketTimeoutException timeout) {
                onReadTimeout.run();
            }
        }
    }
}
