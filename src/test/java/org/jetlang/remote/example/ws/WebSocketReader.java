package org.jetlang.remote.example.ws;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class WebSocketReader<T> {

    private final Charset charset;
    private final WebSocketHandler<T> handler;
    private final WebSocketConnection connection;
    private final HttpRequest headers;
    private final T state;

    public WebSocketReader(WebSocketConnection connection, HttpRequest headers, Charset charset, WebSocketHandler<T> handler) {
        this.connection = connection;
        this.headers = headers;
        this.charset = charset;
        this.handler = handler;
        this.state = handler.onOpen(connection);
    }

    public NioReader.State start() {
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
            switch (opcode) {
                case 1:
                    return new TextFrame();
                case 8:
                    connection.sendClose();
                    return new NioReader.Close() {
                        @Override
                        public void onClosed() {
                            handler.onClose(connection, state);
                        }
                    };
            }
            throw new RuntimeException("Not supported: " + opcode);
        }

        @Override
        public void onClosed() {
            handler.onClose(connection, state);
        }
    }

    private class TextFrame implements NioReader.State {

        @Override
        public NioReader.State processBytes(ByteBuffer bb) {
            byte b = bb.get();
            int size = (byte) (0x7F & b);
            if (size >= 0 && size <= 125) {
                if (size == 0) {
                    handler.onMessage(connection, state, "");
                    return new ContentReader();
                }
                return new BodyReader(size);
            }
            if (size == 126) {
                return new NioReader.State() {
                    @Override
                    public int minRequiredBytes() {
                        return 2;
                    }

                    @Override
                    public NioReader.State processBytes(ByteBuffer bb) {
                        int size = ((bb.get() & 0xFF) << 8) + (bb.get() & 0xFF);
                        return new BodyReader(size);
                    }
                };
            }
            if (size == 127) {
                return new NioReader.State() {
                    @Override
                    public int minRequiredBytes() {
                        return 8;
                    }

                    @Override
                    public NioReader.State processBytes(ByteBuffer bb) {
                        return new BodyReader((int) bb.getLong());
                    }
                };
            }
            throw new RuntimeException("Unsupported size: " + size);
        }

        @Override
        public void onClosed() {
            handler.onClose(connection, state);
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
            handler.onMessage(connection, state, new String(result, charset));
            return new ContentReader();
        }

        @Override
        public void onClosed() {
            handler.onClose(connection, state);
        }
    }
}
