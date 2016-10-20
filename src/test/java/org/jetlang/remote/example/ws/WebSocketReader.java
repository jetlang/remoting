package org.jetlang.remote.example.ws;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

public class WebSocketReader<T> {

    private final StringDecoder charset;
    private final WebSocketHandler<T> handler;
    private final Runnable onClose;
    private final WebSocketConnection connection;
    private final BodyReader bodyRead = new BodyReader();
    private final BodyReader fragment = new BodyReader();
    private final Frame textFrame = new Frame();
    private final T state;

    public WebSocketReader(WebSocketConnection connection, HttpRequest headers, Charset charset, WebSocketHandler<T> handler, Runnable onClose) {
        this.connection = connection;
        this.charset = StringDecoder.create(charset);
        this.handler = handler;
        this.onClose = onClose;
        this.state = handler.onOpen(connection, headers);
    }

    public NioReader.State start() {
        return contentReader;
    }

    private final NioReader.State contentReader = new NioReader.State() {
        @Override
        public NioReader.State processBytes(ByteBuffer bb) {
            byte b = bb.get();
            boolean fin = ((b & 0x80) != 0);
            boolean rsv1 = ((b & 0x40) != 0);
            boolean rsv2 = ((b & 0x20) != 0);
            boolean rsv3 = ((b & 0x10) != 0);
            if (rsv1 || rsv2 || rsv3) {
                return closeOnError("Reserve bits are not supported.");
            }
            byte opcode = (byte) (b & 0x0F);
            switch (opcode) {
                case WebSocketConnection.OPCODE_TEXT:
                    return textFrame.init(ContentType.Text, fin, false);
                case WebSocketConnection.OPCODE_BINARY:
                    return textFrame.init(ContentType.Binary, fin, false);
                case WebSocketConnection.OPCODE_PING:
                    return textFrame.init(ContentType.PING, fin, false);
                case WebSocketConnection.OPCODE_PONG:
                    return textFrame.init(ContentType.PONG, fin, false);
                case WebSocketConnection.OPCODE_CONT:
                    return textFrame.init(null, fin, true);
                case WebSocketConnection.OPCODE_CLOSE:
                    connection.sendClose();
                    return createClose();
            }
            handler.onError(connection, state, opcode + " op code isn't supported.");
            return createClose();
        }

        @Override
        public void onClosed() {
            doClose();
        }
    };

    public void doClose() {
        handler.onClose(connection, state);
        onClose.run();
    }

    private NioReader.State createClose() {
        connection.close();
        return new NioReader.Close() {
            @Override
            public void onClosed() {
                doClose();
            }
        };
    }

    enum ContentType {
        Text(true) {
            @Override
            public <T> boolean onComplete(WebSocketHandler<T> handler, WebSocketConnection connection, T state, byte[] result, int size, StringDecoder charset) {
                String str = charset.decode(result, 0, size);
                if (str != null) {
                    handler.onMessage(connection, state, str);
                    return true;
                } else {
                    handler.onError(connection, state, "Invalid Content");
                    return false;
                }
            }
        }, Binary(true) {
            @Override
            public <T> boolean onComplete(WebSocketHandler<T> handler, WebSocketConnection connection, T state, byte[] result, int size, StringDecoder charset) {
                handler.onBinaryMessage(connection, state, result, size);
                return true;
            }
        }, PING(false) {
            @Override
            public <T> boolean onComplete(WebSocketHandler<T> handler, WebSocketConnection connection, T state, byte[] result, int size, StringDecoder charset) {
                handler.onPing(connection, state, result, size, charset);
                return true;
            }
        }, PONG(false) {
            @Override
            public <T> boolean onComplete(WebSocketHandler<T> handler, WebSocketConnection connection, T state, byte[] result, int size, StringDecoder charset) {
                handler.onPong(connection, state, result, size);
                return true;
            }
        };

        public final boolean canBeFragmented;

        ContentType(boolean canBeFragmented) {
            this.canBeFragmented = canBeFragmented;
        }

        public abstract <T> boolean onComplete(WebSocketHandler<T> handler, WebSocketConnection connection, T state, byte[] result, int size, StringDecoder charset);
    }

    private class Frame implements NioReader.State {

        private ContentType t;
        private boolean fin;
        private boolean isFragment;

        private NioReader.State init(ContentType t, boolean fin, boolean isFragment) {
            if (t != null && !fin && !t.canBeFragmented) {
                return closeOnError(t + " cannot be fragmented.");
            }
            this.t = t;
            this.fin = fin;
            this.isFragment = isFragment;
            return this;
        }

        @Override
        public NioReader.State processBytes(ByteBuffer bb) {
            byte b = bb.get();
            final int size = (byte) (0x7F & b);
            final boolean frameMasked = (b & 0x80) != 0;
            if (size >= 0 && size <= 125) {
                return bodyReadinit(size, t, fin, isFragment, frameMasked);
            }
            if (t != null && !t.canBeFragmented) {
                return closeOnError(t + " Max Size of 125.");
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
                        return bodyReadinit(size, t, fin, isFragment, frameMasked);
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
                        return bodyReadinit((int) bb.getLong(), t, fin, isFragment, frameMasked);
                    }
                };
            }
            handler.onError(connection, state, "Unsupported size: " + size);
            return createClose();
        }

        @Override
        public void onClosed() {
            doClose();
        }
    }

    private NioReader.State bodyReadinit(int size, ContentType t, boolean fin, boolean isFragment, boolean frameMasked) {
        if (isFragment || !fin) {
            return fragment.init(size, t, fin, isFragment, frameMasked);
        }
        if (fragment.expectingFragment() && t.canBeFragmented) {
            return closeOnError(t + " receiving when expecting fragment.");
        }
        return bodyRead.init(size, t, fin, isFragment, frameMasked);
    }


    private class BodyReader implements NioReader.State {
        private int totalSize;
        private int size;
        private ContentType t;
        private byte[] result = new byte[0];
        private boolean fin;
        private boolean frameMasked;

        NioReader.State init(int size, ContentType t, boolean fin, boolean isFragment, boolean frameMasked) {
            this.fin = fin;
            this.frameMasked = frameMasked;
            if (!isFragment) {
                this.t = t;
                this.totalSize = 0;
            } else if (this.t == null) {
                return closeOnError("ContentType not specified for fragment.");
            }
            totalSize += size;
            this.size = size;

            if (result.length < totalSize) {
                result = Arrays.copyOf(result, totalSize);
            }
            return this;
        }

        @Override
        public int minRequiredBytes() {
            return frameMasked ? size + 4 : size;
        }

        @Override
        public NioReader.State processBytes(ByteBuffer bb) {
            if (frameMasked) {
                final int maskPos = bb.position();
                bb.position(bb.position() + 4);
                int startPos = totalSize - size;
                for (int i = 0; i < size; i++) {
                    result[i + startPos] = (byte) (bb.get() ^ bb.get((i % 4) + maskPos));
                }
            } else {
                int startPos = totalSize - size;
                bb.get(result, startPos, size);
            }

            if (fin) {
                boolean success = t.onComplete(handler, connection, state, result, totalSize, charset);
                totalSize = 0;
                t = null;
                if (!success) {
                    return createClose();
                }
            }
            return contentReader;
        }

        @Override
        public void onClosed() {
            doClose();
        }

        public boolean expectingFragment() {
            return t != null;
        }
    }

    private NioReader.State closeOnError(String msg) {
        handler.onError(connection, state, msg);
        return createClose();
    }
}
