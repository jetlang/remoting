package org.jetlang.remote.core;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 10:53 AM
 */
public class JavaSerializer implements Serializer<Object, Object> {

    private final JavaSerializationWriter writer = new JavaSerializationWriter();
    private final ObjectByteReader reader = new JavaSerializationReader();

    @Override
    public ObjectByteWriter<Object> getWriter() {
        return writer;
    }

    @Override
    public ObjectByteReader<Object> getReader() {
        return reader;
    }

    public static class Factory implements SerializerFactory<Object, Object> {

        @Override
        public Serializer<Object, Object> create() {
            return new JavaSerializer();
        }

        @Override
        public ObjectByteWriter<Object> createForGlobalWriter() {
            return new JavaSerializationWriter();
        }

    }
}
