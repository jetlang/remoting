package org.jetlang.remote.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 12:30 PM
 */
public class JavaSerializationReader implements ObjectByteReader<Object> {

    @Override
    public Object readObject(String fromTopic, ByteBuffer bb, int length) throws IOException{
        byte[] body = new byte[length];
        bb.get(body);
        ByteArrayInputStream readStream = new ByteArrayInputStream(body, 0, length);
        try {
            return new ObjectInputStream(readStream).readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
