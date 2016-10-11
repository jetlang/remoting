package org.jetlang.remote.example.ws;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public class StaticResource implements Handler {
    private final Path resource;

    public StaticResource(Path resource) {
        this.resource = resource;
    }

    @Override
    public NioReader.State start(HttpRequest headers, HeaderReader headerReader, NioWriter writer) {
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.0 200 OK\r\n");
        response.append("Content-Type: text/html\r\n");
        try {
            final byte[] b = Files.readAllBytes(resource);
            response.append("Content-Length: " + b.length + "\r\n\r\n");
            response.append(new String(b));
            writer.send(ByteBuffer.wrap(response.toString().getBytes()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return headerReader.start();
    }
}
