package org.jetlang.remote.core;

public interface Serializer {
    ObjectByteWriter getWriter();

    ObjectByteReader getReader();
}
