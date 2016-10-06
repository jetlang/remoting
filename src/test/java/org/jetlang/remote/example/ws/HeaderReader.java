package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HeaderReader {

    private final Charset charset = Charset.forName("UTF-8");
    private final CharsetDecoder decoder = charset.newDecoder();
    private CharBuffer buffer = CharBuffer.allocate(1);
    private final SocketChannel channel;
    private final NioFiber fiber;
    private final NioControls controls;
    private HttpHeaders headers = new HttpHeaders();
    private final MessageDigest msgDigest = getDigest("SHA-1");
    private final WebSocketHandler handler;

    private MessageDigest getDigest(String s) {
        try {
            return MessageDigest.getInstance(s);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public HeaderReader(SocketChannel channel, NioFiber fiber, NioControls controls, WebSocketHandler handler) {
        this.channel = channel;
        this.fiber = fiber;
        this.controls = controls;
        this.handler = handler;
    }

    public Protocol.State start() {
        return new FirstLine();
    }

    private abstract class BaseCharReader implements Protocol.State {

        @Override
        public Protocol.State process(ByteBuffer bb) {
            if (buffer.remaining() < minRequiredBytes()) {
                return null;
            }
            return processBytes(bb);
        }

        public void begin(ByteBuffer bb) throws IOException {
            if (buffer.remaining() < bb.remaining()) {
                CharBuffer resize = CharBuffer.allocate(buffer.position() + bb.remaining());
                buffer.flip();
                buffer = resize.put(buffer);
            }
            decoder.decode(bb, buffer, true);
            buffer.flip();
        }

        public void end() {
            buffer.compact();
        }
    }


    public class FirstLine extends BaseCharReader {
        @Override
        public Protocol.State processBytes(ByteBuffer bb) {
            final int startPosition = buffer.position();
            while (buffer.remaining() > 0) {
                if (isCurrentCharEol()) {
                    addFirstLine(buffer.array(), startPosition, buffer.position() - startPosition);
                    //System.out.println("line = " + line);
                    //buffer.position(buffer.position() + 1);
                    return new HeaderLine();
                } else {
                    buffer.position(buffer.position() + 1);
                }
            }
            buffer.position(startPosition);
            return null;
        }
    }

    private void addFirstLine(char[] array, int startPosition, int length) {
        int first = find(array, startPosition, length, ' ');
        int firstLength = first - startPosition;
        headers.method = new String(array, startPosition, firstLength);
        System.out.println("method = " + headers.method);
        int second = find(array, first + 1, length - firstLength, ' ');
        int secondLength = second - first - 1;
        headers.requestUri = new String(array, startPosition + firstLength + 1, secondLength);
        System.out.println("requestUri = '" + headers.requestUri + "'");
        headers.protocolVersion = new String(array, startPosition + firstLength + secondLength + 2, length - firstLength - secondLength - 2);
        System.out.println("protocolVersion = '" + headers.protocolVersion + "'");
    }

    public class HeaderLine extends BaseCharReader {

        int eol;

        @Override
        public Protocol.State processBytes(ByteBuffer bb) {
            int stripped = stripEndOfLines();
            eol += stripped;
            System.out.println("eol = " + eol + " " + buffer.remaining() + " " + buffer.position());
            if (eol == 4) {
                System.out.println("Done " + eol + " " + buffer.remaining());
                String type = headers.get("Upgrade");
                if ("websocket".equals(type)) {
                    return sendWebsocketHandshake();
                }
                throw new RuntimeException("Unsupported: " + headers);
            }
            if (buffer.hasRemaining() && eol == 2) {
                return new ReadHeader();
            }
            return null;
        }
    }

    public class ReadHeader extends BaseCharReader {

        @Override
        public Protocol.State processBytes(ByteBuffer bb) {
            final int startPosition = buffer.position();
            while (buffer.remaining() > 0) {
                if (isCurrentCharEol()) {
                    addHeader(buffer.array(), startPosition, buffer.position() - startPosition);
                    return new HeaderLine();
                } else {
                    buffer.position(buffer.position() + 1);
                }
            }
            buffer.position(startPosition);
            return null;
        }
    }

    private Protocol.State sendWebsocketHandshake() {
        StringBuilder handshake = new StringBuilder();
        handshake.append("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ");
        String key = headers.get("Sec-WebSocket-Key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        String reply = DatatypeConverter.printBase64Binary(msgDigest.digest(key.getBytes(charset)));
        handshake.append(reply).append("\r\n\r\n");
        controls.write(channel, ByteBuffer.wrap(handshake.toString().getBytes(charset)));
        System.out.println("handshake = " + handshake);
        WebSocketConnection connection = new WebSocketConnection(headers, channel, controls, charset);
        WebSocketReader reader = new WebSocketReader(channel, fiber, controls, connection, headers, charset, handler);
        headers = null;
        return reader.start();
    }

    private void addHeader(char[] array, int startPosition, int length) {
        int first = find(array, startPosition, length, ':');
        final int nameLength = first - startPosition;
        String name = new String(array, startPosition, nameLength);
        System.out.println("name = " + name);
        String value = new String(array, startPosition + nameLength + 2, length - nameLength - 2);
        System.out.println("value = " + value);
        headers.put(name, value);
    }

    private static int find(char[] array, int startPosition, int length, char c) {
        final int endPos = startPosition + length;
        for (int i = startPosition; i < endPos; i++) {
            if (array[i] == c) {
                return i;
            }
        }
        throw new RuntimeException(c + " not found in " + new String(array, startPosition, length) + " " + startPosition + " " + length);
    }

    private int stripEndOfLines() {
        int count = 0;
        while (buffer.remaining() > 0 && isCurrentCharEol()) {
            buffer.position(buffer.position() + 1);
            count++;
        }
        return count;
    }

    private boolean isCurrentCharEol() {
        return isEol(buffer.get(buffer.position()));
    }

    private static boolean isEol(char c) {
        return c == '\n' || c == '\r';
    }

}
