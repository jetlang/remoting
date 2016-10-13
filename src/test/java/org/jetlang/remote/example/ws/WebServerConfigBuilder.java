package org.jetlang.remote.example.ws;

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

public class WebServerConfigBuilder {

    private Charset websocketCharset = Charset.forName("UTF-8");
    private List<Consumer<Map<String, Handler>>> events = new ArrayList<>();
    private int readBufferSizeInBytes = 1024;
    private int maxReadLoops = 50;

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

    public <T> WebServerConfigBuilder add(String path, WebSocketHandler<T> handler) {
        events.add((map) -> {
            map.put(path, new Handler() {
                private final MessageDigest msgDigest = getDigest("SHA-1");
                private final Charset local = websocketCharset;

                @Override
                public NioReader.State start(HttpRequest headers, HeaderReader headerReader, NioWriter writer) {
                    StringBuilder handshake = new StringBuilder("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ");
                    String key = headers.get("Sec-WebSocket-Key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                    String reply = DatatypeConverter.printBase64Binary(msgDigest.digest(key.getBytes(headerReader.ascii)));
                    handshake.append(reply).append("\r\n\r\n");
                    writer.send(ByteBuffer.wrap(handshake.toString().getBytes(headerReader.ascii)));
                    WebSocketConnection connection = new WebSocketConnection(writer);
                    WebSocketReader<T> reader = new WebSocketReader<>(connection, headers, local, handler);
                    return reader.start();
                }
            });
        });
        return this;
    }

    public WebServerConfigBuilder add(String path, StaticResource rs) {
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

    public WebDispatcher create() {
        Map<String, Handler> handlerMap = new HashMap<>();
        for (Consumer<Map<String, Handler>> event : events) {
            event.accept(handlerMap);
        }
        HttpRequestHandler handler = createHandler(handlerMap);
        return new WebDispatcher(handler, readBufferSizeInBytes, maxReadLoops);
    }

    protected HttpRequestHandler createHandler(final Map<String, Handler> handlerMap) {
        return new HttpRequestHandler.Default(handlerMap);
    }
}
