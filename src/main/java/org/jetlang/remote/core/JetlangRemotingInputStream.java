package org.jetlang.remote.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

public class JetlangRemotingInputStream {
    private final InputStream inputStream;
    private final JetlangRemotingProtocol<?> protocol;
    private final Runnable onReadTimeout;
    JetlangRemotingProtocol.State nextCommand;

    public JetlangRemotingInputStream(InputStream inputStream, JetlangRemotingProtocol<?> protocol, Runnable onReadTimeout) {
        this.inputStream = inputStream;
        this.protocol = protocol;
        this.onReadTimeout = onReadTimeout;
        nextCommand = protocol.root;
    }

    public boolean readFromStream() throws IOException {
        final ByteBuffer buffer = protocol.buffer;
        int read = attemptRead();
        if (read < 0) {
            return false;
        }
        buffer.position(buffer.position() + read);
        buffer.flip();
        while (buffer.remaining() >= nextCommand.getRequiredBytes()) {
            nextCommand = nextCommand.run();
        }
        buffer.compact();
        if (nextCommand.getRequiredBytes() > buffer.capacity()) {
            protocol.resizeBuffer(nextCommand.getRequiredBytes());
        }
        return true;
    }

    private int attemptRead() throws IOException {
        final ByteBuffer buffer = protocol.buffer;
        while (true) {
            try {
                return inputStream.read(protocol.bufferArray, buffer.position(), buffer.remaining());
            } catch (SocketTimeoutException timeout) {
                onReadTimeout.run();
            }
        }
    }
}
