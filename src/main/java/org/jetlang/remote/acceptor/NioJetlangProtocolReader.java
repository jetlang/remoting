package org.jetlang.remote.acceptor;

import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.core.JetlangRemotingProtocol;
import org.jetlang.remote.core.ObjectByteReader;
import org.jetlang.remote.core.ReadTimeoutEvent;
import org.jetlang.remote.core.TopicReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class NioJetlangProtocolReader<T> {

    private final JetlangRemotingProtocol<T> protocol;
    private final SocketChannel accept;
    private final Runnable onActivityTimeout;
    private final JetlangRemotingProtocol.Handler<T> session;
    private JetlangRemotingProtocol.State nextCommand;
    private long lastReadMs = System.currentTimeMillis();

    public NioJetlangProtocolReader(SocketChannel accept, JetlangRemotingProtocol.Handler<T> session, ObjectByteReader<T> reader, TopicReader charset, Runnable onActivityTimeout) {
        this.onActivityTimeout = onActivityTimeout;
        this.protocol = new JetlangRemotingProtocol<T>(session, reader, charset);
        this.accept = accept;
        this.nextCommand = protocol.root;
        this.session = session;
    }

    public boolean read() {
        try {
            while (true) {
                //must get latest buffer b/c it may have been resized
                final ByteBuffer buffer = protocol.buffer;
                final int read = this.accept.read(buffer);
                switch (read) {
                    case -1:
                        return false;
                    case 0:
                        return true;
                    default:
                        buffer.flip();
                        while (buffer.remaining() >= nextCommand.getRequiredBytes()) {
                            nextCommand = nextCommand.run();
                        }
                        buffer.compact();
                        if (nextCommand.getRequiredBytes() > buffer.capacity()) {
                            protocol.resizeBuffer(nextCommand.getRequiredBytes());
                        }
                        lastReadMs = System.currentTimeMillis();
                }
            }
        } catch (IOException var6) {
            session.onClientDisconnect(var6);
            return false;
        }
    }
    public void checkForReadTimeout(int readTimeoutInMs) {
        if (System.currentTimeMillis() - lastReadMs > readTimeoutInMs) {
            lastReadMs = System.currentTimeMillis();
            onActivityTimeout.run();
        }
    }
}
