package org.jetlang.remote.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 9:04 AM
 */
public class JavaSerializationWriter implements ObjectByteWriter<Object> {

    public static class ByteStream extends ByteArrayOutputStream {
        //provide raw access to buffer without copying.
        byte[] getBuffer() {
            return buf;
        }
    }

    final ByteStream bytes = new ByteStream();

    public void write(String toTopic, Object msg, ByteMessageWriter writer) throws IOException {
        bytes.reset();
        ObjectOutputStream output = new ObjectOutputStream(bytes);
        output.writeObject(msg);
        writer.writeObjectAsBytes(bytes.getBuffer(), 0, bytes.size());
    }


}
