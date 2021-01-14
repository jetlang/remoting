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

import static org.jetlang.web.WebSocketConnectionImpl.findSize;

public class NioWriter {

    private final SocketChannel channel;
    private final NioFiber fiber;
    private final Object writeLock;
    private final IoBufferPool ioBufferPool;
    private NioFiberImpl.BufferedWrite<SocketChannel> bufferedWrite;
    private boolean closed = false;
    private final SocketAddress remoteAddress;

    public NioWriter(Object lock, SocketChannel channel, NioFiber fiber, IoBufferPool buffer) {
        this.channel = channel;
        this.fiber = fiber;
        this.writeLock = lock;
        //this could return null if the address is no longer connected
        //keep a reference so it can be logged even after disconnect.
        this.remoteAddress = getRemote(channel);
        this.ioBufferPool = buffer;
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
            return doSend(bb);
        }
    }

    public int getTotalBytesBuffered() {
        synchronized (writeLock) {
            if (bufferedWrite != null) {
                return bufferedWrite.getBuffer().remaining();
            }
        }
        return 0;
    }

    private SendResult doSend(ByteBuffer bb) {
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
            public Result onSelect(NioFiber nioFiber, NioControls controls, SelectionKey key) {
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

    public SendResult sendWsMsg(byte opCode, byte[] bytes, int offset, int length, byte[] maskBytes) {
        byte header = 0;
        header |= 1 << 7;
        header |= opCode % 128;
        WebSocketConnectionImpl.SizeType sz = findSize(length);
        synchronized (writeLock) {
            ByteBuffer bb = ioBufferPool.beginWrite(1 + length + sz.bytes + maskBytes.length);
            bb.put(header);
            sz.write(bb, length, maskBytes.length > 0);
            if (maskBytes.length > 0) {
                bb.put(maskBytes);
            }
            if (bytes.length > 0 && maskBytes.length == 0) {
                bb.put(bytes, offset, length);
            } else {
                for (int i = 0; i < length; ++i) {
                    bb.put((byte) (bytes[i + offset] ^ maskBytes[i % 4]));
                }
            }
            bb.flip();
            SendResult sendResult = doSend(bb);
            ioBufferPool.returnBufferAfterWrite(bb);
            return sendResult;
        }
    }

    public SendResult send(byte[] toSend, int start, int length) {
        synchronized (writeLock){
            ByteBuffer bb = ioBufferPool.beginWrite(length);
            bb.put(toSend, start, length);
            bb.flip();
            SendResult sendResult = doSend(bb);
            ioBufferPool.returnBufferAfterWrite(bb);
            return sendResult;
        }
    }
}
