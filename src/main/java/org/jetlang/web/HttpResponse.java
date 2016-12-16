package org.jetlang.web;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

public interface HttpResponse {


    default SendResult sendResponse(int statusCode, String statusTxt, String contentType, Path resource) {
        try {
            final byte[] b = Files.readAllBytes(resource);
            return sendResponse(statusCode, statusTxt, contentType, b);
        } catch (IOException failed) {
            throw new RuntimeException(failed);
        }
    }

    default SendResult sendResponse(int statusCode, String statusTxt, String contentType, String content, Charset ascii) {
        byte[] b = content.getBytes(ascii);
        return sendResponse(statusCode, statusTxt, contentType, b);
    }

    default SendResult sendResponse(int statusCode, String statusTxt, String contentType, byte[] content) {
        return sendResponse(statusCode, statusTxt, contentType, null, content);
    }

    default SendResult sendResponse(int statusCode, String statusTxt, String contentType, HeaderList headers, byte[] content) {
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.0 ").append(statusCode).append(' ').append(statusTxt).append("\r\n");
        response.append("Content-Type: ").append(contentType).append("\r\n");
        if (headers != null) {
            headers.appendTo(response);
        }
        response.append("Content-Length: ").append(content.length).append("\r\n\r\n");
        byte[] header = response.toString().getBytes(HeaderReader.ascii);
        ByteBuffer bb = ByteBuffer.allocate(header.length + content.length);
        bb.put(header);
        bb.put(content);
        bb.flip();
        return send(bb);
    }

    SendResult send(ByteBuffer fullResponse);

    default void sendWebsocketHandshake(String reply, HeaderList additionalHeaders) {
        StringBuilder handshake = new StringBuilder("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ");
        handshake.append(reply).append("\r\n");
        if (additionalHeaders != null) {
            additionalHeaders.appendTo(handshake);
        }
        handshake.append("\r\n");
        send(ByteBuffer.wrap(handshake.toString().getBytes(HeaderReader.ascii)));
    }

    class Default implements HttpResponse {
        private final NioWriter writer;

        public Default(NioWriter writer) {
            this.writer = writer;
        }

        public SendResult send(ByteBuffer fullResponse) {
            return writer.send(fullResponse);
        }
    }

    class Decorator implements HttpResponse {

        private final HttpResponse target;

        public Decorator(HttpResponse target) {
            this.target = target;
        }

        @Override
        public SendResult send(ByteBuffer fullResponse) {
            return target.send(fullResponse);
        }
    }

}
