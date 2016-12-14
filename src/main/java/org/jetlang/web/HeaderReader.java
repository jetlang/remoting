package org.jetlang.web;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class HeaderReader<T> {

    public static final Charset ascii = Charset.forName("ASCII");
    private final SocketChannel channel;
    private final NioFiber fiber;
    private final NioControls controls;
    private final HttpRequestHandler<T> handler;
    private final SessionFactory<T> sessionFactory;
    private final SessionDispatcherFactory<T> dispatcher;
    private boolean sessionInit;
    private T session;
    private SessionDispatcherFactory.SessionDispatcher<T> sessionDispatcher;
    private final NioWriter writer;
    private final HttpResponseWriter httpWriter;

    public HeaderReader(SocketChannel channel, NioFiber fiber, NioControls controls, HttpRequestHandler<T> handler, SessionFactory<T> sessionFactory, SessionDispatcherFactory<T> dispatcher) {
        this.channel = channel;
        this.fiber = fiber;
        this.controls = controls;
        this.handler = handler;
        this.sessionFactory = sessionFactory;
        this.dispatcher = dispatcher;
        this.writer = new NioWriter(new Object(), channel, fiber);
        this.httpWriter = new HttpResponseWriter(writer);
    }

    public NioReader.State start() {
        return new FirstLine();
    }

    public NioFiber getReadFiber() {
        return fiber;
    }

    public void onClose() {
        if (sessionInit) {
            sessionFactory.onClose(session);
            sessionDispatcher.onClose(session);
        }
    }

    public HttpResponseWriter getHttpResponseWriter() {
        return httpWriter;
    }

    public class FirstLine implements NioReader.State {
        private final HttpRequest headers = new HttpRequest();

        @Override
        public NioReader.State processBytes(ByteBuffer buffer) {
            final int startPosition = buffer.position();
            while (buffer.remaining() > 0) {
                if (isCurrentCharEol(buffer)) {
                    addFirstLine(buffer.array(), startPosition, buffer.position() - startPosition);
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
            int second = find(array, first + 1, length - firstLength, ' ');
            int secondLength = second - first - 1;
            headers.requestUri = URI.create(new String(array, startPosition + firstLength + 1, secondLength, ascii));
            headers.protocolVersion = new String(array, startPosition + firstLength + secondLength + 2, length - firstLength - secondLength - 2, ascii);
        }
    }

    public NioReader.State dispatchHttpRequest(HttpRequest headers) {
        if (!sessionInit) {
            sessionInit = true;
            session = sessionFactory.create(channel, fiber, controls, headers);
            sessionDispatcher = dispatcher.createOnNewSession(session, headers);
        }
        return handler.dispatch(sessionDispatcher, headers, HeaderReader.this, writer, session);
    }

    public class HeaderLine implements NioReader.State {

        private final HttpRequest headers;
        int eol;

        public HeaderLine(HttpRequest headers) {
            this.headers = headers;
        }

        @Override
        public NioReader.State processBytes(ByteBuffer buffer) {
            int stripped = stripEndOfLines(buffer);
            eol += stripped;
            if (eol == 4) {
                if (headers.contentLength == 0) {
                    return dispatchHttpRequest(headers);
                } else {
                    return new ContentReader(headers);
                }
            }
            if (buffer.hasRemaining() && eol == 2) {
                return new ReadHeader(headers);
            }
            return null;
        }
    }

    private class ContentReader implements NioReader.State {

        private final HttpRequest headers;

        public ContentReader(HttpRequest headers) {
            this.headers = headers;
        }

        @Override
        public int minRequiredBytes() {
            return headers.contentLength;
        }

        @Override
        public NioReader.State processBytes(ByteBuffer bb) {
            byte[] content = new byte[this.headers.contentLength];
            bb.get(content);
            headers.content = content;
            return dispatchHttpRequest(headers);
        }
    }

    public class ReadHeader implements NioReader.State {

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
            String value = new String(array, startPosition + nameLength + 2, length - nameLength - 2, ascii);
            headers.put(name, value);
            switch (name) {
                case "Content-Length":
                    headers.contentLength = Integer.parseInt(value);
                    break;
            }
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

    private static int stripEndOfLines(ByteBuffer buffer) {
        int count = 0;
        while (buffer.remaining() > 0 && isCurrentCharEol(buffer)) {
            buffer.position(buffer.position() + 1);
            count++;
        }
        return count;
    }

    private static boolean isCurrentCharEol(ByteBuffer buffer) {
        return isEol(buffer.get(buffer.position()));
    }

    private static boolean isEol(byte c) {
        return c == '\n' || c == '\r';
    }

}
