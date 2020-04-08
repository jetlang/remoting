package org.jetlang.remote.core;

import java.nio.ByteBuffer;

public interface RawMsg {
    void read(ByteBuffer destination);
}
