package org.jetlang.remote.example.ws;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class WebSocketReader<T> {

    private final Charset charset;
    private final WebSocketHandler<T> handler;
    private final WebSocketConnection connection;
    private final HttpRequest headers;
    private final BodyReader bodyRead = new BodyReader();
    private final T state;

    public WebSocketReader(WebSocketConnection connection, HttpRequest headers, Charset charset, WebSocketHandler<T> handler) {
        this.connection = connection;
        this.headers = headers;
        this.charset = charset;
        this.handler = handler;
        this.state = handler.onOpen(connection);
    }

    public NioReader.State start() {
        return contentReader;
    }

    private final NioReader.State contentReader = new NioReader.State() {
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
                    return textFrame;
                case 8:
                    connection.sendClose();
                    return new NioReader.Close() {
                        @Override
                        public void onClosed() {
                            handler.onClose(connection, state);
                        }
                    };
            }
            handler.onError(opcode + " op code isn't supported.");
            return createClose();
        }

        @Override
        public void onClosed() {
            handler.onClose(connection, state);
        }
    };

    private NioReader.State createClose() {
        return new NioReader.Close() {
            @Override
            public void onClosed() {
                handler.onClose(connection, state);
            }
        };
    }

    private final NioReader.State textFrame = new NioReader.State() {

        @Override
        public NioReader.State processBytes(ByteBuffer bb) {
            byte b = bb.get();
            int size = (byte) (0x7F & b);
            if (size >= 0 && size <= 125) {
                if (size == 0) {
                    handler.onMessage(connection, state, "");
                    return contentReader;
                }
                return bodyRead.init(size);
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
                        return bodyRead.init(size);
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
                        return bodyRead.init((int) bb.getLong());
                    }
                };
            }
            handler.onError("Unsupported size: " + size);
            return createClose();
        }

        @Override
        public void onClosed() {
            handler.onClose(connection, state);
        }
    };

    private class BodyReader implements NioReader.State {
        private int size;
        private byte[] result = new byte[0];

        BodyReader init(int size) {
            this.size = size;
            if (result.length < size) {
                result = new byte[size];
            }
            return this;
        }

        @Override
        public int minRequiredBytes() {
            return size + 4;
        }

        @Override
        public NioReader.State processBytes(ByteBuffer bb) {
            final int maskPos = bb.position();
            bb.position(bb.position() + 4);
            for (int i = 0; i < size; i++) {
                result[i] = (byte) (bb.get() ^ bb.get((i % 4) + maskPos));
            }
            handler.onMessage(connection, state, new String(result, 0, size, charset));
            return contentReader;
        }

        @Override
        public void onClosed() {
            handler.onClose(connection, state);
        }
    }
}
