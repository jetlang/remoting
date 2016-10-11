package org.jetlang.remote.example.ws;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class WebSocketConnection {

    private static final byte OPCODE_CONT = 0x0;
    private static final byte OPCODE_TEXT = 0x1;
    private static final byte OPCODE_BINARY = 0x2;
    private static final byte OPCODE_CLOSE = 0x8;
    private static final byte OPCODE_PING = 0x9;
    private static final byte OPCODE_PONG = 0xA;
    public static final byte[] empty = new byte[0];
    private final Charset charset;
    private final NioWriter writer;
    private static final SizeType[] sizes = SizeType.values();

    public WebSocketConnection(Charset charset, NioWriter writer) {
        this.charset = charset;
        this.writer = writer;
    }

    public SendResult send(String msg) {
        final byte[] bytes = msg.getBytes(charset);
        return send(OPCODE_TEXT, bytes);
    }

    enum SizeType {
        Small(125, 1) {
            @Override
            void write(ByteBuffer bb, int length) {
                bb.put((byte) length);
            }
        },
        Medium(65535, 3) {
            @Override
            void write(ByteBuffer bb, int length) {
                bb.put((byte) 126);
                bb.put((byte) (length >>> 8));
                bb.put((byte) length);
            }
        },
        Large(Integer.MAX_VALUE, 9) {
            @Override
            void write(ByteBuffer bb, int length) {
                bb.put((byte) 127);
                bb.putLong(length);
            }
        };

        public int max;
        private final int bytes;

        SizeType(int max, int bytes) {
            this.max = max;
            this.bytes = bytes;
        }

        abstract void write(ByteBuffer bb, int length);
    }

    private SendResult send(byte opCode, byte[] bytes) {
        final int length = bytes.length;
        byte header = 0;
        header |= 1 << 7;
        header |= opCode % 128;
        SizeType sz = findSize(length);
        ByteBuffer bb = NioReader.bufferAllocate(1 + length + sz.bytes);
        bb.put(header);
        sz.write(bb, length);
        if (bytes.length > 0) {
            bb.put(bytes);
        }
        bb.flip();
        return writer.send(bb);
    }

    private static SizeType findSize(int length) {
        for (SizeType size : sizes) {
            if (length <= size.max) {
                return size;
            }
        }
        throw new RuntimeException(length + " invalid ");
    }

    void sendClose() {
        send(OPCODE_CLOSE, empty);
    }

    public void close() throws IOException {
        writer.close();
    }
}
