package org.jetlang.web;

import javax.xml.bind.DatatypeConverter;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class WebSocketRequestHandler<S, T> implements Handler<S> {

    private final MessageDigest msgDigest = getDigest("SHA-1");
    private final Charset local;
    private final WebSocketHandler<S, T> handler;
    private final WebSocketSecurity<S> security;

    public WebSocketRequestHandler(Charset local, WebSocketHandler<S, T> handler, WebSocketSecurity<S> security) {
        this.local = local;
        this.handler = handler;
        this.security = security;
    }

    @Override
    public NioReader.State start(SessionDispatcherFactory.SessionDispatcher<S> dispatcher, HttpRequest headers, HttpResponse response, HeaderReader<S> headerReader, NioWriter writer, S sessionState) {
        if (security.passes(headerReader.getReadFiber(), headers, sessionState)) {
            final String key = headers.get("Sec-WebSocket-Key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
            final String reply = DatatypeConverter.printBase64Binary(msgDigest.digest(key.getBytes(headerReader.ascii)));
            response.sendWebsocketHandshake(reply, null);
            WebSocketConnectionImpl connection = new WebSocketConnectionImpl(writer, new byte[0], headerReader.getReadFiber(), headers);
            WebSocketReader<S, T> reader = new WebSocketReader<S, T>(connection, headers, local, dispatcher.createOnNewSession(handler, headers, sessionState), () -> {
            }, sessionState);
            return reader.start();
        } else {
            return NioReader.CLOSE;
        }
    }

    private MessageDigest getDigest(String s) {
        try {
            return MessageDigest.getInstance(s);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

}
