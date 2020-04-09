package org.jetlang.remote.core;

public interface Serializer<R, W> {
    ObjectByteWriter<W> getWriter();

    ObjectByteReader<R> getReader();
}
