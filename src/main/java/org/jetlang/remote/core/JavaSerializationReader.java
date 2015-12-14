package org.jetlang.remote.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 12:30 PM
 */
public class JavaSerializationReader implements ObjectByteReader {

    public Object readObject(String fromTopic, byte[] buffer, int offset, int length) throws IOException {
        ByteArrayInputStream readStream = new ByteArrayInputStream(buffer, offset, length);
        try {
            return new ObjectInputStream(readStream).readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
