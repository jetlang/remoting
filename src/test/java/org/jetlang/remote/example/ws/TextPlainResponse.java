package org.jetlang.remote.example.ws;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class TextPlainResponse {

    private final byte[] header;
    private final byte[] body;

    public TextPlainResponse(int responseCode, String responseText, String text, Charset ascii) {
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.0 ").append(responseCode).append(" ").append(responseText);
        response.append("\r\n");
        response.append("Content-Type: text/plain");
        response.append("\r\n");
        body = text.getBytes(ascii);
        response.append("Content-Length: ").append(body.length);
        response.append("\r\n");
        response.append("\r\n");
        header = response.toString().getBytes(ascii);
    }

    public ByteBuffer getByteBuffer() {
        ByteBuffer bb = ByteBuffer.allocate(header.length + body.length);
        bb.put(header).put(body);
        bb.flip();
        return bb;
    }
}
