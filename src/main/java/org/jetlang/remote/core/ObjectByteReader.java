package org.jetlang.remote.core;

import java.io.IOException;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 12:27 PM
 */
public interface ObjectByteReader<T> {
    T readObject(String fromTopic, byte[] buffer, int offset, int length) throws IOException;
}
