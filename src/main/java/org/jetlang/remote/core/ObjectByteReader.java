package org.jetlang.remote.core;

import java.io.IOException;

public interface ObjectByteReader<T> {
    T readObject(String fromTopic, byte[] buffer, int offset, int length) throws IOException;
}
