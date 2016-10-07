package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Map;

public class HeaderReader {

    private final Charset ascii = Charset.forName("ASCII");
    private final SocketChannel channel;
    private final NioFiber fiber;
    private final NioControls controls;
    private final Map<String, Handler> handler;

    public HeaderReader(SocketChannel channel, NioFiber fiber, NioControls controls, Map<String, Handler> handler) {
        this.channel = channel;
        this.fiber = fiber;
        this.controls = controls;
        this.handler = handler;
    }

    public NioReader.State start() {
        return new FirstLine();
    }

    private abstract class BaseCharReader implements NioReader.State {

//        @Override
//        public NioReader.State process(ByteBuffer buffer) {
//            if (buffer.remaining() < minRequiredBytes()) {
//                return null;
//            }
//            return processBytes(bb);
//        }
//
//        public void begin(ByteBuffer bb) throws IOException {
//            if (buffer.remaining() < bb.remaining()) {
//                CharBuffer resize = CharBuffer.allocate(buffer.position() + bb.remaining());
//                buffer.flip();
//                buffer = resize.put(buffer);
//            }
//            decoder.decode(bb, buffer, true);
//            buffer.flip();
//        }
//
//        public void end() {
//            buffer.compact();
//        }
    }


    public class FirstLine extends BaseCharReader {
        private final HttpRequest headers = new HttpRequest();

        @Override
        public NioReader.State processBytes(ByteBuffer buffer) {
            final int startPosition = buffer.position();
            while (buffer.remaining() > 0) {
                if (isCurrentCharEol(buffer)) {
                    addFirstLine(buffer.array(), startPosition, buffer.position() - startPosition);
                    //System.out.println("line = " + line);
                    //buffer.position(buffer.position() + 1);
                    return new HeaderLine(headers);
                } else {
                    buffer.position(buffer.position() + 1);
                }
            }
            buffer.position(startPosition);
            return null;
        }

        private void addFirstLine(byte[] array, int startPosition, int length) {
            int first = find(array, startPosition, length, ' ');
            int firstLength = first - startPosition;
            headers.method = new String(array, startPosition, firstLength, ascii);
            System.out.println("method = " + headers.method);
            int second = find(array, first + 1, length - firstLength, ' ');
            int secondLength = second - first - 1;
            headers.requestUri = new String(array, startPosition + firstLength + 1, secondLength, ascii);
            System.out.println("requestUri = '" + headers.requestUri + "'");
            headers.protocolVersion = new String(array, startPosition + firstLength + secondLength + 2, length - firstLength - secondLength - 2, ascii);
            System.out.println("protocolVersion = '" + headers.protocolVersion + "'");
        }
    }


    public class HeaderLine extends BaseCharReader {

        private final HttpRequest headers;
        int eol;

        public HeaderLine(HttpRequest headers) {
            this.headers = headers;
        }

        @Override
        public NioReader.State processBytes(ByteBuffer buffer) {
            int stripped = stripEndOfLines(buffer);
            eol += stripped;
            System.out.println("eol = " + eol + " " + buffer.remaining() + " " + buffer.position());
            if (eol == 4) {
                System.out.println("Done " + eol + " " + buffer.remaining());
                Handler h = handler.get(headers.getRequestUri());
                if (h != null) {
                    return h.start(headers, controls, channel, fiber, HeaderReader.this);
                } else {
                    //throw new RuntimeException("Unsupported: " + headers);
                }
            }
            if (buffer.hasRemaining() && eol == 2) {
                return new ReadHeader(headers);
            }
            return null;
        }
    }

    public class ReadHeader extends BaseCharReader {

        private final HttpRequest headers;

        public ReadHeader(HttpRequest headers) {
            this.headers = headers;
        }

        @Override
        public NioReader.State processBytes(ByteBuffer buffer) {
            final int startPosition = buffer.position();
            while (buffer.remaining() > 0) {
                if (isCurrentCharEol(buffer)) {
                    addHeader(buffer.array(), startPosition, buffer.position() - startPosition);
                    return new HeaderLine(headers);
                } else {
                    buffer.position(buffer.position() + 1);
                }
            }
            buffer.position(startPosition);
            return null;
        }

        private void addHeader(byte[] array, int startPosition, int length) {
            int first = find(array, startPosition, length, ':');
            final int nameLength = first - startPosition;
            String name = new String(array, startPosition, nameLength, ascii);
            System.out.println("name = " + name);
            String value = new String(array, startPosition + nameLength + 2, length - nameLength - 2, ascii);
            System.out.println("value = " + value);
            headers.put(name, value);
        }
    }

    private static int find(byte[] array, int startPosition, int length, char c) {
        final int endPos = startPosition + length;
        for (int i = startPosition; i < endPos; i++) {
            if (array[i] == c) {
                return i;
            }
        }
        throw new RuntimeException(c + " not found in " + new String(array, startPosition, length) + " " + startPosition + " " + length);
    }

    private int stripEndOfLines(ByteBuffer buffer) {
        int count = 0;
        while (buffer.remaining() > 0 && isCurrentCharEol(buffer)) {
            buffer.position(buffer.position() + 1);
            count++;
        }
        return count;
    }

    private boolean isCurrentCharEol(ByteBuffer buffer) {
        return isEol(buffer.get(buffer.position()));
    }

    private static boolean isEol(byte c) {
        return c == '\n' || c == '\r';
    }

}
