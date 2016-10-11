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

    public WebSocketConnection(Charset charset, NioWriter writer) {
        this.charset = charset;
        this.writer = writer;
    }

    public SendResult send(String msg) {
        final byte[] bytes = msg.getBytes(charset);
        return send(OPCODE_TEXT, bytes);
    }

    private SendResult send(byte opCode, byte[] bytes) {
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
        return writer.send(bb);
    }

    void sendClose() {
        send(OPCODE_CLOSE, empty);
    }

    public void close() throws IOException {
        writer.close();
    }
}
