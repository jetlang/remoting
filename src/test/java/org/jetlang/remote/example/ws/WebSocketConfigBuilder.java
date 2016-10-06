package org.jetlang.remote.example.ws;

import org.jetlang.fibers.NioControls;
import org.jetlang.fibers.NioFiber;
import org.jetlang.remote.acceptor.NioAcceptorHandler;

import javax.xml.bind.DatatypeConverter;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class WebSocketConfigBuilder {

    private Map<String, Handler> handlerMap = new HashMap<>();
    private final Charset charset = Charset.forName("UTF-8");

    public WebSocketConfigBuilder add(String path, WebSocketHandler handler) {
        handlerMap.put(path, new Handler() {
            private final MessageDigest msgDigest = getDigest("SHA-1");

            @Override
            public NioReader.State start(HttpRequest headers, NioControls controls, SocketChannel channel, NioFiber fiber) {
                StringBuilder handshake = new StringBuilder();
                handshake.append("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ");
                String key = headers.get("Sec-WebSocket-Key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                String reply = DatatypeConverter.printBase64Binary(msgDigest.digest(key.getBytes(charset)));
                handshake.append(reply).append("\r\n\r\n");
                controls.write(channel, ByteBuffer.wrap(handshake.toString().getBytes(charset)));
                System.out.println("handshake = " + handshake);
                WebSocketConnection connection = new WebSocketConnection(headers, channel, controls, charset);
                WebSocketReader reader = new WebSocketReader(channel, fiber, controls, connection, headers, charset, handler);
                return reader.start();
            }
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

    public NioAcceptorHandler.ClientFactory create() {
        return new WebSocketClientFactory(new HashMap<>(handlerMap));
    }
}
