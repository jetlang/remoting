package org.jetlang.remote.acceptor;

import org.jetlang.fibers.NioChannelHandler;
import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.core.JetlangRemotingProtocol;
import org.jetlang.remote.core.ObjectByteReader;
import org.jetlang.remote.core.RawMsgHandler;
import org.jetlang.remote.core.ReadTimeoutEvent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class NioJetlangChannelHandler implements NioChannelHandler {

    private final JetlangRemotingProtocol protocol;
    private final SocketChannel accept;
    private final JetlangMessageHandler session;
    private final Runnable onEnd;
    private JetlangRemotingProtocol.State nextCommand;
    private long lastReadMs = System.currentTimeMillis();

    public NioJetlangChannelHandler(SocketChannel accept, JetlangMessageHandler session, ObjectByteReader reader,
                                    Runnable onEnd, Charset charset, RawMsgHandler rawMsgHandler) {
        this.session = session;
        this.onEnd = onEnd;
        this.protocol = new JetlangRemotingProtocol(session, reader, charset, rawMsgHandler.enabled());
        this.accept = accept;
        this.nextCommand = protocol.root;
    }

    public Result onSelect(NioFiber nioFiber, NioControls controls, SelectionKey key) {
        try {
            while (true) {
                //must get latest buffer b/c it may have been resized
                final ByteBuffer buffer = protocol.buffer;
                final int e = this.accept.read(buffer);
                switch (e) {
                    case -1:
                        return Result.CloseSocket;
                    case 0:
                        return Result.Continue;
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
            return Result.CloseSocket;
        }
    }

    public SelectableChannel getChannel() {
        return this.accept;
    }

    public int getInterestSet() {
        return SelectionKey.OP_READ;
    }

    public void onEnd() {
        try {
            onEnd.run();
            this.accept.close();
        } catch (IOException var2) {
            throw new RuntimeException(var2);
        }
    }

    public void onSelectorEnd() {
        onEnd();
    }

    public void checkForReadTimeout(int readTimeoutInMs) {
        if (System.currentTimeMillis() - lastReadMs > readTimeoutInMs) {
            lastReadMs = System.currentTimeMillis();
            session.onReadTimeout(new ReadTimeoutEvent());
        }
    }
}
