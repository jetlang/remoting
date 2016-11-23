package org.jetlang.web;

import javax.xml.bind.DatatypeConverter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class WebSocketRequestHandler<S, T> implements Handler<S> {

    private final MessageDigest msgDigest = getDigest("SHA-1");
    private final Charset local;
    private final WebSocketHandler<S, T> handler;

    public WebSocketRequestHandler(Charset local, WebSocketHandler<S, T> handler) {
        this.local = local;
        this.handler = handler;
    }

    @Override
    public NioReader.State start(HttpRequest headers, HeaderReader<S> headerReader, NioWriter writer, S sessionState) {
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

    private MessageDigest getDigest(String s) {
        try {
            return MessageDigest.getInstance(s);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

}
