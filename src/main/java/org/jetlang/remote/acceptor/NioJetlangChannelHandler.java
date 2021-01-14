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
import java.nio.charset.Charset;

public class NioJetlangChannelHandler<T> implements NioChannelHandler {

    private final NioJetlangProtocolReader protocol;
    private final SocketChannel accept;
    private final Runnable onEnd;

    public NioJetlangChannelHandler(SocketChannel accept, JetlangMessageHandler<T> session, ObjectByteReader<T> reader, Runnable onEnd, TopicReader charset) {
        this.onEnd = onEnd;
        this.protocol = new NioJetlangProtocolReader<T>(accept, session, reader, charset);
        this.accept = accept;
    }

    @Override
    public Result onSelect(NioFiber nioFiber, NioControls controls, SelectionKey key) {
        boolean result = protocol.read();
        if(result){
            return Result.Continue;
        }
        else {
            return Result.CloseSocket;
        }
    }

    @Override
    public SelectableChannel getChannel() {
        return this.accept;
    }

    @Override
    public int getInterestSet() {
        return SelectionKey.OP_READ;
    }

    @Override
    public void onEnd() {
        try {
            onEnd.run();
            this.accept.close();
        } catch (IOException var2) {
            throw new RuntimeException(var2);
        }
    }

    @Override
    public void onSelectorEnd() {
        onEnd();
    }

    public void checkForReadTimeout(int readTimeoutInMs) {
        protocol.checkForReadTimeout(readTimeoutInMs);
    }
}
