package org.jetlang.remote.example.ws;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class WebSocketReader<T> {

    private final Charset charset;
    private final WebSocketHandler<T> handler;
    private final WebSocketConnection connection;
    private final HttpRequest headers;
    private final BodyReader bodyRead = new BodyReader();
    private final Frame textFrame = new Frame();
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
                case WebSocketConnection.OPCODE_TEXT:
                    return textFrame.init(ContentType.Text);
                case WebSocketConnection.OPCODE_BINARY:
                    return textFrame.init(ContentType.Binary);
                case WebSocketConnection.OPCODE_PING:
                    return textFrame.init(ContentType.PING);
                case WebSocketConnection.OPCODE_PONG:
                    return textFrame.init(ContentType.PONG);
                case WebSocketConnection.OPCODE_CLOSE:
                    connection.sendClose();
                    return createClose();
            }
            handler.onError(connection, state, opcode + " op code isn't supported.");
            return createClose();
        }

        @Override
        public void onClosed() {
            handler.onClose(connection, state);
        }
    };

    private NioReader.State createClose() {
        connection.close();
        return new NioReader.Close() {
            @Override
            public void onClosed() {
                handler.onClose(connection, state);
            }
        };
    }

    enum ContentType {
        Text {
            @Override
            public <T> void onComplete(WebSocketHandler<T> handler, WebSocketConnection connection, T state, byte[] result, int size, Charset charset) {
                handler.onMessage(connection, state, new String(result, 0, size, charset));
            }
        }, Binary {
            @Override
            public <T> void onComplete(WebSocketHandler<T> handler, WebSocketConnection connection, T state, byte[] result, int size, Charset charset) {
                handler.onBinaryMessage(connection, state, result, size);
            }
        }, PING {
            @Override
            public <T> void onComplete(WebSocketHandler<T> handler, WebSocketConnection connection, T state, byte[] result, int size, Charset charset) {
                handler.onPing(connection, state, result, size, charset);
            }
        }, PONG {
            @Override
            public <T> void onComplete(WebSocketHandler<T> handler, WebSocketConnection connection, T state, byte[] result, int size, Charset charset) {
                handler.onPong(connection, state, result, size);
            }
        };

        public abstract <T> void onComplete(WebSocketHandler<T> handler, WebSocketConnection connection, T state, byte[] result, int size, Charset charset);
    }

    private class Frame implements NioReader.State {

        private ContentType t;

        private NioReader.State init(ContentType t) {
            this.t = t;
            return this;
        }

        @Override
        public NioReader.State processBytes(ByteBuffer bb) {
            byte b = bb.get();
            int size = (byte) (0x7F & b);
            if (size >= 0 && size <= 125) {
                if (size == 0) {
                    handler.onMessage(connection, state, "");
                    return contentReader;
                }
                return bodyRead.init(size, t);
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
                        return bodyRead.init(size, t);
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
                        return bodyRead.init((int) bb.getLong(), t);
                    }
                };
            }
            handler.onError(connection, state, "Unsupported size: " + size);
            return createClose();
        }

        @Override
        public void onClosed() {
            handler.onClose(connection, state);
        }
    };

    private class BodyReader implements NioReader.State {
        private int size;
        private ContentType t;
        private byte[] result = new byte[0];

        BodyReader init(int size, ContentType t) {
            this.size = size;
            this.t = t;
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
            t.onComplete(handler, connection, state, result, size, charset);
            return contentReader;
        }

        @Override
        public void onClosed() {
            handler.onClose(connection, state);
        }
    }
}
