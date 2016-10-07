package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class WebSocketReader {

    private final Charset charset;
    private final WebSocketHandler handler;
    private final SocketChannel channel;
    private final NioFiber fiber;
    private final NioControls controls;
    private final WebSocketConnection connection;
    private final HttpRequest headers;

    public WebSocketReader(SocketChannel channel, NioFiber fiber, NioControls controls, WebSocketConnection connection, HttpRequest headers, Charset charset, WebSocketHandler handler) {
        this.channel = channel;
        this.fiber = fiber;
        this.controls = controls;
        this.connection = connection;
        this.headers = headers;
        this.charset = charset;
        this.handler = handler;
    }

    public NioReader.State start() {
        handler.onOpen(connection);
        return new ContentReader();
    }

    private class ContentReader implements NioReader.State {
        @Override
        public NioReader.State processBytes(ByteBuffer bb) {
            byte b = bb.get();
            boolean fin = ((b & 0x80) != 0);
//                boolean rsv1 = ((b & 0x40) != 0);
//                boolean rsv2 = ((b & 0x20) != 0);
//                boolean rsv3 = ((b & 0x10) != 0);
            byte opcode = (byte) (b & 0x0F);
            System.out.println("first = " + b);
            System.out.println("fin = " + fin);
            System.out.println("op = " + opcode);
            System.out.println("AfterRead");
            switch (opcode) {
                case 1:
                    return new TextFrame();
                case 8:
                    return new NioReader.Close() {
                        @Override
                        public void onClosed() {
                            handler.onClose(connection);
                        }
                    };
            }
            throw new RuntimeException("Not supported: " + opcode);
        }

        @Override
        public void onClosed() {
            handler.onClose(connection);
        }
    }

    private class TextFrame implements NioReader.State {

        @Override
        public NioReader.State processBytes(ByteBuffer bb) {
            byte b = bb.get();
            System.out.println("b = " + b);
            int size = (byte) (0x7F & b);
            System.out.println("size = " + size);
            if (size >= 0 && size <= 125) {
                if (size == 0) {
                    handler.onMessage(connection, "");
                    return new ContentReader();
                }
                return new BodyReader(size);
            }
            throw new RuntimeException("Unsupported size: " + size);
        }

        @Override
        public void onClosed() {
            handler.onClose(connection);
        }
    }

    private class BodyReader implements NioReader.State {
        private final int size;

        public BodyReader(int size) {
            this.size = size;
        }

        @Override
        public int minRequiredBytes() {
            return size + 4;
        }

        @Override
        public NioReader.State processBytes(ByteBuffer bb) {
            final int maskPos = bb.position();
            bb.position(bb.position() + 4);
            byte[] result = new byte[size];
            for (int i = 0; i < size; i++) {
                result[i] = (byte) (bb.get() ^ bb.get((i % 4) + maskPos));
            }
            handler.onMessage(connection, new String(result, charset));
            return new ContentReader();
        }

        @Override
        public void onClosed() {
            handler.onClose(connection);
        }
    }
}
