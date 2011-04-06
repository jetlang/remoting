package com.jetlang.remote.core;

import java.net.Socket;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 10:53 AM
 */
public class JavaSerializer implements Serializer {

    private final JavaSerializationWriter writer = new JavaSerializationWriter();

    public ObjectByteWriter getWriter() {
        return writer;
    }

    public static class Factory implements SerializerFactory {

        public Serializer createForSocket(Socket socket) {
            return new JavaSerializer();
        }

    }
}
