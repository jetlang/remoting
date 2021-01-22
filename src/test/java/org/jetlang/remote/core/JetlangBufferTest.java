package org.jetlang.remote.core;

import org.jetlang.remote.acceptor.CloseableByteArrayStream;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class JetlangBufferTest {

    @Test
    public void same() throws IOException {
        ByteArraySerializer serializer = new ByteArraySerializer();
        Charset usAscii = StandardCharsets.US_ASCII;
        CloseableByteArrayStream b = new CloseableByteArrayStream();
        SocketMessageStreamWriter<byte[]> stream = new SocketMessageStreamWriter<byte[]>(createOut(b), usAscii, serializer.getWriter());
        byte[] contents = new byte[0];
        JetlangBuffer direct = new JetlangBuffer(128);
        direct.appendReply(5, "reply", contents, serializer.getWriter(), usAscii);
        stream.writeReply(5, "reply", contents);
        byte[] expected = b.data.toByteArray();
        assertEquals(expected.length, direct.position());
        direct.flip();
        byte[] bytes = new byte[direct.position()];
        direct.getBuffer().get(bytes);
        for(int i = 0; i < bytes.length; i++){
            assertEquals(bytes[i], expected[i]);
        }
    }

    private SocketMessageStreamWriter.Out createOut(CloseableByteArrayStream b) throws IOException {
        return new SocketMessageStreamWriter.BufferedStream(new ByteArrayBuffer(), b);
    }
}
