package org.jetlang.web;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public class HttpResponseWriter {
    private final NioWriter writer;

    public HttpResponseWriter(NioWriter writer) {
        this.writer = writer;
    }

    public SendResult sendResponse(String status, String contentType, Path resource) {
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.0 ").append(status).append("\r\n");
        response.append("Content-Type: ").append(contentType).append("\r\n");
        try {
            final byte[] b = Files.readAllBytes(resource);
            response.append(new String(b));
            response.append("Content-Length: ").append(b.length).append("\r\n\r\n");
            return send(ByteBuffer.wrap(response.toString().getBytes()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public SendResult send(ByteBuffer fullResponse) {
        return writer.send(fullResponse);
    }
}
