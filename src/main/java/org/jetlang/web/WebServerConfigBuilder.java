package org.jetlang.web;

import org.jetlang.fibers.NioFiber;

import javax.xml.bind.DatatypeConverter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class WebServerConfigBuilder<S> {

    private final SessionFactory<S> factory;
    private Charset websocketCharset = Charset.forName("UTF-8");
    private List<Consumer<Map<String, Handler<S>>>> events = new ArrayList<>();
    private int readBufferSizeInBytes = 1024;
    private int maxReadLoops = 50;
    private RequestDecorator<S> decorator = new RequestDecorator<S>() {
        @Override
        public HttpRequestHandler<S> decorate(HttpRequestHandler<S> handler) {
            return handler;
        }
    };

    public WebServerConfigBuilder(SessionFactory<S> factory) {
        this.factory = factory;
    }

    public RequestDecorator<S> getDecorator() {
        return decorator;
    }

    public void setDecorator(RequestDecorator<S> decorator) {
        this.decorator = decorator;
    }

    public int getMaxReadLoops() {
        return maxReadLoops;
    }

    public void setMaxReadLoops(int maxReadLoops) {
        this.maxReadLoops = maxReadLoops;
    }

    public int getReadBufferSizeInBytes() {
        return readBufferSizeInBytes;
    }

    public void setReadBufferSizeInBytes(int readBufferSizeInBytes) {
        this.readBufferSizeInBytes = readBufferSizeInBytes;
    }

    public Charset getWebsocketCharset() {
        return websocketCharset;
    }

    public WebServerConfigBuilder setWebsocketCharset(Charset websocketCharset) {
        this.websocketCharset = websocketCharset;
        return this;
    }

    public <T> WebServerConfigBuilder<S> add(String path, WebSocketHandler<S, T> handler) {
        events.add((map) -> {
            map.put(path, new Handler<S>() {
                private final MessageDigest msgDigest = getDigest("SHA-1");
                private final Charset local = websocketCharset;

                @Override
                public NioReader.State start(HttpRequest headers, HeaderReader headerReader, NioWriter writer, S sessionState) {
                    StringBuilder handshake = new StringBuilder("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ");
                    String key = headers.get("Sec-WebSocket-Key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                    String reply = DatatypeConverter.printBase64Binary(msgDigest.digest(key.getBytes(headerReader.ascii)));
                    handshake.append(reply).append("\r\n\r\n");
                    writer.send(ByteBuffer.wrap(handshake.toString().getBytes(headerReader.ascii)));
                    WebSocketConnectionImpl connection = new WebSocketConnectionImpl(writer, new byte[0], headerReader.getReadFiber());
                    WebSocketReader<S, T> reader = new WebSocketReader<S, T>(connection, headers, local, handler, () -> {
                    }, sessionState);
                    return reader.start();
                }
            });
        });
        return this;
    }

    public WebServerConfigBuilder<S> add(String path, HttpHandler<S> rs) {
        events.add((map) -> {
            map.put(path, rs);
        });
        return this;
    }

    private MessageDigest getDigest(String s) {
        try {
            return MessageDigest.getInstance(s);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public interface RequestDecorator<S> {

        HttpRequestHandler<S> decorate(HttpRequestHandler<S> handler);
    }

    public WebDispatcher<S> create(NioFiber readFiber) {
        Map<String, Handler<S>> handlerMap = new HashMap<>();
        for (Consumer<Map<String, Handler<S>>> event : events) {
            event.accept(handlerMap);
        }
        HttpRequestHandler<S> handler = decorator.decorate(createHandler(handlerMap));
        return new WebDispatcher<>(readFiber, handler, readBufferSizeInBytes, maxReadLoops, factory);
    }

    protected HttpRequestHandler<S> createHandler(final Map<String, Handler<S>> handlerMap) {
        return new HttpRequestHandler.Default<>(handlerMap);
    }
}
