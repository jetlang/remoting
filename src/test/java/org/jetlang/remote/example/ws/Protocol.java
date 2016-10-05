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
import java.util.HashMap;
import java.util.Map;

public class Protocol {

    private final Charset charset = Charset.forName("UTF-8");
    private final CharsetDecoder decoder = charset.newDecoder();
    private final ByteBuffer bb = ByteBuffer.allocate(1024);
    private final CharBuffer buffer = CharBuffer.allocate(1024);
    private final SocketChannel channel;
    private final NioFiber fiber;
    private final NioControls controls;
    private State current = new FirstLine();
    private final Map<String, String> headers = new HashMap<>();
    private String method;
    private String requestUri;
    private String protocolVersion;
    private final MessageDigest msgDigest = getDigest("SHA-1");

    private MessageDigest getDigest(String s) {
        try {
            return MessageDigest.getInstance(s);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public Protocol(SocketChannel channel, NioFiber fiber, NioControls controls) {
        this.channel = channel;
        this.fiber = fiber;
        this.controls = controls;
    }

    public boolean onRead() throws IOException {
        while (channel.read(bb) > 0) {
            bb.flip();
            decoder.decode(bb, buffer, true);
            buffer.flip();
            State result = current;
            while (result != null) {
                result = current.afterRead();
                if (result != null) {
                    current = result;
                }
            }
            buffer.compact();
            bb.compact();
        }
        return true;
    }

    interface State {
        State afterRead();
    }

    public class FirstLine implements State {
        @Override
        public State afterRead() {
            final int startPosition = buffer.position();
            while (buffer.remaining() > 0) {
                if (isCurrentCharEol()) {
                    addFirstLine(buffer.array(), startPosition, buffer.position() - startPosition);
                    //System.out.println("line = " + line);
                    buffer.position(buffer.position() + 1);
                    return new HeaderLine();
                } else {
                    buffer.position(buffer.position() + 1);
                }
            }
            buffer.position(0);
            return null;
        }
    }

    private void addFirstLine(char[] array, int startPosition, int length) {
        int first = find(array, startPosition, length, ' ');
        int firstLength = first - startPosition;
        method = new String(array, startPosition, firstLength);
        System.out.println("method = " + method);
        int second = find(array, first + 1, length - firstLength, ' ');
        int secondLength = second - first - 1;
        requestUri = new String(array, startPosition + firstLength + 1, secondLength);
        System.out.println("requestUri = '" + requestUri + "'");
        protocolVersion = new String(array, startPosition + firstLength + secondLength + 2, length - firstLength - secondLength - 2);
        System.out.println("protocolVersion = '" + protocolVersion + "'");
    }

    public class HeaderLine implements State {

        int eol;

        @Override
        public State afterRead() {
            eol += stripEndOfLines();
            if (eol == 4) {
                System.out.println("Done " + eol + " " + buffer.remaining());
                if ("websocket".equals(headers.get("Upgrade"))) {
                    return sendWebsocketHandshake();
                }
                return null;
            }
            final int startPosition = buffer.position();
            while (buffer.remaining() > 0) {
                if (isCurrentCharEol()) {
                    addHeader(buffer.array(), startPosition, buffer.position() - startPosition);
                    return new HeaderLine();
                } else {
                    buffer.position(buffer.position() + 1);
                }
            }
            return null;
        }
    }

    private State sendWebsocketHandshake() {
        StringBuilder handshake = new StringBuilder();
        handshake.append("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ");
        String key = headers.get("Sec-WebSocket-Key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        String reply = DatatypeConverter.printBase64Binary(msgDigest.digest(key.getBytes(charset)));
        handshake.append(reply).append("\r\n\r\n");
        controls.write(channel, ByteBuffer.wrap(handshake.toString().getBytes(charset)));
        System.out.println("handshake = " + handshake);
        return new FirstLine();
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
