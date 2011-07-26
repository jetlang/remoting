package org.jetlang.remote.core;

import java.io.IOException;
import java.net.Socket;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 10:53 AM
 */
public class ByteArraySerializer implements Serializer {

    public static class Writer implements ObjectByteWriter {
        public void write(String topic, Object msg, ByteMessageWriter writer) throws IOException {
            byte[] obj = (byte[]) msg;
            writer.writeObjectAsBytes(obj, 0, obj.length);
        }
    }

    public static class Reader implements ObjectByteReader {

        public Object readObject(String fromTopic, byte[] buffer, int offset, int length) throws IOException {
            byte[] toReturn = new byte[length];
            System.arraycopy(buffer, offset, toReturn, 0, length);
            return toReturn;
        }
    }

    private final Writer writer = new Writer();
    private final Reader reader = new Reader();

    public ObjectByteWriter getWriter() {
        return writer;
    }

    public ObjectByteReader getReader() {
        return reader;
    }

    public static class Factory implements SerializerFactory {

        public Serializer createForSocket(Socket socket) {
            return new ByteArraySerializer();
        }

        public ObjectByteWriter createForGlobalWriter() {
            return new Writer();
        }

    }
}
