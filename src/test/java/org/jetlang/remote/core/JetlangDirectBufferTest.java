package org.jetlang.remote.core;

import org.jetlang.remote.acceptor.CloseableByteArrayStream;
import org.jetlang.remote.client.JetlangDirectBuffer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class JetlangDirectBufferTest {

    @Test
    public void same() throws IOException {
        ByteArraySerializer serializer = new ByteArraySerializer();
        Charset usAscii = StandardCharsets.US_ASCII;
        CloseableByteArrayStream b = new CloseableByteArrayStream();
        SocketMessageStreamWriter<byte[]> stream = new SocketMessageStreamWriter<byte[]>(createOut(b), usAscii, serializer.getWriter());
        byte[] contents = new byte[0];
        JetlangDirectBuffer direct = new JetlangDirectBuffer(128);
        direct.appendReply(5, "reply", contents, serializer.getWriter(), usAscii);
        stream.writeReply(5, "reply", contents);
        byte[] expected = b.data.toByteArray();
        assertEquals(expected.length, direct.buffer.position());
        direct.buffer.flip();
        byte[] bytes = new byte[direct.buffer.position()];
        direct.buffer.get(bytes);
        for(int i = 0; i < bytes.length; i++){
            assertEquals(bytes[i], expected[i]);
        }
    }

    private SocketMessageStreamWriter.Out createOut(CloseableByteArrayStream b) throws IOException {
        return new SocketMessageStreamWriter.BufferedStream(new ByteArrayBuffer(), b);
    }
}
