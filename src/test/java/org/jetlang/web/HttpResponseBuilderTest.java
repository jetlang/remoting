package org.jetlang.web;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class HttpResponseBuilderTest {

    @Test
    public void build() {
        StringBuilder handshake = new StringBuilder("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ");
        handshake.append("reply").append("\r\n");
        ByteBuffer result = ByteBuffer.wrap(handshake.toString().getBytes(HeaderReader.ascii));

        HttpResponseBuilder b = new HttpResponseBuilder(HeaderReader.ascii.newEncoder());
        b.append("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ");
        b.append("reply").append("\r\n");
        b.encode();

        assertEquals(result, b.output);
    }
}
