package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.fibers.NioFiberImpl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;

public class WebSocketConnection {

    private static final byte OPCODE_CONT = 0x0;
    private static final byte OPCODE_TEXT = 0x1;
    private static final byte OPCODE_BINARY = 0x2;
    private static final byte OPCODE_CLOSE = 0x8;
    private static final byte OPCODE_PING = 0x9;
    private static final byte OPCODE_PONG = 0xA;
    public static final byte[] empty = new byte[0];
    private final SocketChannel channel;
    private final NioControls controls;
    private final Charset charset;
    private final NioFiber fiber;
    private final Object writeLock = new Object();
    private NioFiberImpl.BufferedWrite<SocketChannel> bufferedWrite;

    public WebSocketConnection(HttpRequest headers, SocketChannel channel, NioControls controls, Charset charset, NioFiber fiber) {
        this.channel = channel;
        this.controls = controls;
        this.charset = charset;
        this.fiber = fiber;
    }

    public void send(String msg) {
        final byte[] bytes = msg.getBytes(charset);
        send(OPCODE_TEXT, bytes);
    }

    private void send(byte opCode, byte[] bytes) {
        final int length = bytes.length;
        byte header = 0;
        header |= 1 << 7;
        header |= opCode % 128;
        ByteBuffer bb = NioReader.bufferAllocate(2 + length);
        bb.put(header);
        bb.put((byte) length);
        if (bytes.length > 0) {
            bb.put(bytes);
        }
        bb.flip();
        synchronized (writeLock) {
            if (bufferedWrite != null) {
                if (channel.isOpen() && channel.isRegistered())
                    bufferedWrite.buffer(bb);
                return;
            }
            try {
                writeAll(channel, bb);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (!bb.hasRemaining()) {
                //System.out.println("sent : " + bytes.length);
                return;
            }
            bufferedWrite = new NioFiberImpl.BufferedWrite<SocketChannel>(channel, new NioFiberImpl.WriteFailure() {
                @Override
                public <T extends SelectableChannel & WritableByteChannel> void onFailure(IOException e, T t, ByteBuffer byteBuffer) {
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

                @Override
                public void onEnd() {
                }
            };
            bufferedWrite.buffer(bb);
            //fiber.execute((c)->{
            if (channel.isOpen() && channel.isRegistered()) {
                controls.addHandler(bufferedWrite);
                //c.addHandler(bufferedWrite);
                System.out.println("Added buffer to niofiber");
            } else {
                System.out.println("Channel is closed.");
            }
            //});
        }
    }

    public static void writeAll(WritableByteChannel channel, ByteBuffer data) throws IOException {
        int write;
        do {
            write = channel.write(data);
        } while (write != 0 && data.remaining() > 0);
    }


    void sendClose() {
        send(OPCODE_CLOSE, empty);
    }
}
