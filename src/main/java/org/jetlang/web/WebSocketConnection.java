package org.jetlang.web;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class WebSocketConnection {

    public static final byte OPCODE_CONT = 0x0;
    public static final byte OPCODE_TEXT = 0x1;
    public static final byte OPCODE_BINARY = 0x2;
    public static final byte OPCODE_CLOSE = 0x8;
    public static final byte OPCODE_PING = 0x9;
    public static final byte OPCODE_PONG = 0xA;
    public static final byte[] empty = new byte[0];
    private static final Charset charset = Charset.forName("UTF-8");
    private final NioWriter writer;
    private final byte[] maskingBytes;
    private static final SizeType[] sizes = SizeType.values();

    public WebSocketConnection(NioWriter writer, byte[] maskingBytes) {
        this.writer = writer;
        this.maskingBytes = maskingBytes;
    }

    public SocketAddress getRemoteAddress() {
        try {
            return writer.getChannel().getRemoteAddress();
        } catch (IOException e) {
            return null;
        }
    }

    public SendResult send(String msg) {
        final byte[] bytes = msg.getBytes(charset);
        return send(OPCODE_TEXT, bytes, 0, bytes.length);
    }

    public SendResult sendPong(byte[] bytes, int offset, int length) {
        return send(OPCODE_PONG, bytes, offset, length);
    }


    enum SizeType {
        Small(125, 1) {
            @Override
            void write(ByteBuffer bb, int length, boolean mask) {
                bb.put(setMask((byte) length, mask));
            }
        },
        Medium(65535, 3) {
            @Override
            void write(ByteBuffer bb, int length, boolean mask) {
                bb.put(setMask((byte) 126, mask));
                bb.put((byte) (length >>> 8));
                bb.put((byte) length);
            }
        },
        Large(Integer.MAX_VALUE, 9) {
            @Override
            void write(ByteBuffer bb, int length, boolean mask) {
                bb.put(setMask((byte) 127, mask));
                bb.putLong(length);
            }
        };

        public int max;
        private final int bytes;

        SizeType(int max, int bytes) {
            this.max = max;
            this.bytes = bytes;
        }

        private static byte setMask(byte b, boolean mask) {
            if (mask) {
                b |= 1 << 7;
            }
            return b;
        }

        abstract void write(ByteBuffer bb, int length, boolean mask);
    }

    public SendResult sendBinary(byte[] buffer, int offset, int length) {
        return send(OPCODE_BINARY, buffer, offset, length);
    }

    private SendResult send(byte opCode, byte[] bytes, int offset, int length) {
        byte header = 0;
        header |= 1 << 7;
        header |= opCode % 128;
        byte[] maskBytes = maskingBytes;
        SizeType sz = findSize(length);
        ByteBuffer bb = NioReader.bufferAllocate(1 + length + sz.bytes + maskBytes.length);
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
        send(OPCODE_CLOSE, empty, 0, 0);
    }

    public void close() {
        writer.close();
    }
}
