package org.jetlang.web;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

public class HttpResponseWriter {
    private final NioWriter writer;

    public HttpResponseWriter(NioWriter writer) {
        this.writer = writer;
    }

    public SendResult sendResponse(int statusCode, String statusTxt, String contentType, Path resource, Charset charset) {
        try {
            final byte[] b = Files.readAllBytes(resource);
            String str = new String(b, charset);
            return sendResponse(statusCode, statusTxt, contentType, str, charset);
        } catch (IOException failed) {
            throw new RuntimeException(failed);
        }
    }

    public SendResult send(ByteBuffer fullResponse) {
        return writer.send(fullResponse);
    }

    public SendResult sendResponse(int statusCode, String statusTxt, String contentType, String content, Charset ascii) {
        byte[] b = content.getBytes(ascii);
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.0 ").append(statusCode).append(' ').append(statusTxt).append("\r\n");
        response.append("Content-Type: ").append(contentType).append("\r\n");
        response.append("Content-Length: ").append(b.length).append("\r\n\r\n");
        response.append(content);
        return send(ByteBuffer.wrap(response.toString().getBytes()));
    }

}
