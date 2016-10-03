package org.jetlang.remote.example.ws;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

public class Protocol {

    private final CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();
    private final ByteBuffer bb = ByteBuffer.allocate(1024);
    private final CharBuffer buffer = CharBuffer.allocate(1024);
    private State current = new FirstLine();

    public boolean onRead(SocketChannel channel) throws IOException {
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
            stripEndOfLines();
            final int startPosition = buffer.position();
            while (buffer.remaining() > 0) {
                if (isCurrentCharEol()) {
                    String line = new String(buffer.array(), startPosition, buffer.position() - startPosition);
                    System.out.println("line = " + line);
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

    public class HeaderLine implements State {

        @Override
        public State afterRead() {
            if (stripEndOfLines() > 2) {
                System.out.println("Done");
                return null;
            }
            final int startPosition = buffer.position();
            while (buffer.remaining() > 0) {
                if (isCurrentCharEol()) {
                    String line = new String(buffer.array(), startPosition, buffer.position() - startPosition);
                    System.out.println("header = " + line);
                    buffer.position(buffer.position() + 1);
                    return new HeaderLine();
                } else {
                    buffer.position(buffer.position() + 1);
                }
            }
            return null;
        }
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
