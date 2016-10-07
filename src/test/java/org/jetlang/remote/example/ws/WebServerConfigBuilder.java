package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;

import javax.xml.bind.DatatypeConverter;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
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

    public Charset getWebsocketCharset() {
        return websocketCharset;
    }

    public WebServerConfigBuilder setWebsocketCharset(Charset websocketCharset) {
        this.websocketCharset = websocketCharset;
        return this;
    }

    public WebServerConfigBuilder add(String path, WebSocketHandler handler) {
        events.add((map) -> {
            map.put(path, new Handler() {
                private final MessageDigest msgDigest = getDigest("SHA-1");
                private final Charset local = websocketCharset;

                @Override
                public NioReader.State start(HttpRequest headers, NioControls controls, SocketChannel channel, NioFiber fiber, HeaderReader headerReader) {
                    StringBuilder handshake = new StringBuilder();
                    handshake.append("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ");
                    String key = headers.get("Sec-WebSocket-Key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                    String reply = DatatypeConverter.printBase64Binary(msgDigest.digest(key.getBytes(headerReader.ascii)));
                    handshake.append(reply).append("\r\n\r\n");
                    controls.write(channel, ByteBuffer.wrap(handshake.toString().getBytes(headerReader.ascii)));
                    System.out.println("handshake = " + handshake);
                    WebSocketConnection connection = new WebSocketConnection(headers, channel, controls, headerReader.ascii);
                    WebSocketReader reader = new WebSocketReader(channel, fiber, controls, connection, headers, local, handler);
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
        return new WebDispatcher(handlerMap);
    }
}
