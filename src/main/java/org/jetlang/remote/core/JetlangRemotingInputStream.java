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
    private byte[] toRead = new byte[0];

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
        if(toRead.length < buffer.remaining()){
            toRead = new byte[buffer.remaining()];
        }
        while (true) {
            try {
                int read = inputStream.read(toRead, 0, buffer.remaining());
                if(read > 0){
                    buffer.put(toRead, 0, read);
                    buffer.flip();
                }
                return read;
            } catch (SocketTimeoutException timeout) {
                onReadTimeout.run();
            }
        }
    }
}
