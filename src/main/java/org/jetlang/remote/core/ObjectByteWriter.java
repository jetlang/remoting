package org.jetlang.remote.core;

public interface ObjectByteWriter<T> {
    void write(String topic, T msg, ByteMessageWriter writer);
}
