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
public class BufferedSerializer {

    private final CloseableByteArrayStream globalBuffer = new CloseableByteArrayStream();
    private final SocketMessageStreamWriter stream;

    public BufferedSerializer(Charset charset, ObjectByteWriter writer){
        try {
            this.stream = new SocketMessageStreamWriter(globalBuffer, charset, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] createArray(String topic, Object msg) {
        globalBuffer.reset();
        try {
            stream.write(topic, msg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return globalBuffer.data.toByteArray();
    }
}
