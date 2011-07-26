package org.jetlang.remote.core;

import java.io.IOException;

/**
 * User: mrettig
 * Date: 4/6/11
 * Time: 9:00 AM
 */
public interface ObjectByteWriter {
    void write(String topic, Object msg, ByteMessageWriter writer) throws IOException;
}
