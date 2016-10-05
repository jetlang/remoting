package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class WebSocketConnection {

    private static final byte OPCODE_CONT = 0x0;
    private static final byte OPCODE_TEXT = 0x1;
    private static final byte OPCODE_BINARY = 0x2;
    private static final byte OPCODE_CLOSE = 0x8;
    private static final byte OPCODE_PING = 0x9;
    private static final byte OPCODE_PONG = 0xA;
    private final SocketChannel channel;
    private final NioControls controls;
    private final Charset charset;

    public WebSocketConnection(HttpHeaders headers, SocketChannel channel, NioControls controls, Charset charset) {
        this.channel = channel;
        this.controls = controls;
        this.charset = charset;
    }

    public void send(String msg) {
        final byte[] bytes = msg.getBytes(charset);
        final int length = bytes.length;
        boolean maskPayload = true;
        byte header = 0;
        header |= 1 << 7;
        header |= OPCODE_TEXT % 128;
        byte lengthWithMask = (byte) (maskPayload ? 0x80 | (byte) length : (byte) length);

        int random = (int) (Math.random() * Integer.MAX_VALUE);
        byte[] mask = ByteBuffer.allocate(4).putInt(random).array();
        ByteBuffer bb = ByteBuffer.allocate(2 + 4 + length);
        bb.put(header);
        bb.put(lengthWithMask);
        bb.put(mask);

        for (int i = 0; i < length; i++) {
            byte byteData = bytes[i];
            bb.put((byte) (byteData ^ mask[+i % 4]));
        }
        bb.flip();
        controls.write(channel, bb);
    }
}
