package org.jetlang.remote.acceptor;

import org.jetlang.remote.core.ObjectByteWriter;
import org.jetlang.remote.core.SocketMessageStreamWriter;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * User: mrettig
 * Date: 11/28/11
 * Time: 4:36 PM
 */
public class BufferedSerializer<W> {

    private final CloseableByteArrayStream globalBuffer = new CloseableByteArrayStream();
    private final SocketMessageStreamWriter<W> stream;

    public BufferedSerializer(Charset charset, ObjectByteWriter<W> writer){
        try {
            this.stream = new SocketMessageStreamWriter<W>(globalBuffer, charset, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] createArray(String topic, W msg) {
        globalBuffer.reset();
        try {
            stream.write(topic, msg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return globalBuffer.data.toByteArray();
    }
}
