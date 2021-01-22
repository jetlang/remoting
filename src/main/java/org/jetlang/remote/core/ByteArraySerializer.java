package org.jetlang.remote.core;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 10:53 AM
 */
public class ByteArraySerializer implements Serializer<byte[], byte[]> {

    public static class Writer implements ObjectByteWriter<byte[]> {
        @Override
        public void write(String topic, byte[] obj, ByteMessageWriter writer) {
            writer.writeObjectAsBytes(obj, 0, obj.length);
        }
    }

    public static class Reader implements ObjectByteReader<byte[]> {

        @Override
        public byte[] readObject(String fromTopic, ByteBuffer bb, int length) {
            byte[] toReturn = new byte[length];
            bb.get(toReturn);
            return toReturn;
        }
    }

    private final Writer writer = new Writer();
    private final Reader reader = new Reader();

    public ObjectByteWriter<byte[]> getWriter() {
        return writer;
    }

    public ObjectByteReader<byte[]> getReader() {
        return reader;
    }

    public static class Factory implements SerializerFactory {

        public Serializer<byte[], byte[]> create() {
            return new ByteArraySerializer();
        }

        public ObjectByteWriter<byte[]> createForGlobalWriter() {
            return new Writer();
        }

    }
}
