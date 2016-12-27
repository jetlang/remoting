package org.jetlang.web;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.fibers.NioFiberImpl;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

public class NioWriter {

    private final SocketChannel channel;
    private final NioFiber fiber;
    private final Object writeLock;
    private NioFiberImpl.BufferedWrite<SocketChannel> bufferedWrite;
    private boolean closed = false;
    private final SocketAddress remoteAddress;

    public NioWriter(Object lock, SocketChannel channel, NioFiber fiber) {
        this.channel = channel;
        this.fiber = fiber;
        this.writeLock = lock;
        //this could return null if the address is no longer connected
        //keep a reference so it can be logged even after disconnect.
        this.remoteAddress = getRemote(channel);
    }

    private static SocketAddress getRemote(SocketChannel channel) {
        try {
            return channel.getRemoteAddress();
        } catch (IOException e) {
            return null;
        }
    }

    public SendResult send(ByteBuffer bb) {
        synchronized (writeLock) {
            if (closed) {
                bb.position(bb.position() + bb.remaining());
                return SendResult.Closed;
            }
            if (bufferedWrite != null) {
                if (channel.isOpen() && channel.isRegistered()) {
                    int toBuffer = bb.remaining();
                    int totalBuffered = bufferedWrite.buffer(bb);
                    return new SendResult.Buffered(toBuffer, totalBuffered);
                } else {
                    bb.position(bb.position() + bb.remaining());
                    return SendResult.Closed;
                }
            }
            try {
                NioFiberImpl.writeAll(channel, bb);
            } catch (IOException e) {
                attemptCloseOnNioFiber();
                bb.position(bb.position() + bb.remaining());
                return new SendResult.FailedWithError(e);
            }
            if (!bb.hasRemaining()) {
                return SendResult.SUCCESS;
            }
            bufferedWrite = new NioFiberImpl.BufferedWrite<SocketChannel>(channel, new NioFiberImpl.WriteFailure() {
                @Override
                public <T extends SelectableChannel & WritableByteChannel> void onFailure(IOException e, T t, ByteBuffer byteBuffer) {
                    attemptCloseOnNioFiber();
                }
            }, new NioFiberImpl.OnBuffer() {
                @Override
                public <T extends SelectableChannel & WritableByteChannel> void onBufferEnd(T t) {
                    bufferedWrite = null;
                }

                @Override
                public <T extends SelectableChannel & WritableByteChannel> void onBuffer(T t, ByteBuffer byteBuffer) {
                }
            }) {
                @Override
                public boolean onSelect(NioFiber nioFiber, NioControls controls, SelectionKey key) {
                    synchronized (writeLock) {
                        return super.onSelect(nioFiber, controls, key);
                    }
                }
            };
            int remaining = bb.remaining();
            int totalBuffered = bufferedWrite.buffer(bb);
            fiber.execute((c) -> {
                if (c.isRegistered(channel)) {
                    c.addHandler(bufferedWrite);
                } else {
                    synchronized (writeLock) {
                        bufferedWrite = null;
                    }
                }
            });
            return new SendResult.Buffered(remaining, totalBuffered);
        }
    }

    private void attemptCloseOnNioFiber() {
        if (!closed) {
            fiber.execute((c) -> c.close(channel));
            closed = true;
        }
    }

    public void close() {
        try {
            channel.close();
        } catch (IOException e) {

        }
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }
}
