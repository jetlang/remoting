package org.jetlang.remote.core;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface ObjectByteReader<T> {
    T readObject(String fromTopic, ByteBuffer bb, int length);
}
